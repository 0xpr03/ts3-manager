/**************************************************************************
a * Modular bot for teamspeak 3 (c)
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jdt.annotation.Nullable;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.MYSQLConnector;
import Aron.Heinecke.ts3Manager.Lib.SBuffer;
import Aron.Heinecke.ts3Manager.Lib.API.Mod;
import Aron.Heinecke.ts3Manager.Lib.API.ModRegisters;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;

/**
 * ModDetailedStats: very detailed user statistics<br>
 * per join / leave, Non-Blocking
 * @author Aron Heinecke
 */
public class ModDetailedStats implements Mod {
	Logger logger = LogManager.getLogger();
	private static final String C_LID = "clid";
	private static final String C_DBID = "client_database_id";
	private static final String C_NAME = "client_nickname";
	private static final int HOST_ID = 1;
	private static final int UNKNOWN_ID = -1;
	private static final String UNKNOWN_NAME = "Unknown ID placeholder";
	private Instance instance;
	private Timer bufferTimer;
	private TimerTask taskBuffer;
	private PreparedStatement stmStats = null;
	private final String sqlStats;
	private final String sqlNames;
	private final String tableStats;
	private final String tableNames;
	private MYSQLConnector conn = null;
	private SBuffer<DataElem> sBuffer = new SBuffer<DataElem>(2);
	
	// temporary ID : DB ID
	private HashMap<Integer,Integer> clientMapping;
	private final int SCHEDULE_TIME = 15*60*1000; // 15 minutes
	private PreparedStatement stmNames;

	/**
	 * New detailed stats module
	 * @param instance
	 */
	public ModDetailedStats(Instance instance) {
		logger.debug("Instance: {}", instance.getPSID());
		this.instance = instance;
		clientMapping = new HashMap<>(20);
		tableStats = "mDSStats_" + instance.getPSID();
		tableNames = "mDSNames_" + instance.getPSID();
		sqlStats = String.format("INSERT INTO `%s` (`timestamp`,`client_id`,`online`) VALUES (?,?,?);", tableStats);
		sqlNames = String.format("INSERT INTO `%s` (`client_id`,`name`) VALUES (?,?) ON DUPLICATE KEY UPDATE name = VALUES(name);", tableNames);
		
		MYSQLConnector connector = null;
		try {
			// test statements & insert unknown ID name
			connector = new MYSQLConnector();
			DataElem elem = new DataElem(UNKNOWN_ID, true, UNKNOWN_NAME);
			PreparedStatement stm = connector.prepareStm(sqlNames);
			stm.setInt(1, elem.client);
			stm.setString(2, elem.name.get());
			stm.executeUpdate();
			stm.close();
			connector.prepareStm(sqlStats).close();
		} catch (SQLException e) {
			logger.fatal(e);
		} finally {
			if(connector != null)
				connector.disconnect();
		}
		
		taskBuffer = new TimerTask() {
			@Override
			public void run() {
				insertBuffer(false);
			}
		};
	}
	
	/**
	 * Flushes the current buffer into the DB
	 * Error hardened, will save failed elements for the next run
	 */
	private synchronized void insertBuffer(final boolean closingLogger){
		long time = System.currentTimeMillis();
		sBuffer.swap();
		int size = sBuffer.getLastChannelSize();
		if(size == 0)
			return;
		Collection<DataElem> data = sBuffer.getLastChannel();
		try{
			conn = new MYSQLConnector();
			stmStats = conn.prepareStm(sqlStats);
			stmNames = conn.prepareStm(sqlNames);
			// use the iterator directly, to remove the element if it's been used
			// by this we're able to save all left data on a sql failure
			for(Iterator<DataElem> iterator = data.iterator(); iterator.hasNext(); ){
				DataElem de = iterator.next();
				if(de.client == HOST_ID) { // ignore hoster query
					iterator.remove();
					continue;
				}
				stmStats.setTimestamp(1, de.timestamp);
				stmStats.setInt(2, de.client);
				stmStats.setBoolean(3, de.online);
				try{ // issue #3
					stmStats.executeUpdate();
					if(de.name.isPresent()) {
						stmNames.setInt(1, de.client);
						stmNames.setString(2, de.name.get());
						stmNames.executeUpdate();
					}
				}catch(SQLIntegrityConstraintViolationException e){
					try {
						logger.warn("Ignoring dataset: {}\n{}",de.toString(),e);
					} catch (Exception e2) {
						// do nothing, ignore closed loggers on shutdown
					}
				}
				iterator.remove();
			}
			stmStats.close();
			stmNames.close();
			conn.disconnect();
			logger.debug("Buffer for {} flushed in {} MS, {} entrys",instance.getPSID(),System.currentTimeMillis() - time,size);
		}catch(SQLException | java.util.ConcurrentModificationException e){
			sBuffer.add(data);
			logger.error("Error flushing Buffer of SID {} \n{}",instance.getPSID(),e);
			logger.info("Delayed insertion of {} elements.",data.size());
		}
		sBuffer.clearOldChannel();
	}

	@Override
	public void handleClientJoined(HashMap<String, String> eventInfo) {
		int dbID = Integer.valueOf(eventInfo.get(C_DBID));
		int lID = Integer.valueOf(eventInfo.get(C_LID));
		String name = eventInfo.get(C_NAME);
		DataElem elem = new DataElem(dbID, true,name);
		sBuffer.add(elem);
		clientMapping.put(lID, dbID);
		logger.exit();
	}

	@Override
	public void handleClientLeft(HashMap<String, String> eventInfo) {
		int lID = Integer.valueOf(eventInfo.get(C_LID));
		if(clientMapping.containsKey(lID)) {
			sBuffer.add(new DataElem(clientMapping.get(lID), false));
		} else {
			sBuffer.add(new DataElem(UNKNOWN_ID,false));
			logger.info("No info about leaving client.");
		}
		logger.exit();
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
			String serverName = instance.getTS3Connection()
			.getConnector().getInfo(JTS3ServerQuery.INFOMODE_SERVERINFO, 0).get("virtualserver_name");
			String[] tables = {String.format("CREATE TABLE IF NOT EXISTS `%s` ("
						+ " `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
						+ " `client_id` int(11) NOT NULL,"
						+ " `online` bit(1) NOT NULL,"
						+ " PRIMARY KEY (`client_id`,`timestamp`),"
						+ " KEY `timestamp` (`timestamp`),"
						+ " KEY `client_id` (`client_id`)"
						+ ") ENGINE=InnoDB DEFAULT CHARSET=latin1 ROW_FORMAT=COMPRESSED COMMENT='%s'", tableStats, serverName)
					,
						String.format("CREATE TABLE IF NOT EXISTS `%s` ("
						+ " `name` VARCHAR(100) NOT NULL,"
						+ " `client_id` int(11) NOT NULL,"
						+ " PRIMARY KEY (`client_id`),"
						+ " KEY `name` (`name`)"
						+ ") ENGINE=InnoDB CHARACTER SET 'utf8mb4' ROW_FORMAT=COMPRESSED COMMENT='%s'", tableNames, serverName)
			};
			MYSQLConnector conn = new MYSQLConnector();
			for(String table : tables)  {
				PreparedStatement stmt = conn.prepareStm(table);
				stmt.executeQuery();
				stmt.close();
			}
			conn.disconnect();
		} catch (SQLException | TS3ServerQueryException e) {
			logger.error("{}", e);
		}
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
		taskBuffer.cancel();
		if(bufferTimer != null) 
			bufferTimer.cancel();
		insertBuffer(true);
		try {
			if (stmStats != null){
				if (!stmStats.isClosed())
					stmStats.close();
			}
			if(stmNames != null) {
				if(!stmNames.isClosed())
					stmNames.close();
			}
			if(conn != null)
				conn.disconnect();
		} catch (SQLException e) {
		}
	}

	/**
	 * Data entry for a single point in time
	 * @author Aron Heinecke
	 */
	class DataElem {
		public final Timestamp timestamp;
		public final int client;
		public final boolean online;
		public final Optional<String> name;
		
		/**
		 * Creates a new DataElement, without a name
		 * @param client
		 * @param online
		 */
		public DataElem(final int client, final boolean online) {
			this(client,online,null);
		}

		/**
		 * Creates a new DateElement
		 * @param client client ID
		 * @param online client is now online/offline
		 * @param name client name, nullable
		 */
		public DataElem(final int client, final boolean online,@Nullable String name) {
			this.timestamp = new Timestamp(System.currentTimeMillis());
			this.client = client;
			this.online = online;
			this.name = Optional.ofNullable(name);
		}

		@Override
		public String toString(){
			return "date: "+timestamp +" client: "+ client+" online: "+online;
		}
	}
}
