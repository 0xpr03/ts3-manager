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

public class ModStats implements ModEvent, TS3Event {
	Logger logger = LogManager.getLogger();
	private long last_update = 0L;
	private boolean blocked = false;
	private Instance<?> instance;
	private Timer timer = new Timer(false);
	private TimerTask timerdosnapshot;
	private PreparedStatement stm;
	private String sql;
	
	public ModStats(Instance<?> instance){
		this.instance = instance;
		logger.debug("Instance: {}",this.instance.getSID());
		sql = "INSERT INTO %s (``) VALUES (?);";
		sql.replaceFirst("%s", String.valueOf(instance.getSID()));
		timerdosnapshot = new TimerTask() {
			@Override
			public void run() {
				updateClients();
			}
		};
	}
	
	private synchronized void updateClients(){
		if(blocked){
			return;
		}
		if(System.currentTimeMillis() - last_update > 1000){
			HashMap<String, String> i;
			try {
				i = instance.getTS3Connection().getConnector().getInfo(JTS3ServerQuery.INFOMODE_SERVERINFO, 0);
				logger.info("{}",i);
//				stm.setInt(1, );
//				stm.executeUpdate();
			} catch (TS3ServerQueryException e) {
				logger.error(e);
			}
			blocked = false;
		}else{
			blocked = true;
			timer.schedule(timerdosnapshot, 1000);
		}
	}

	@Override
	public void handleClientJoined(HashMap<String, String> eventInfo) {
		logger.debug("Client joined, ");
		updateClients();
	}

	@Override
	public void handleClientLeft(HashMap<String, String> eventInfo) {
		logger.debug("Client left, ");
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
//		try{
//			String table = "CREATE TABLE IF NOT EXISTS `%s` ("
//				+" `clients` int(11) NOT NULL,"
//				+" `timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
//				+" PRIMARY KEY (`timestamp`)"
//				+") ENGINE=InnoDB DEFAULT CHARSET=latin1 COMMENT='%d'";
//			HashMap<String, String> i = instance.getTS3Connection().getConnector().getInfo(JTS3ServerQuery.INFOMODE_SERVERINFO, 0);
//			instance.getMysqlconnector().execUpdateQuery(table.replace("%s", "ModStats_"+instance.getSID()));
//		}catch(SQLException | TS3ServerQueryException e){
//			logger.error("{}",e);
//		}
		try {
			stm = instance.getMysqlconnector().prepareStm(sql);
		} catch (SQLException e) {
			logger.error("{}",e);
		}
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
			stm.close();
		} catch (SQLException e) {}
	}
}
