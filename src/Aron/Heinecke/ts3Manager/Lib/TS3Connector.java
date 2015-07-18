package Aron.Heinecke.ts3Manager.Lib;

import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;
import de.stefan1200.jts3serverquery.TeamspeakActionListener;

/**
 * TS3Connector containing also the actionlistener
 * @author Aron Heinecke
 */
public class TS3Connector implements TeamspeakActionListener {
	Logger logger = LogManager.getLogger();
	JTS3ServerQuery query;
	
	public TS3Connector(int id, String ip, int port, String user, String password) throws TS3ServerQueryException{
		query = new JTS3ServerQuery();
		try{
			query.connectTS3Query(ip, port);
			query.loginTS3(user, password);
			query.setTeamspeakActionListener(this);
			query.selectVirtualServer(id);
		}catch (TS3ServerQueryException sqe){
			logger.fatal("Instance id {} Error during Connection establishing!");
			
			if (sqe.getFailedPermissionID() >= 0)
				logger.info("Missing permissions");
			throw sqe;
		}catch (Exception e){
			logger.fatal(e);
		}
	}
	
	public boolean registerEvents(boolean server, boolean channel, boolean text_server, boolean text_channel, boolean text_private){
		try {
			registerEvent(JTS3ServerQuery.EVENT_MODE_SERVER, server);
			registerEvent(JTS3ServerQuery.EVENT_MODE_CHANNEL, channel);
			registerEvent(JTS3ServerQuery.EVENT_MODE_TEXTSERVER, text_server);
			registerEvent(JTS3ServerQuery.EVENT_MODE_TEXTCHANNEL, text_channel);
			registerEvent(JTS3ServerQuery.EVENT_MODE_TEXTPRIVATE, text_private);
		return true;
		} catch (TS3ServerQueryException e) {
			logger.error("Error registering events! {}",e);
			return false;
		}
	}
	
	private void registerEvent(int eventMode, boolean enable) throws TS3ServerQueryException{
		if(enable)
			query.addEventNotify(eventMode, 0);
	}

	@Override
	public void teamspeakActionPerformed(String eventType, HashMap<String, String> eventInfo) {
		
	}
}
