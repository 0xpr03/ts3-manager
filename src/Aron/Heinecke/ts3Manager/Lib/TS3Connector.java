package Aron.Heinecke.ts3Manager.Lib;

import java.util.Timer;
import java.util.TimerTask;

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
	private int id;
	private String ip;
	private int port;
	private String user;
	private String password;
	private String name;
	private int channel;
	private U listener;
	private Timer timer = null;
	private TimerTask timertask = null;
	
	public TS3Connector(U listener, int id, String ip, int port, String user, String password, String name,int channel) throws TS3ServerQueryException{
		query = new JTS3ServerQuery();
		this.id = id;
		this.ip = ip;
		this.password = password;
		this.name = name;
		this.port = port;
		this.user = user;
		this.channel = channel;
		this.listener = listener;
		connect();
		
		interruptTimer();
	}
	
	/**
	 * Starts the timer<br>
	 * Also interrupts it, if it's already running
	 */
	private void interruptTimer(){
		if(timer != null){
			timertask.cancel();
			timer.cancel();
		}
		timer = new Timer(true);
		timertask = new TimerTask(){
			 public void run() {
		          checkConnect();
		     }
		};
		timer.schedule(timertask, 5*60*1000, 5*60*1000);
	}
	
	/**
	 * Connect is inside a new class for checkConnect
	 * @throws TS3ServerQueryException
	 */
	private void connect() throws TS3ServerQueryException{
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
	
	/**
	 * Register for events to be handled
	 * @param server
	 * @param channel
	 * @param text_server
	 * @param text_channel
	 * @param text_private
	 * @return
	 */
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
	
	/**
	 * Returns the connector & delays the connection keep alive
	 * @return
	 */
	public JTS3ServerQuery getConnector(){
		interruptTimer();
		return query;
	}
	
	/**
	 * Wrapper converting booleans to ints
	 * @param eventMode
	 * @param enable
	 * @throws TS3ServerQueryException
	 */
	private void registerEvent(int eventMode, boolean enable) throws TS3ServerQueryException{
		if(enable)
			query.addEventNotify(eventMode, 0);
	}
	
	public void disconnect(){
		timer.cancel();
		query.closeTS3Connection();
	}
	
	/**
	 * Needs to run all 
	 */
	private void checkConnect() {
		logger.entry();
		try {
			if ( !query.isConnected() ) {
				logger.warn("DC!");
				connect();
			} else {
				query.doCommand("hostinfo");
			}
		} catch (Exception e) {
			logger.error(e);
		}
		logger.exit();
	}
}
