/**************************************************************************
a * Modular bot for teamspeak 3 (c)
 * Copyright (C) 2015-2018 Aron Heinecke
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

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

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
 * ModDetailedStats: daily online time per user
 * @author Aron Heinecke
 */
public class ModDStatsEvent implements Mod {
	Logger logger = LogManager.getLogger();
	private static final String C_LID = "clid";
	private static final String C_DBID = "client_database_id";
	private static final String C_NAME = "client_nickname";
	private static final int HOST_ID = 1;
	private static final int UNKNOWN_ID = -1;
	private static final String UNKNOWN_NAME = "Unknown ID & bot placeholder";
	private Instance instance;
	private Timer bufferTimer;
	private TimerTask taskBuffer;
	private PreparedStatement stmStats = null;
	private final String sqlStats;
	private final String sqlNames;
	private final String tableStats;
	private final String tableNames;
	private MYSQLConnector conn = null;
	
	private ClientStorage clientStorage = new ClientStorage();
	private List<Client> backlog = new LinkedList<>();
	
	private final int SCHEDULE_TIME = 15*60*1000; // 15 minutes in ms
	private PreparedStatement stmNames;

	/**
	 * New detailed stats module
	 * @param instance
	 */
	public ModDStatsEvent(Instance instance) {
		logger.debug("Instance: {}", instance.getID());
		this.instance = instance;
		tableStats = "mDSStats2_" + instance.getID();
		tableNames = "mDSNames_" + instance.getID();
		sqlStats = String.format("INSERT INTO `%s` (`date`,`client_id`,`time`) VALUES (?,?,?) ON DUPLICATE KEY UPDATE `time` = `time`+VALUES(`time`);", tableStats);
		sqlNames = String.format("INSERT INTO `%s` (`client_id`,`name`) VALUES (?,?) ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);", tableNames);
		
		taskBuffer = new TimerTask() {
			@Override
			public void run() {
				try {
					insertBuffer(false);
				} catch(Exception e) {
					logger.error("Insert task:\n{}",e);
				}
			}
		};
	}
	
	@Override
	public void handleReconnect() {
		try {
			Vector<HashMap<String, String>> result = instance.getTS3Connection().getConnector().getList(JTS3ServerQuery.LISTMODE_CLIENTLIST);
			logger.info("Found {} clients online.",result.size());
			for(HashMap<String, String> map : result) {
				try {
					int conn_id = Integer.parseInt(map.get(C_LID));
					int client_id = Integer.parseInt(map.get(C_DBID));
					String name = map.get(C_NAME);
					clientStorage.connect(client_id, conn_id, name);
				} catch (NullPointerException e) {
					logger.error("Unable to get details about client on cache refill: {}",map);
				}
			}
		} catch (TS3ServerQueryException e) {
			logger.error("Unable to read clientlist on cache refill: {}",e);
		}
	}
	
	@Override
	public void handleConnectionLoss() {
		clientStorage.forceDisconnect();
	}

	/**
	 * Flushes the current buffer into the DB
	 * Error hardened, will save failed elements for the next run
	 */
	private synchronized void insertBuffer(final boolean closingLogger){
		if(!closingLogger)
			logger.entry();
		long time = System.currentTimeMillis();
		Date date = clientStorage.getDate();
		Collection<Client> data = clientStorage.swap();
		data.addAll(backlog);
		backlog.clear();
		if(data.size() == 0)
			return;
		int startSize = data.size();
		try{
			conn = new MYSQLConnector();
			stmStats = conn.prepareStm(sqlStats);
			stmNames = conn.prepareStm(sqlNames);
			// use the iterator directly, to remove the element if it's been used
			// by this we're able to save all left data on a sql failure
			for(Iterator<Client> iterator = data.iterator(); iterator.hasNext(); ){
				Client client = iterator.next();
				if(client.client == HOST_ID) { // ignore hoster query
					iterator.remove();
					continue;
				}
				stmStats.setDate(1, date);
				stmStats.setInt(2, client.getClientID());
				stmStats.setInt(3, client.getOnlineTimeCappedInt());
				try{ // issue #3
					stmStats.executeUpdate();
					if(client.name.isPresent()) {
						stmNames.setInt(1, client.getClientID());
						stmNames.setString(2, client.getName().get());
						stmNames.executeUpdate();
					}
				}catch(SQLIntegrityConstraintViolationException e){
					try {
						logger.warn("Ignoring dataset: {} {}\n{}",date,client.toString(),e);
					} catch (Exception e2) {
						// do nothing, ignore closed loggers on shutdown
					}
				}
				iterator.remove();
			}
			stmStats.close();
			stmNames.close();
			conn.disconnect();
			if(!closingLogger)
				logger.debug("Buffer for {} flushed in {} MS, {} entries",instance.getID(),System.currentTimeMillis() - time,startSize);
		}catch(SQLException | java.util.ConcurrentModificationException e){
			backlog.addAll(data); // retry, possible DB downtime
			if(!closingLogger) {
				logger.error("Error flushing Buffer of ID {} \n{}",instance.getID(),e);
				logger.info("Delayed insertion of {}/{} elements.",data.size(),startSize);
			}
		}
	}

	@Override
	public void handleClientJoined(HashMap<String, String> eventInfo) {
		int client_id = Integer.valueOf(eventInfo.get(C_DBID));
		int conn_id = Integer.valueOf(eventInfo.get(C_LID));
		String name = eventInfo.get(C_NAME);
		clientStorage.connect(client_id, conn_id, name);
	}

	@Override
	public void handleClientLeft(HashMap<String, String> eventInfo) {
		int conn_id = Integer.valueOf(eventInfo.get(C_LID));
		if(!clientStorage.disconnect(conn_id)) { // something went wrong, force DC handling, refreshing cache
			logger.info("Unknown disconnect, forcing cache refresh");
			handleConnectionLoss();
			handleReconnect();
		}
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
		MYSQLConnector conn = null;
		try {
			String serverName = instance.getTS3Connection()
			.getConnector().getInfo(JTS3ServerQuery.INFOMODE_SERVERINFO, 0).get("virtualserver_name");
			String[] tables = {String.format("CREATE TABLE IF NOT EXISTS `%s` ("
						+ " `date` date NOT NULL,"
						+ " `client_id` int(11) NOT NULL,"
						+ " `time` INT NOT NULL,"
						+ " PRIMARY KEY (`client_id`,`date`),"
						+ " KEY `date` (`date`),"
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
			conn = new MYSQLConnector();
			for(String table : tables)  {
				PreparedStatement stmt = conn.prepareStm(table);
				stmt.executeQuery();
				stmt.close();
			}
			conn.disconnect();
		} catch (SQLException | TS3ServerQueryException e) {
			logger.error("{}", e);
		} finally {
			if(conn != null) {
				conn.disconnect();
			}
		}
		
		MYSQLConnector connector = null;
		try {
			// test statements & insert unknown ID name
			connector = new MYSQLConnector();
			Client elem = new Client(UNKNOWN_ID, 0, UNKNOWN_NAME);
			logger.debug(elem);
			if(!elem.disconnect(0) || elem.isOnline()) {
				logger.fatal("Client disconnect test failed! {}",elem);
				return;
			}
			PreparedStatement stm = connector.prepareStm(sqlStats);
			stm.setDate(1, clientStorage.date);
			stm.setInt(2, elem.getClientID());
			stm.setInt(3, elem.getOnlineTimeCappedInt());
			stm.executeUpdate();
			stm.close();
			PreparedStatement stmName = connector.prepareStm(sqlNames);
			stmName.setInt(1, elem.getClientID());
			stmName.setString(2, elem.getName().get());
			stmName.executeUpdate();
			stmName.close();
			
			this.handleReconnect(); // get list of users
			
			// success
			bufferTimer = new Timer(true);
			bufferTimer.schedule(taskBuffer, SCHEDULE_TIME,SCHEDULE_TIME);
			logger.debug("mod started successfully!");
		} catch (SQLException e) {
			logger.fatal("Initial test insert failed, mod shutdown!\n{}", e);
		} finally {
			if(connector != null)
				connector.disconnect();
		}
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
		try {
			insertBuffer(true);
		} catch (Exception e) {
			System.out.println(e);
		}
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
	 * Client storage<br>
	 * Stores current & old clients, storing online times
	 * @author Aron Heinecke
	 *
	 */
	private class ClientStorage {
		// client id : connection
		private HashMap<Integer,Client> liveConnClientID = new HashMap<>();
		// connection id : connection
		private HashMap<Integer,Client> liveConnID = new HashMap<>();
		SBuffer<Client> oldConnections = new SBuffer<>(2);
		private Date date = new Date(System.currentTimeMillis());
		
		/**
		 * Get date for current data set
		 * @return
		 */
		public Date getDate() {
			return date;
		}
		
		/**
		 * Handle connect
		 * @param client_id client ID
		 * @param connectionID connection ID <b>reused client ID</b>
		 * @param name Client Name
		 */
		public void connect(int client_id, int connectionID,@Nullable String name) {
			synchronized (liveConnClientID) {
				if(liveConnClientID.containsKey(client_id)) {
					Client client = liveConnClientID.get(client_id);
					client.connect(connectionID,name);
					liveConnID.put(connectionID, client);
				} else {
					Client client = new Client(client_id, connectionID, name);
					liveConnID.put(connectionID, client);
					liveConnClientID.put(client_id, client);
				}
			}
		}
		
		/**
		 * Swaps the client storage, resetting all connections to current time<br>
		 * <b>Date refresh</b>
		 * @return List of clients that were stored
		 */
		public List<Client> swap(){
			synchronized (liveConnClientID) {
				date = new Date(System.currentTimeMillis());
				oldConnections.swap();
				List<Client> lst = oldConnections.getLastChannel();
				oldConnections.clearOldChannel();
				for(Client client : liveConnClientID.values()) {
					lst.add(client.swap());
				}
				return lst;
			}
		}
		
		/**
		 * Force disconnect of all client<br>
		 * To be used for bot connection loss
		 */
		public void forceDisconnect() {
			synchronized (liveConnClientID) {
				for(Client client : liveConnClientID.values()) {
					client.forceDisconnect();
					oldConnections.add(client);
				}
				liveConnClientID.clear();
				liveConnID.clear();
			}
		}
		
		/**
		 * Handle disconnect
		 * @param connectionID
		 * @return true if connectionID is known, false on miss
		 */
		public boolean disconnect(int connectionID) {
			synchronized (liveConnClientID) {
				if(liveConnID.containsKey(connectionID)) {
					Client client = liveConnID.get(connectionID);
					if(client.disconnect(connectionID)) {
						liveConnID.remove(connectionID);
						liveConnClientID.remove(client.client);
						oldConnections.add(client);
					}
				} else {
					logger.warn("Unknown disconnect id {}",connectionID);
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * Client object, 
	 * Data entry for a single connection
	 * @author Aron Heinecke
	 */
	private class Client {
		public final LinkedList<Integer> connections;
		public final int client;
		private Optional<String> name;
		private Instant joinTime;
		private Long onlineTime = null;

		/**
		 * Creates a new Client object
		 * @param client client ID
		 * @param connID current connection ID
		 * @param name client name, nullable
		 */
		public Client(final int client, final int connID,@Nullable String name) {
			this.connections = new LinkedList<>();
			connections.add(connID);
			this.client = client;
			this.name = Optional.ofNullable(name);
			this.joinTime = Instant.now();
		}
		
		/**
		 * Create a disconnected Client with set time<br>
		 * For repeating DB inserts using the swap mechanism.
		 * @param client
		 * @param duration online time
		 */
		private Client(Client parent, long duration) {
			this.connections = null;
			this.client = parent.client;
			this.name = parent.name;
			this.joinTime = parent.joinTime;
			this.onlineTime = duration;
		}
		
		/**
		 * Add connection
		 * @param connID Connection ID
		 * @throws IllegalStateException if already disconnected
		 */
		public void connect(final int connID, final String name) {
			if(!isOnline()) {
				throw new IllegalStateException("Cannot add connections to finished Client");
			}
			connections.add(connID);
			if(name != null) {
				this.name = Optional.of(name);
			}
		}
		
		/**
		 * Handle disconnect, sets onlineTime
		 * @throws IllegalStateException if disconnect was called before
		 * @return true if no alive connections remain, setting the total time & finalizing this connection
		 */
		public boolean disconnect(final int connectionID) {
			if(isOnline()) {
				if(connections.remove((Integer) connectionID)) {
					if(connections.size() == 0) {
						onlineTime = calcOnlineTimeNow();
						return true;
					}
				} else {
					logger.error("Unknown connectionID {} for client! {}",connectionID,this);
				}
				return false;
			} else {
				throw new IllegalStateException("Client life exceeded for further disconnects!");
			}
		}
		
		/**
		 * Calculate online time from start to now
		 * @return Time of duration in seconds, does not change the Client object
		 */
		private long calcOnlineTimeNow() {
			return Duration.between(joinTime, Instant.now()).getSeconds();
		}
		
		/**
		 * Returns a clone with the time until now, resets this clients time to now
		 */
		public Client swap() {
			Client client = new Client(this,calcOnlineTimeNow());
			this.joinTime = Instant.now();
			return client;
		}
		
		/**
		 * Force disconnect, set duration to time until now
		 */
		public void forceDisconnect() {
			this.onlineTime = calcOnlineTimeNow();
			this.connections.clear();
		}
		
		/**
		 * Is online
		 * @return true if "online"<br>
		 * 
		 * Note: online means there are known connection IDs
		 */
		public boolean isOnline() {
			return onlineTime == null;
		}
		
		/**
		 * Get online time
		 * @return time in secs/null, check via isDisconnected
		 */
		@Nullable
		public Long getOnlineTime() {
			return onlineTime;
		}
		
		/**
		 * Returns the online time as int, capped to Integer.MAX
		 * @return time in secs, check via isDisconnected
		 */
		@Nullable
		public int getOnlineTimeCappedInt() {
			try {
				return Math.toIntExact(getOnlineTime());
			} catch (ArithmeticException e) {
				logger.fatal("Online time reached max!");
				return Integer.MAX_VALUE;
			}
		}
		
		/**
		 * Returns the client id of this client
		 * @return client_id
		 */
		public int getClientID() {
			return this.client;
		}
		
		/**
		 * Returns the latest name of this client
		 * @return Optional of String
		 */
		public Optional<String> getName() {
			return this.name;
		}

		@Override
		public String toString(){
			return "connection IDs: "+connections.size()+" client: "+ client+" online: "+onlineTime;
		}
	}
}
