package Aron.Heinecke.ts3Manager.Mods;

import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.API.ModEvent;
import Aron.Heinecke.ts3Manager.Lib.API.TS3Event;

public class ModStats implements ModEvent, TS3Event {
	Logger logger;
	private Instance<?> instance;
	
	public ModStats(Instance<?> instance){
		logger = LogManager.getLogger();
		this.instance = instance;
		logger.debug("Instance: {}",this.instance.getSID());
	}

	@Override
	public void handleClientJoined(String eventType, HashMap<String, String> eventInfo) {
		logger.debug("Client joined, {}",eventInfo);
	}

	@Override
	public void handleClientLeft(String eventType, HashMap<String, String> eventInfo) {
		logger.debug("Client left, {}",eventInfo);
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
	}

	@Override
	public void handleClientMoved(String eventType, HashMap<String, String> eventInfo) {
	}
	
	@Override
	public void handleTextMessage(String eventType, HashMap<String, String> eventInfo) {
	}
}
