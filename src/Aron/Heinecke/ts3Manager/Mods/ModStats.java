package Aron.Heinecke.ts3Manager.Mods;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.API.ModEvent;
import Aron.Heinecke.ts3Manager.Lib.API.TS3Event;

public class ModStats implements ModEvent, TS3Event {
	Logger logger;
	private long last_update = 0L;
	private boolean blocked = false;
	private Instance<?> instance;
	private Timer timer = new Timer(false);
	private TimerTask timerdosnapshot;
	
	public ModStats(Instance<?> instance){
		logger = LogManager.getLogger();
		this.instance = instance;
		logger.debug("Instance: {}",this.instance.getSID());
		timerdosnapshot = new TimerTask() {
			@Override
			public void run() {
				updateClients();
			}
		};
	}
	
	private synchronized void updateClients(){
		if(System.currentTimeMillis() - last_update > 1000){
			// update
			blocked = false;
		}else{
			blocked = true;
			timer.schedule(timerdosnapshot, 1000);
		}
	}

	@Override
	public void handleClientJoined(HashMap<String, String> eventInfo) {
		logger.debug("Client joined, ");
	}

	@Override
	public void handleClientLeft(HashMap<String, String> eventInfo) {
		logger.debug("Client left, ");
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
	public void handleClientMoved(HashMap<String, String> eventInfo) {
	}
	
	@Override
	public void handleTextMessage(String eventType, HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleShutdown() {
		logger.entry();
		blocked = true;
		timerdosnapshot.cancel();
		timer.cancel();
	}
}
