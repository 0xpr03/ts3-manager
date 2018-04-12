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
package Aron.Heinecke.ts3Manager.Lib.API;

import java.util.HashMap;

/**
 * Required interface for all mods
 * @author Aron Heinecke
 */
public interface Mod {
	/**
	 * Called on initialization, registers for events<br>
	 * Should not block
	 * @return
	 */
	public abstract ModRegisters registerEvents();
	/**
	 * Called after initialization
	 */
	public abstract void handleReady();
	/**
	 * Called on shutdown<br>
	 * Logger is undefined & will probably crash at this point!
	 */
	public abstract void handleShutdown();
	public abstract void handleClientJoined(HashMap<String, String> eventInfo);
	public abstract void handleClientLeft(HashMap<String, String> eventInfo);
	public abstract void handleTextMessage(String eventType, HashMap<String, String> eventInfo);
	public abstract void handleClientMoved(HashMap<String, String> eventInfo);
	/**
	 * Fired after connection re-establish<br>
	 * Not fired on initial connect.
	 */
	public default void handleReconnect() {};
	/**
	 * Fired on connection loss
	 */
	public default void handleConnectionLoss() {};
}
