package Aron.Heinecke.ts3Manager.Mods;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.API.ModEvent;
import Aron.Heinecke.ts3Manager.Lib.API.TS3Event;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;

/**
 * ModStats server usage statistics
 * @author Aron Heinecke
 */
public class ModStats implements ModEvent, TS3Event {
	Logger logger = LogManager.getLogger();
	private long last_update = 0L;
	private boolean blocked = false;
	private Instance<?> instance;
	private Timer timer = new Timer(false);
	private TimerTask timerdosnapshot;
	private PreparedStatement stm;
	private String sql;
	private String tableName;
	
	public ModStats(Instance<?> instance){
		this.instance = instance;
		logger.debug("Instance: {}",this.instance.getSID());
		tableName = "ModStats_"+instance.getSID();
		sql = String.format("INSERT INTO %s (`clients`,`queryclients`) VALUES (?,?);",tableName);
		timerdosnapshot = new TimerTask() {
			@Override
			public void run() {
				scheduleUpdate();
			}
		};
	}
	
	/**
	 * Request update<br>
	 * Lazy scheduling stops rapid updates on massive join/leaves
	 */
	private void updateClients() {
		if(blocked){
			return;
		}
		if(System.currentTimeMillis() - last_update >= 1000){
			scheduleUpdate();
		}else{
			blocked = true;
			timer.schedule(timerdosnapshot, 1000);
		}
	}
	
	/**
	 * Internal update scheduler, shouldn't be called directly
	 */
	private void scheduleUpdate(){
		try {
			long start = System.currentTimeMillis();
			HashMap<String,String> i = getInfo();
			stm.setInt(1, Integer.valueOf(i.get("virtualserver_clientsonline")));
			stm.setInt(2, Integer.valueOf(i.get("virtualserver_queryclientsonline")));
			stm.executeUpdate();
			logger.debug("insert took {} ms",System.currentTimeMillis() - start);
		} catch (TS3ServerQueryException | SQLException e) {
			logger.error(e);
		} finally{
			last_update = System.currentTimeMillis();
			blocked = false;
		}
	}
	
	/**
	 * Get basic TS3Server Infos
	 * @return HashMap with all infos
	 * @throws TS3ServerQueryException
	 */
	private HashMap<String, String> getInfo() throws TS3ServerQueryException{
		return instance.getTS3Connection().getConnector().getInfo(JTS3ServerQuery.INFOMODE_SERVERINFO, 0);
	}

	@Override
	public void handleClientJoined(HashMap<String, String> eventInfo) {
		logger.debug("Client joined");
		updateClients();
	}

	@Override
	public void handleClientLeft(HashMap<String, String> eventInfo) {
		logger.debug("Client left");
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
	public boolean needs_MYSQL() {
		return true;
	}

	@Override
	public void handleReady() {
		try{
			String table = String.format("CREATE TABLE IF NOT EXISTS `%s` ("
				+" `clients` int(11) NOT NULL,"
				+" `queryclients` int(11) NOT NULL,"
				+" `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,"
				+" PRIMARY KEY (`timestamp`)"
				+") ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='%s'",tableName,instance.getTS3Connection().getConnector().getInfo(JTS3ServerQuery.INFOMODE_SERVERINFO, 0).get("virtualserver_name"));
			instance.getMysqlconnector().execUpdateQuery(table);
		}catch(SQLException | TS3ServerQueryException e){
			logger.error("{}",e);
		}
		try {
			stm = instance.getMysqlconnector().prepareStm(sql);
		} catch (SQLException e) {
			logger.error("{}",e);
		}
		updateClients();
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
		blocked = true;
		timerdosnapshot.cancel();
		timer.cancel();
		try {
			if(stm != null)
				stm.close();
		} catch (SQLException e) {}
	}
}
