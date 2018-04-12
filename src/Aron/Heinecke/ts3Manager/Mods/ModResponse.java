package Aron.Heinecke.ts3Manager.Mods;

import java.util.HashMap;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.API.Mod;
import Aron.Heinecke.ts3Manager.Lib.API.ModRegisters;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;

/**
 * Response mod, just for fun
 * @author Aron Heinecke
 *
 */
public class ModResponse implements Mod {
	
	private Logger logger = LogManager.getLogger();
	private Instance instance;
	private static final String CMD_INFO = "Yeah, I'm here watching you.";
	private static final String CMD_INFO2 = "Don't disturb me while I'm working.";
	private static final String[] textAnnoyed = {"Not again..","No.","I will ignore you!","Sssh",
			"Get a hobby.","Go do some CWs","Ignoring you","LALALALALALALA CAN'T HEAR YOU"};
	private final long COOLDOWN = 15000;
	private long lastTxtTime = 0;
	private int textAmount = 0;
	
	public ModResponse (Instance instance) {
		this.instance = instance;
	}

	@Override
	public ModRegisters registerEvents() {
		return new ModRegisters.Builder()
				.eventTextChannel(true)
				.build();
	}

	@Override
	public void handleReady() {
	}

	@Override
	public void handleShutdown() {
	}

	@Override
	public void handleClientJoined(HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleClientLeft(HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleTextMessage(String eventType, HashMap<String, String> eventInfo) {
		String[] args = eventInfo.get("msg").split(" ");
		if(args[0].equals("!bot")) {
			if(System.currentTimeMillis()-lastTxtTime > COOLDOWN) {
				lastTxtTime = System.currentTimeMillis();
				textAmount = 0;
				sendChannelMessage(CMD_INFO);
				sendChannelMessage(CMD_INFO2);
			} else if (textAmount < textAnnoyed.length){
				sendChannelMessage(textAnnoyed[textAmount]);
				textAmount++;
			}
			lastTxtTime = System.currentTimeMillis();
		}
	}
	
	/**
	 * Send text to channel, ignoring errors
	 * @param message
	 */
	private void sendChannelMessage(final String message) {
		try {
			instance.getTS3Connection().getConnector().sendTextMessage(instance.getChannel(),
					JTS3ServerQuery.TEXTMESSAGE_TARGET_CHANNEL, message);
		} catch (TS3ServerQueryException e) {
			logger.error(e);
		}
	}

	@Override
	public void handleClientMoved(HashMap<String, String> eventInfo) {
	}

}
