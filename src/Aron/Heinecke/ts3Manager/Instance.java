package Aron.Heinecke.ts3Manager;

import java.util.HashMap;

public class Instance {
	private int SID;
	private String BOT_NAME;
	private int CHANNEL;
	private HashMap<String, Boolean> features;
	
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
		this.features = features;
	}
}
