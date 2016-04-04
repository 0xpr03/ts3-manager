/**************************************************************************
 * Modular bot for teamspeak 3 (c)
 * Copyright (C) 2015 Aron Heinecke
 * 
 * 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 * See main class TS3Manager.java for the full version.
 *************************************************************************/
package Aron.Heinecke.ts3Manager;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Lib.TS3Connector;
import Aron.Heinecke.ts3Manager.Lib.API.ModEvent;
import Aron.Heinecke.ts3Manager.Lib.API.TS3Event;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
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
	public int ADMIN_GROUP;
	private HashMap<String, Boolean> enabled_features;
	private Vector<E> mods = new Vector<E>();
	private Vector<E> event_joined = new Vector<E>();
	private Vector<E> event_left = new Vector<E>();
	private Vector<E> event_chat = new Vector<E>();
	private Vector<E> event_move = new Vector<E>();
	private TS3Connector<?> ts3connector;
	private String lastActionString = "";
	
	/**
	 * An ts3 server instance
	 * @param SID
	 * @param BOT_NAME
	 * @param CHANNEL
	 * @param features
	 */
	public Instance(final int SID, String BOT_NAME, int CHANNEL, HashMap<String, Boolean> features, int admin_group){
		this.SID = SID;
		this.BOT_NAME = BOT_NAME;
		this.CHANNEL = CHANNEL;
		this.enabled_features = features;
		this.ADMIN_GROUP = admin_group;
		retry = Config.getBoolValue("CONNECTIONS_RETRY");
		Thread t = new Thread(){
			public void run(){
				boolean connected = false;
				do {
					ts3connector = getNewTS3Connector(getInstance());
					connected = ts3connector != null;
				} while(!connected && retry);
				if(connected)
					createFeatures();
				else{
					logger.error("Failed to connect to SID {}, disabling instance.", SID);
				}
			}
		};
		t.start();
	}
	
	private Instance<E> getInstance(){
		return this;
	}
	
	public void shutdown(){
		logger.entry();
		for(E e : mods){
			e.handleShutdown();
		}
		ts3connector.disconnect();
	}
	
	/**
	 * Returns a new ts3 connection
	 * @param i action receiver class
	 * @param bot_name
	 * @param channel_id
	 * @return null on failure
	 */
	public <U extends TeamspeakActionListener> TS3Connector<U> getNewTS3Connector(U i, String bot_name, int channel_id){
		try {
			return new TS3Connector<U>(i, SID, Config.getStrValue("TS3_IP"), Config.getIntValue("TS3_PORT"), Config.getStrValue("TS3_USER"), Config.getStrValue("TS3_PASSWORD"),bot_name, channel_id);
		} catch (TS3ServerQueryException e) {
			logger.warn("{} for SID {}",e, SID);
			return null;
		}
	}
	
	/**
	 * Returns a new ts3 connection with config name & channel settings
	 * @param i
	 * @return null on failure
	 */
	private <U extends TeamspeakActionListener> TS3Connector<U> getNewTS3Connector(U i){
		return getNewTS3Connector(i,BOT_NAME,CHANNEL );
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
		
		for(E i: mods){
			i.handleReady();
		}
	}

	@Override
	public void teamspeakActionPerformed(String eventType, HashMap<String, String> eventInfo) {
		if (eventType.equals("notifytextmessage")) {
			if ( Integer.parseInt(eventInfo.get("invokerid")) == ts3connector.getConnector().getCurrentQueryClientID() ) {
				return;
			}
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
	
	/**
	 * Test if client has group
	 * @param cid client id
	 * @param group_id group id
	 * @param query query to use for request
	 * @return true if client has group
	 * @throws TS3ServerQueryException 
	 */
	public boolean hasGroup(int cid, int group_id,JTS3ServerQuery query ) throws TS3ServerQueryException {
		return isGroupListed(query.getInfo(13, cid).get("client_servergroups"),group_id);
	}
	
	private boolean isGroupListed(String groupIDs, int searchGroupID) {
		StringTokenizer groupTokenizer = new StringTokenizer(groupIDs, ",", false);
		int groupID;

		while (groupTokenizer.hasMoreTokens()) {
			groupID = Integer.parseInt(groupTokenizer.nextToken());
			if ( groupID == searchGroupID ) {
				return true;
			}
		}
		return false;
	}
	
	public int getSID() {
		return SID;
	}

	public String getBOT_NAME() {
		return BOT_NAME;
	}
	
	/**
	 * Returns the ts3connection of this instance<br>
	 * Do not use this connection for heavy tasks as it's interrupting the event loop<br>
	 * Please use <code>getNewTS3Connector</code> instead for those jobs
	 * @return
	 */
	public TS3Connector<?> getTS3Connection(){
		return ts3connector;
	}
	
	public void setConnectionRetry(boolean retry){
		this.retry = retry;
	}
}
