/**************************************************************************
 * Modular bot for teamspeak 3 (c)
 * Copyright (C) 2015-2016 Aron Heinecke
 * 
 * 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 * See main class TS3Manager.java for the full version.
 *************************************************************************/
package Aron.Heinecke.ts3Manager.Mods;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.SBuffer;
import Aron.Heinecke.ts3Manager.Lib.MYSQLConnector;
import Aron.Heinecke.ts3Manager.Lib.API.Mod;
import Aron.Heinecke.ts3Manager.Lib.API.ModRegisters;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;

/**
 * ModStats: server usage statistics<br>
 * per join / leave, Non-Blocking
 * @author Aron Heinecke
 */
public class ModStats implements Mod {
	Logger logger = LogManager.getLogger();
	private long last_update = 0L;
	private Instance instance;
	private Timer timer;
	private TimerTask timerdosnapshot;
	private Timer bufferTimer;
	private TimerTask taskBuffer;
	private PreparedStatement stm = null;
	private String sql;
	private String tableName;
	private MYSQLConnector conn = null;
	private SBuffer<DataElem> sBuffer = new SBuffer<DataElem>(2);
	private final int SCHEDULE_TIME = 15*60*1000; // 15 minutes
	private final int SPAM_INTERVALL = 1500; // > 1 second, as mysql and mariadb < 5.3 aren't storing MS, see #3
	private final Object lock = new Object();

	public ModStats(Instance instance) {
		this.instance = instance;
		logger.debug("Instance: {}", this.instance.getPSID());
		tableName = "ModStats_" + instance.getPSID();
		sql = String.format("INSERT INTO %s (`timestamp`,`clients`,`queryclients`) VALUES (?,?,?);", tableName);
		timerdosnapshot = new TimerTask() {
			@Override
			public void run() {
				logger.trace(tableName);
				addUpdate();
			}
		};
		taskBuffer = new TimerTask() {
			@Override
			public void run() {
				insertBuffer();
			}
		};
	}
	
	/**
	 * Flushes the current buffer into the DB
	 * Error hardened, will save failed elements for the next run
	 */
	private void insertBuffer(){
		long time = System.currentTimeMillis();
		sBuffer.swap();
		int size = sBuffer.getLastChannelSize();
		if(size == 0)
			return;
		Vector<DataElem> data = sBuffer.getLastChannel();
		sBuffer.clearOldChannel();
		try{
			conn = new MYSQLConnector();
			stm = conn.prepareStm(sql);
			// use the iterator directly, to remove the element if it's been used
			// by this we're able to save all left data on a sql failure
			for(Iterator<DataElem> iterator = data.iterator(); iterator.hasNext(); ){
				DataElem de = iterator.next();
				stm.setTimestamp(1, de.getTimestamp());
				stm.setInt(2, de.getClients());
				stm.setInt(3, de.getQueryclients());
				try{ // issue #3
					stm.executeUpdate();
				}catch(SQLIntegrityConstraintViolationException e){
					logger.warn("Ignoring dataset: {}\n{}",de.toString(),e);
				}
				iterator.remove();
			}
			stm.close();
			conn.disconnect();
			logger.debug("Buffer for {} flushed in {} MS, {} entrys",instance.getPSID(),System.currentTimeMillis() - time,size);
		}catch(SQLException | java.util.ConcurrentModificationException e){
			sBuffer.add(data);
			logger.error("Error flusing Buffer of SID {} \n{}",instance.getPSID(),e);
			logger.info("Delayed insertion of {} elements.",data.size());
		}
	}

	/**
	 * Request update<br>
	 * Lazy scheduling stops rapid updates on massive joins/leaves
	 */
	private void updateClients() {
		synchronized(lock){ // #3 prevention, still fast enough to withstand ModTest CN/DC spams
			if ((System.currentTimeMillis() - last_update) >= SPAM_INTERVALL) {
				last_update = System.currentTimeMillis();
				addUpdate();
				cancelLazySchedule();
			} else { // too short timespan, we'll create a datapoint later
				logger.debug("Scheduling later");
				timer = new Timer(true);
				timer.schedule(timerdosnapshot, 1000);
			}
		}
	}
	
	/**
	 * Abort delayed scheduling
	 */
	private void cancelLazySchedule(){
		if(timer != null){
			timer.cancel();
			timer = null;
		}
	}

	/**
	 * Internal update scheduler, shouldn't be called directly
	 * Called if there is a high rate of join/leaves after some time to avoid db spamming
	 */
	private void addUpdate() {
		try {
			HashMap<String, String> i = getInfo();
			sBuffer.add(new DataElem(Integer.valueOf(i.get("virtualserver_clientsonline")),
					Integer.valueOf(i.get("virtualserver_queryclientsonline"))));
		} catch (TS3ServerQueryException e) {
			logger.error(e);
		}
	}

	/**
	 * Get basic TS3Server Infos
	 * 
	 * @return HashMap with all infos
	 * @throws TS3ServerQueryException
	 */
	private HashMap<String, String> getInfo() throws TS3ServerQueryException {
		return instance.getTS3Connection().getConnector().getInfo(JTS3ServerQuery.INFOMODE_SERVERINFO, 0);
	}

	@Override
	public void handleClientJoined(HashMap<String, String> eventInfo) {
		updateClients();
	}

	@Override
	public void handleClientLeft(HashMap<String, String> eventInfo) {
		updateClients();
	}
	
	@Override
	public ModRegisters registerEvents(){
		return new ModRegisters.Builder()
				.eventServer(true)
				.build();
	}

	/**
	 * Create table for server if not existing
	 */
	@Override
	public void handleReady() {
		try {
			String table = String.format("CREATE TABLE IF NOT EXISTS `%s` ("
					+ " `clients` int(11) NOT NULL,"
					+ " `queryclients` int(11) NOT NULL,"
					+ " `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,"
					+ " PRIMARY KEY (`timestamp`)"
					+ ") ENGINE=InnoDB DEFAULT CHARSET=latin1 ROW_FORMAT=COMPRESSED COMMENT='%s'", tableName,
					instance.getTS3Connection().getConnector().getInfo(JTS3ServerQuery.INFOMODE_SERVERINFO, 0)
							.get("virtualserver_name"));
			MYSQLConnector conn = new MYSQLConnector();
			conn.execUpdateQuery(table);
			conn.disconnect();
		} catch (SQLException | TS3ServerQueryException e) {
			logger.error("{}", e);
		}
		updateClients();
		insertBuffer();
		bufferTimer = new Timer(true);
		bufferTimer.schedule(taskBuffer, SCHEDULE_TIME,SCHEDULE_TIME);
	}

	@Override
	public void handleClientMoved(HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleTextMessage(String eventType, HashMap<String, String> eventInfo) {
	}

	/**
	 * Shutdown, insert last data
	 */
	@Override
	public void handleShutdown() {
		logger.entry();
		timerdosnapshot.cancel();
		if (timer != null){ // cancel delayed schedule, schedule now
			timer.cancel();
			last_update = 0;
			updateClients();
		}
		synchronized(lock){ // block inserts
			try {
				if (stm != null){
					if (!stm.isClosed())
						stm.close();
				}
				if(conn != null)
					conn.disconnect();
			} catch (SQLException e) {
			}
			taskBuffer.cancel();
			if(bufferTimer != null) 
				bufferTimer.cancel();
			insertBuffer();
			logger.exit();
		}
	}

	/**
	 * Dataset for a single point in time
	 * @author Aron Heinecke
	 */
	class DataElem {
		private Timestamp timestamp;
		private int clients;
		private int queryclients;

		public DataElem(int clients, int queryclients) {
			this.timestamp = new Timestamp(System.currentTimeMillis());
			this.clients = clients;
			this.queryclients = queryclients;
		}

		public Timestamp getTimestamp() {
			return timestamp;
		}

		public int getClients() {
			return clients;
		}

		public int getQueryclients() {
			return queryclients;
		}
		
		@Override
		public String toString(){
			return "date: "+timestamp +" clients: "+ clients+" qe:"+ queryclients;
		}
	}
}
