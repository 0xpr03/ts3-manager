package Aron.Heinecke.ts3Manager;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Lib.MYSQLConnector;
import Aron.Heinecke.ts3Manager.Lib.TS3Connector;
import Aron.Heinecke.ts3Manager.Lib.API.ModEvent;
import Aron.Heinecke.ts3Manager.Lib.API.TS3Event;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;
import de.stefan1200.jts3serverquery.TeamspeakActionListener;

/**
 * Instance represents one server
 * @author Aron Heinecke
 * @param <E>
 */
public class Instance<E extends ModEvent & TS3Event> implements TeamspeakActionListener {
	private Logger logger = LogManager.getLogger();
	private int SID;
	private boolean retry;
	private String BOT_NAME;
	private int CHANNEL;
	private HashMap<String, Boolean> enabled_features;
	private Vector<E> mods = new Vector<E>();
	private Vector<E> event_joined = new Vector<E>();
	private Vector<E> event_left = new Vector<E>();
	private Vector<E> event_chat = new Vector<E>();
	private Vector<E> event_move = new Vector<E>();
	private TS3Connector<?> ts3connector;
	private String lastActionString = "";
	private MYSQLConnector mysqlconnector;
	
	/**
	 * An ts3 server instance
	 * @param SID
	 * @param BOT_NAME
	 * @param CHANNEL
	 * @param features
	 */
	public Instance(int SID, String BOT_NAME, int CHANNEL, HashMap<String, Boolean> features){
		this.SID = SID;
		this.BOT_NAME = BOT_NAME;
		this.CHANNEL = CHANNEL;
		this.enabled_features = features;
		boolean connected = false;
		retry = Config.getBoolValue("CONNECTIONS_RETRY");
		do {
			ts3connector = getTS3Connector(this);
			connected = ts3connector != null;
		} while(!connected && retry);
		createFeatures();
	}
	
	public void shutdown(){
		logger.entry();
		for(E e : mods){
			e.handleShutdown();
		}
		ts3connector.disconnect();
		mysqlconnector.disconnect();
	}
	
	/**
	 * Create new connector
	 * @return null on failure
	 */
	private <U extends TeamspeakActionListener> TS3Connector<U> getTS3Connector(U i){
		try {
			return new TS3Connector<U>(i, SID, Config.getStrValue("TS3_IP"), Config.getIntValue("TS3_PORT"), Config.getStrValue("TS3_USER"), Config.getStrValue("TS3_PASSWORD"),BOT_NAME,CHANNEL);
		} catch (TS3ServerQueryException e) {
			logger.warn("{}",e);
			return null;
		}
	}
	
	/**
	 * Mod Loader<br>
	 * Loads all Mods & registers events according to the mod specifications<br>
	 * only registers events which are required by at least one mod
	 * Finally tells all mods that they can start their init process
	 */
	private void createFeatures(){
		boolean serverEvent = false;
		boolean channelEvent = false;
		boolean textChannel = false;
		boolean textPrivate = false;
		boolean textServer = false;
		boolean mysql_required = false;
		for(String fnName : enabled_features.keySet()){
			if(enabled_features.get(fnName)){
				try {
					Class<?> newFunction = Class.forName("Aron.Heinecke.ts3Manager.Mods." + fnName);
					if(ModEvent.class.isAssignableFrom(newFunction) && TS3Event.class.isAssignableFrom(newFunction)){
						@SuppressWarnings("unchecked")
						E obj = (E) newFunction.getDeclaredConstructor(Instance.class).newInstance(this);
						if(obj == null){
							logger.fatal("object ist null!");
						}
						mods.add(obj);
						if(obj.needs_Event_Server() || obj.needs_Event_Channel()){
							event_joined.add(obj);
							event_left.add(obj);
						}
						
						if(obj.needs_Event_TextChannel() || obj.needs_Event_TextPrivate() || obj.needs_Event_TextServer())
							event_chat.add(obj);
						
						if(obj.needs_Event_TextChannel())
							textChannel = true;
						if(obj.needs_Event_TextPrivate())
							textPrivate = true;
						if(obj.needs_Event_TextServer())
							textServer = true;
						if(obj.needs_Event_Server())
							serverEvent = true;
						if(obj.needs_Event_Channel())
							channelEvent = true;
						if(obj.needs_MYSQL())
							mysql_required = true;
						logger.info("Instance {} loaded {}",SID,fnName);
					}else{
						logger.fatal("Class not representing a mod! {}",fnName);
					}
				} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
					logger.fatal("Error during class loading: {}",e);
				}
			}
		}
		
		ts3connector.registerEvents(serverEvent, channelEvent, textServer, textChannel, textPrivate);
		
		if(mysql_required)
			mysqlconnector = new MYSQLConnector();
		
		for(E i: mods){
			i.handleReady();
		}
	}

	@Override
	public void teamspeakActionPerformed(String eventType, HashMap<String, String> eventInfo) {
		if (eventType.equals("notifytextmessage")) {
			for(E i : event_chat){
				i.handleTextMessage(eventType, eventInfo);
			}
		} else {
			if ((eventType + eventInfo.toString()).equals(this.lastActionString)) { // double event firing bug
				return;
			}
			if(eventType.equals("notifyclientleftview")){
//				logger.info("running client left {}",event_joined.size());
				for(E i : event_left){
					i.handleClientLeft(eventInfo);
				}
			}else if(eventType.equals("notifycliententerview")){
//				logger.info("running client joined {}",event_joined.size());
				for(E i : event_joined){
					i.handleClientJoined(eventInfo);
				}
			}else if(eventType.equals("notifyclientmoved")){
				for(E i : event_move){
					i.handleClientMoved(eventInfo);
				}
			}else{
				logger.info("Unknown event: {}",eventType);
			}
			lastActionString = eventType + eventInfo.toString();
		}
		
		//logger.debug("EVENT TYPE {} Instance: {}\n{}",eventType,SID,eventInfo);
	}
	
	public int getSID() {
		return SID;
	}

	public String getBOT_NAME() {
		return BOT_NAME;
	}
	
	public TS3Connector<?> getTS3Connection(){
		return ts3connector;
	}
	
	public void setConnectionRetry(boolean retry){
		this.retry = retry;
	}
	
	public MYSQLConnector getMysqlconnector() {
		return mysqlconnector;
	}
}
