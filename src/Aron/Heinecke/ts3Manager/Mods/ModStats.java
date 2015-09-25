package Aron.Heinecke.ts3Manager.Mods;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.SBuffer;
import Aron.Heinecke.ts3Manager.Lib.MYSQLConnector;
import Aron.Heinecke.ts3Manager.Lib.API.ModEvent;
import Aron.Heinecke.ts3Manager.Lib.API.TS3Event;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;

/**
 * ModStats server usage statistics
 * Non-Blocking 
 * @author Aron Heinecke
 */
public class ModStats implements ModEvent, TS3Event {
	Logger logger = LogManager.getLogger();
	private long last_update = 0L;
	private Instance<?> instance;
	private Timer timer;
	private TimerTask timerdosnapshot;
	private Timer bufferTimer;
	private TimerTask taskBuffer;
	private PreparedStatement stm = null;
	private String sql;
	private String tableName;
	private MYSQLConnector conn = null;
	private SBuffer<DataElem> sBuffer = new SBuffer<DataElem>(2);

	public ModStats(Instance<?> instance) {
		this.instance = instance;
		logger.debug("Instance: {}", this.instance.getSID());
		tableName = "ModStats_" + instance.getSID();
		sql = String.format("INSERT INTO %s (`timestamp`,`clients`,`queryclients`) VALUES (?,?,?);", tableName);
		timerdosnapshot = new TimerTask() {
			@Override
			public void run() {
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
	 */
	private void insertBuffer(){
		try{
			long time = System.currentTimeMillis();
			sBuffer.swap();
			int size = sBuffer.getLastChannelSize();
			if(size == 0)
				return;
			conn = new MYSQLConnector();
			stm = conn.prepareStm(sql);
			for(DataElem de : sBuffer.getLastChannel()){
				stm.setTimestamp(1, de.getTimestamp());
				stm.setInt(2, de.getClients());
				stm.setInt(3, de.getQueryclients());
				stm.executeUpdate();
			}
			sBuffer.clearOldChannel();
			stm.close();
			conn.disconnect();
			logger.debug("Buffer for {} flushed in {} MS, {} entrys",instance.getSID(),System.currentTimeMillis() - time,size);
		}catch(SQLException | java.util.ConcurrentModificationException e){
			logger.error("Error flusing Buffer of SID {}",instance.getSID());
			logger.error(e);
		}
	}

	/**
	 * Request update<br>
	 * Lazy scheduling stops rapid updates on massive join/leaves
	 */
	private void updateClients() {
		if (System.currentTimeMillis() - last_update >= 1000) {
			last_update = System.currentTimeMillis();
			addUpdate();
		} else {
			logger.debug("Scheduling later");
			timer = new Timer(false);
			timer.schedule(timerdosnapshot, 1000);
		}
	}

	/**
	 * Internal update scheduler, shouldn't be called directly
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
		logger.debug("Client joined {}", tableName);
		updateClients();
	}

	@Override
	public void handleClientLeft(HashMap<String, String> eventInfo) {
		logger.debug("Client left {}", tableName);
		updateClients();
	}

	@Override
	public boolean needs_Event_Channel() {
		return false;
	}

	@Override
	public boolean needs_Event_Server() {
		// TODO Auto-generated method stub
		return true;
	}

	@Override
	public boolean needs_Event_TextChannel() {
		return false;
	}

	@Override
	public boolean needs_Event_TextPrivate() {
		return false;
	}

	@Override
	public boolean needs_Event_TextServer() {
		return false;
	}

	@Override
	public void handleReady() {
		try {
			String table = String.format("CREATE TABLE IF NOT EXISTS `%s` (" + " `clients` int(11) NOT NULL,"
					+ " `queryclients` int(11) NOT NULL," + " `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,"
					+ " PRIMARY KEY (`timestamp`)"
					+ ") ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='%s'", tableName, instance.getTS3Connection()
							.getConnector().getInfo(JTS3ServerQuery.INFOMODE_SERVERINFO, 0).get("virtualserver_name"));
			new MYSQLConnector().execUpdateQuery(table);
		} catch (SQLException | TS3ServerQueryException e) {
			logger.error("{}", e);
		}
		updateClients();
		insertBuffer();
		bufferTimer = new Timer(true);
		bufferTimer.schedule(taskBuffer, 15*60*1000,15*60*1000); // 15 min
	}

	@Override
	public void handleClientMoved(HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleTextMessage(String eventType, HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleShutdown() {
		logger.entry();
		timerdosnapshot.cancel();
		if (timer != null)
			timer.cancel();
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
	}
}
