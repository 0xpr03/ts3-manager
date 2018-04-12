/**************************************************************************
 * Modular bot for teamspeak 3 (c)
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
package Aron.Heinecke.ts3Manager;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.StringTokenizer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Lib.ConfigLib;
import Aron.Heinecke.ts3Manager.Lib.ServerIdentifier;
import Aron.Heinecke.ts3Manager.Lib.TS3ConnectionException;
import Aron.Heinecke.ts3Manager.Lib.TS3Connector;
import Aron.Heinecke.ts3Manager.Lib.API.Mod;
import Aron.Heinecke.ts3Manager.Lib.API.ModRegisters;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;
import de.stefan1200.jts3serverquery.TeamspeakActionListener;

/**
 * Bot Instance, represents one server
 * @author Aron Heinecke
 * @param <E>
 */
public class Instance implements TeamspeakActionListener {
	private Logger logger = LogManager.getLogger();
	private ServerIdentifier SI;
	private boolean retry;
	private String BOT_NAME;
	private int CHANNEL;
	public int ADMIN_GROUP;
	private HashMap<String, Boolean> enabled_features;
	private ArrayList<Mod> mods = new ArrayList<>();
	private ArrayList<Mod> event_joined = new ArrayList<>();
	private ArrayList<Mod> event_left = new ArrayList<>();
	private ArrayList<Mod> event_chat = new ArrayList<>();
	private ArrayList<Mod> event_move = new ArrayList<>();
	private TS3Connector<?> ts3connector;
	
	/**
	 * An ts3 server instance
	 * @param PSID port/server id of this instance
	 * @param isSID use pSID as SID on true or false for port
	 * @param BOT_NAME
	 * @param CHANNEL
	 * @param features
	 * @param admin_group
	 */
	public Instance(final ServerIdentifier SI, String BOT_NAME, int CHANNEL, HashMap<String, Boolean> features, int admin_group){
		this.SI = SI;
		this.BOT_NAME = BOT_NAME;
		this.CHANNEL = CHANNEL;
		this.enabled_features = features;
		this.ADMIN_GROUP = admin_group;
		retry = Config.getBoolValue("CONNECTIONS_RETRY");
		final Instance selfRef = this;
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
					logger.error("Failed to connect to SID {}, disabling instance.", SI.ID);
					TS3Manager.removeInstance(selfRef);
				}
			}
		};
		t.start();
	}
	
	private Instance getInstance(){
		return this;
	}
	
	public void shutdown(){
		logger.entry();
		for(Mod mod : mods){
			try {
				mod.handleShutdown();
			} catch (Exception e) {
				System.err.println(e);
			}
		}
		if(ts3connector != null)
			ts3connector.disconnect();
		
	}
	
	/**
	 * Returns a new ts3 connection
	 * @param i action receiver class
	 * @param bot_name
	 * @param channel_id can be -1 for no move
	 * @return null on failure
	 */
	public <U extends TeamspeakActionListener> TS3Connector<U> getNewTS3Connector(U i, String bot_name, int channel_id){
		try {
			return new TS3Connector<U>(i, SI,
					Config.getStrValue(ConfigLib.TS3_USER),
					Config.getStrValue(ConfigLib.TS3_PASSWORD),
					bot_name, channel_id);
		} catch (TS3ConnectionException e) {
			logger.warn("{} for SID {}",e, SI.ID);
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
					Class<?> newMod = Class.forName("Aron.Heinecke.ts3Manager.Mods." + fnName);
					if(Mod.class.isAssignableFrom(newMod)){
						Mod mod = (Mod) newMod.getDeclaredConstructor(Instance.class).newInstance(this);
						if(mod == null){
							logger.fatal("object ist null!");
						}
						mods.add(mod);
						ModRegisters modSettings = mod.registerEvents();
						if(modSettings.isEventServer() || modSettings.isEventChannel()){
							event_joined.add(mod);
							event_left.add(mod);
						}
						
						if(modSettings.isEventTextChannel() || modSettings.isEventTextPrivate() || modSettings.isEventTextServer())
							event_chat.add(mod);
						
						if(modSettings.isEventTextChannel())
							textChannel = true;
						if(modSettings.isEventTextPrivate())
							textPrivate = true;
						if(modSettings.isEventTextServer())
							textServer = true;
						if(modSettings.isEventServer())
							serverEvent = true;
						if(modSettings.isEventChannel())
							channelEvent = true;
						logger.info("Instance {} loaded {}",SI.ID,fnName);
					}else{
						logger.fatal("Class not representing a mod! {}",fnName);
					}
				} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
					logger.fatal("Error during class loading: {}",e);
				}
			}
		}
		
		ts3connector.registerEvents(serverEvent, channelEvent, textServer, textChannel, textPrivate);
		
		for(Mod i: mods){
			i.handleReady();
		}
	}

	@Override
	public void teamspeakActionPerformed(String eventType, HashMap<String, String> eventInfo) {
		if (eventType.equals("notifytextmessage")) {
			if ( Integer.parseInt(eventInfo.get("invokerid")) == ts3connector.getConnector().getCurrentQueryClientID() ) {
				return; // own action
			}
			for(Mod i : event_chat){
				i.handleTextMessage(eventType, eventInfo);
			}
		} else {
			switch(eventType){
			case "notifyclientleftview":
				for(Mod i : event_left){
					i.handleClientLeft(eventInfo);
				}
				break;
			case "notifycliententerview":
				for(Mod i : event_joined){
					i.handleClientJoined(eventInfo);
				}
				break;
			case "notifyclientmoved":
				for(Mod i : event_move){
					i.handleClientMoved(eventInfo);
				}
				break;
			case "notifyserveredited":
			case "notifytokenused":
			case "notifychannelpasswordchanged":
			case "notifychannelmoved":
			case "notifychanneldeleted":
			case "notifychannelcreated":
			case "notifychanneldescriptionchanged":
			case "notifychanneledited":
				break;
			default:
				logger.info("Unknown event {}",eventType);
			}
		}
		logger.exit();
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
	
	/**
	 * Returns the servers identifier port or ID
	 * @return ID / port
	 */
	public int getID() {
		return SI.ID;
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
	
	/**
	 * Returns the bot channel
	 * @return
	 */
	public int getChannel() {
		return CHANNEL;
	}
	
	@Override
	public void handleConnectionLoss() {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				for(Mod i : mods){
					try {
						i.handleConnectionLoss();
					} catch(Exception e) {
						logger.error("Catched mod exception on connection loss event!\n{}",e);
					}
				}
			}
		});
		t.start();
	}

	@Override
	public void handleReconnect() {
		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				for(Mod i : mods){
					try {
						i.handleReconnect();
					} catch(Exception e) {
						logger.error("Catched mod exception on reconnect event!\n{}",e);
					}
				}
			}
		});
		t.start();
	}
}
