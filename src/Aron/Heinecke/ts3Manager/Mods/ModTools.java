/**************************************************************************
 * Modular bot for teamspeak 3 (c)
 * Copyright (C) 2015-2016 Aron Heinecke
 * 
 * 
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 * See main class TS3Manager.java for the full version.
 *************************************************************************/
package Aron.Heinecke.ts3Manager.Mods;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.TS3Connector;
import Aron.Heinecke.ts3Manager.Lib.API.Mod;
import Aron.Heinecke.ts3Manager.Lib.API.ModRegisters;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;

public class ModTools implements Mod {
	private Instance instance;
	private Logger logger = LogManager.getLogger();
	private String CMD_HELP = "TS3Manager - ToolsMod\n!tools rocket [client_id] [ignore]\nuse [ignore] to rocket through used channels.";
	private AtomicBoolean rocketRunning = new AtomicBoolean(false);
	
	private final String PERM_MSG = "Not enough permissions to perform this action !";
	
	private boolean vetoActive = false;
	
	public ModTools(Instance instance){
		this.instance = instance;
	}
	
	/**
	 * User rocket function, moving a client through all available channels upwards
	 * @param target target session ID
	 * @param applicant applicant session ID
	 * @param ignore_clients set to true to move also through channels with clients
	 */
	private void rocketchan(String target, int applicant, boolean ignore_clients) {
		if(!rocketRunning.compareAndSet(false, true)) {
			try {
				this.instance.getTS3Connection().getConnector().sendTextMessage(applicant, JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, "[b]Already in use![/b]");
			} catch (TS3ServerQueryException e) {
				logger.error(e);
			}
			return;
		}
		TS3Connector<?> ts3conn = instance.getNewTS3Connector(null,"Rocket",-1);
		try {
			int tid = Integer.parseInt(target); // get targetid
			if(vetoActive) {
				tid = applicant;
				vetoActive = false;
			}
			HashMap<String, String> tmap = ts3conn.getConnector().getInfo(JTS3ServerQuery.INFOMODE_CLIENTINFO, tid); // target infos
			if ( tmap.get("client_database_id") == null ) {
				throw new NumberFormatException();
			} else if ( tmap.get("client_database_id") == "" ) {
				throw new NumberFormatException();
			}
			List<Integer> channels = new ArrayList<Integer>();
			Vector<HashMap<String, String>> rawChannelList = ts3conn.getConnector().getList(JTS3ServerQuery.LISTMODE_CHANNELLIST);
			for ( HashMap<String, String> channel : rawChannelList ) {
				if ( channel.get("total_clients").equals("0") || ignore_clients ) {
					channels.add(Integer.valueOf(channel.get("cid")));
				}
			}
			logger.debug("Channels used for rocket:\n{}",channels);
			for(int x = channels.size() -1; x >= 0; x--){
				ts3conn.getConnector().moveClient(tid, channels.get(x), "");
				ts3conn.getConnector().pokeClient(tid, "move");
				try {
					Thread.sleep(40);
				} catch ( InterruptedException e ) {
					logger.warn(e);
				}
			}
			ts3conn.getConnector().kickClient(tid, false, "You were rocketed!");
			if(tid != applicant)
				ts3conn.getConnector().sendTextMessage(applicant, JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, "[b]Client " + tmap.get("client_nickname")+ "[" + tmap.get("client_database_id") + "] was rocketed![/b]");
		} catch ( NumberFormatException e ) {
			try {
				ts3conn.getConnector().sendTextMessage(applicant, JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, "[b]Client " + target + " wasn't found![/b] Please specify a valid (local)id.");
			} catch (TS3ServerQueryException e1) {
				logger.fatal(e1);
			}
		} catch (TS3ServerQueryException e) {
			logger.error("Error on rocket \n{}",e);
		}finally{
			if(ts3conn != null)
				ts3conn.disconnect();
			rocketRunning.set(false);
		}
	}

	@Override
	public void handleTextMessage(String eventType, HashMap<String, String> eventInfo) {
		String[] args = eventInfo.get("msg").split(" ");
		int sID = Integer.parseInt(eventInfo.get("invokerid"));
		try {
		if(args[0].equals("!tools")){
			if ( hasAdminPerms(sID) ) {
				boolean misCMD = false;
				switch(args[1]){
				case "rocket":
					if(args.length >= 3) {
						boolean ignore_users = false;
						if (args.length == 4)
								ignore_users = args[3].equals("ignore");
						rocketchan(args[2], sID, ignore_users);
					}else{
						misCMD = true;
					}
					break;
				case "veto":
					logger.debug("Got veto cmd.");
					if(!rocketRunning.get()) {
						vetoActive = true;
						instance.getTS3Connection().getConnector().sendTextMessage(sID, JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, "Accepted.");
					}
					break;
				case "help":
				default:
					misCMD = true;
					logger.warn("unknown command");
				}
				if(misCMD){
					instance.getTS3Connection().getConnector().sendTextMessage(sID, JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, CMD_HELP);
				}
			} else {
				instance.getTS3Connection().getConnector().sendTextMessage(sID, JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, PERM_MSG);
			}
		}
		} catch (TS3ServerQueryException e){
			logger.error("Error on cmd handling: \n",e);
		}
	}
	
	private boolean hasAdminPerms(int clientID) throws TS3ServerQueryException{
		return instance.hasGroup(clientID, instance.ADMIN_GROUP, instance.getTS3Connection().getConnector());
	}
	
	@Override
	public void handleClientJoined(HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleClientLeft(HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleClientMoved(HashMap<String, String> eventInfo) {
	}

	@Override
	public ModRegisters registerEvents(){
		return new ModRegisters.Builder()
				.eventTextChannel(true)
				.eventTextPrivate(true)
				.eventTextServer(true)
				.build();
	}

	@Override
	public void handleReady() {
		
	}

	@Override
	public void handleShutdown() {
		
	}

}
