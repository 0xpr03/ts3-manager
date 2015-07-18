package Aron.Heinecke.ts3Manager.Lib.API;

import java.util.HashMap;

/**
 * Interface for ts3 events<br>
 * Only activated if so specified in the mod library
 * @author Aron Heinecke
 */
public interface TS3Event {
	public abstract void handleClientJoined(String eventType, HashMap<String, String> eventInfo);
	public abstract void handleClientLeft(String eventType, HashMap<String, String> eventInfo);
	public abstract void handleTextMessage(String eventType, HashMap<String, String> eventInfo);
	public abstract void handleClientMoved(String eventType, HashMap<String, String> eventInfo);
}
