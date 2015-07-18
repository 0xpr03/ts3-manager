package Aron.Heinecke.ts3Manager.Lib;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;
import de.stefan1200.jts3serverquery.TeamspeakActionListener;

/**
 * TS3Connector containing also the actionlistener
 * @author Aron Heinecke
 * @param <U>
 */
public class TS3Connector<U extends TeamspeakActionListener> {
	Logger logger = LogManager.getLogger();
	JTS3ServerQuery query;
	
	public TS3Connector(U listener, int id, String ip, int port, String user, String password, String name,int channel) throws TS3ServerQueryException{
		query = new JTS3ServerQuery();
		try{
			query.connectTS3Query(ip, port);
			query.loginTS3(user, password);
			query.selectVirtualServer(id);
			query.setTeamspeakActionListener(listener);
		}catch (TS3ServerQueryException sqe){
			logger.fatal("Instance id {} Error during Connection establishing!");
			if (sqe.getFailedPermissionID() >= 0)
				logger.info("Missing permissions");
			throw sqe;
		}catch (Exception e){
			logger.fatal(e);
			return;
		}
		try{
			query.moveClient(query.getCurrentQueryClientID(), channel, null);
		}catch(TS3ServerQueryException e){
			logger.warn("Error on joining channel!");
		}
		try{
			query.setDisplayName(name);
		}catch (TS3ServerQueryException sqe){
			logger.info("Name already taken");
			query.setDisplayName(name+System.currentTimeMillis());
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
}
