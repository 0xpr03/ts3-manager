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
package Aron.Heinecke.ts3Manager.Lib.API;

import java.util.HashMap;

/**
 * Interface for ts3 events<br>
 * Only activated if so specified in the mod library
 * @author Aron Heinecke
 */
public interface TS3Event {
	public abstract void handleClientJoined(HashMap<String, String> eventInfo);
	public abstract void handleClientLeft(HashMap<String, String> eventInfo);
	public abstract void handleTextMessage(String eventType, HashMap<String, String> eventInfo);
	public abstract void handleClientMoved(HashMap<String, String> eventInfo);
}
