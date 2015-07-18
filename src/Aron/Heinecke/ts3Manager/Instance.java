package Aron.Heinecke.ts3Manager;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
	private Vector<E> mods;
	private TS3Connector<?> ts3connector;
	
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
		do {
			ts3connector = getTS3Connector(this);
			connected = ts3connector == null ? true : false;
		} while(!connected && retry);
		createFeatures();
	}
	
	/**
	 * Create new connector
	 * @return null on failure
	 */
	private <U extends TeamspeakActionListener> TS3Connector<U> getTS3Connector(U i){
		try {
			return new TS3Connector<U>(i, SID, Config.getStrValue(""), Config.getIntValue(""), Config.getStrValue(""), Config.getStrValue(""),BOT_NAME,CHANNEL);
		} catch (TS3ServerQueryException e) {
			return null;
		}
	}
	
	private void createFeatures(){
		for(String fnName : enabled_features.keySet()){
			if(enabled_features.get(fnName)){
				try {
					Class<?> newFunction = Class.forName("de.stefan1200.jts3servermod.functions." + fnName);
					if(ModEvent.class.isAssignableFrom(newFunction) && TS3Event.class.isAssignableFrom(newFunction)){
						@SuppressWarnings("unchecked")
						E obj = (E) newFunction.getDeclaredConstructor(Instance.class).newInstance(this);
						mods.add(obj);
					}else{
						logger.fatal("Class not representing a mod! {}",fnName);
					}
				} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
					logger.fatal("Error during class loading: {}",e);
				}
			}
		}
	}

	@Override
	public void teamspeakActionPerformed(String eventType, HashMap<String, String> eventInfo) {
		logger.debug("{}",eventInfo);
	}
	
	public void setConnectionRetry(boolean retry){
		this.retry = retry;
	}
}
