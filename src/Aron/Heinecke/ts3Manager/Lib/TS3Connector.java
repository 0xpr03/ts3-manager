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
package Aron.Heinecke.ts3Manager.Lib;

import java.util.Timer;
import java.util.TimerTask;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Config;
import Aron.Heinecke.ts3Manager.Instance;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;
import de.stefan1200.jts3serverquery.TeamspeakActionListener;

/**
 * TS3Connector abstract layer over JTS3ServerQuery<br>
 * Handles connection timeouts and errors. Ads reconnect event.
 * @author Aron Heinecke
 * @param <U>
 */
public class TS3Connector<U extends TeamspeakActionListener> {
	Logger logger = LogManager.getLogger();
	JTS3ServerQuery query;
	private ServerIdentifier SI;
	private String user;
	private String password;
	private String name;
	private int channel;
	private U listener;
	private Timer timer = null;
	private TimerTask timertask = null;
	
	private boolean serverEvent = false;
	private boolean channelEvent = false;
	private boolean textChannel = false;
	private boolean textPrivate = false;
	private boolean textServer = false;
	
	/**
	 * New TS3Connector
	 * @param listener
	 * @param SI
	 * @param user
	 * @param password
	 * @param name
	 * @param channel
	 * @throws TS3ConnectionException if the initial connect fails
	 */
	public TS3Connector(U listener, ServerIdentifier SI,String user, String password, String name,int channel) throws TS3ConnectionException{
		query = new JTS3ServerQuery();
		this.SI = SI;
		this.password = password;
		this.name = name;
		this.user = user;
		this.channel = channel;
		this.listener = listener;
		long lastErrMsg = System.currentTimeMillis();
		boolean showedFlood = false;
		do{
			try{
				connect();
				startTimer();
			}catch(Exception e){
				if(System.currentTimeMillis() - lastErrMsg > 1000){
					lastErrMsg = System.currentTimeMillis();
					if(e instanceof TS3ServerQueryException){
						TS3ServerQueryException e2 = (TS3ServerQueryException) e;
						logger.fatal("Error during connection establishment! Instance {}",SI.ID);
						if (e2.getFailedPermissionID() >= 0)
							logger.warn("Missing permissions! {} on ID {}",e2.getFailedPermissionID(),SI.ID);
						logger.warn("Suppressing error log flooting");
					}else{
						logger.fatal("Socket exception, server down ?\n{}",e);
					}
				}else if (!showedFlood){
					showedFlood = true;
					logger.warn("Suppressing log error flooding");
				}
			}
		}while(timer == null && Config.getBoolValue("CONNECTIONS_RETRY"));
		if(timer == null){
			throw new TS3ConnectionException();
		}
	}
	
	/**
	 * Connect is inside a new class for checkConnect
	 * @throws Exception 
	 */
	private void connect() throws Exception{
		query.connectTS3Query(SI.IP, SI.queryPort);
		query.loginTS3(user, password);
		if(SI.isSID)
			query.selectVirtualServer(SI.ID);
		else
			query.selectVirtualServer(SI.ID, true);
		query.setTeamspeakActionListener(listener);
		try{
			if(channel != -1)
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
	 * Registers events and stores the enabled events in case of a re-connect.
	 * @param server_event
	 * @param channel_event
	 * @param text_server_event
	 * @param text_channel_event
	 * @param text_private_event
	 * @return success
	 */
	public boolean registerEvents(boolean server_event, boolean channel_event, boolean text_server_event, boolean text_channel_event
			, boolean text_private_event){
		serverEvent = server_event;
		channelEvent = channel_event;
		textServer = text_server_event;
		textPrivate = text_private_event;
		textChannel = text_channel_event;
		return registerEvents();
	}
	
	/**
	 * Event register caller
	 * Uses stored internal booleans
	 * @return success
	 */
	private boolean registerEvents(){
		try {
			registerEvent(JTS3ServerQuery.EVENT_MODE_SERVER, serverEvent);
			registerEvent(JTS3ServerQuery.EVENT_MODE_CHANNEL, channelEvent);
			registerEvent(JTS3ServerQuery.EVENT_MODE_TEXTSERVER, textServer);
			registerEvent(JTS3ServerQuery.EVENT_MODE_TEXTCHANNEL, textChannel);
			registerEvent(JTS3ServerQuery.EVENT_MODE_TEXTPRIVATE, textPrivate);
			return true;
		} catch (TS3ServerQueryException e) {
			logger.error("Error registering events for PSID {}! {}",SI.ID,e);
			return false;
		}
	}
	
	/**
	 * Starts the timer for connection keep alive
	 */
	private void startTimer(){
		timer = new Timer(true);
		timertask = new TimerTask(){
			 public void run() {
		          checkConnect();
		     }
		};
		timer.schedule(timertask, 1*60*1000, 1*60*1000);
	}
	
	/**
	 * Stops timer for connection keep alive
	 */
	private void stopTimer(){
		if(timer == null){
			return;
		}
		timertask.cancel();
		timer.cancel();
	}
	
	/**
	 * Returns the connector & delays the connection keep alive
	 * @return
	 */
	public JTS3ServerQuery getConnector(){
		stopTimer();
		startTimer();
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
	 * Checks if the connnection is still alive & the server status is online<br>
	 * Will otherwise try to reconnect & register it's events
	 */
	private void checkConnect() {
		try {
			if ( query.isConnected() ) {
				// virtual server running: virtualserver_status=online
				if((query.doCommand("serverinfo").get("response").contains("virtualserver_status=online")))
						return;
			}
			logger.warn("Disconnect on ID {}! Reconnecting..",SI.ID);
			try {
				query.closeTS3Connection();
				connect();
				registerEvents();
				if(listener != null)
					listener.handleReconnect();
			} catch (TS3ServerQueryException e) {
				if(e.getErrorID() == 1033){ // #2
					logger.warn("Server instance ID {} is down! Can't connect.",SI.ID);
				}else{
					logger.error("Reconnect failed for ID {} \n{}", SI.ID,e.getMessage());
				}
				if(!Config.getBoolValue("CONNECTIONS_RETRY")){
					if(listener instanceof Instance){
						logger.info("No retry enabled, shutting down the instance.");
						((Instance)listener).shutdown();
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error during connection check\n{}",e);
			logger.info("ID {}, listener {}",SI.ID,listener != null ? "null" : listener.getClass().getName());
		}
	}
}
