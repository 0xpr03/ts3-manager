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

/**
 * Event registering interface for mods<br>
 * Return true for events that are required
 * @author Aron Heinecke
 */
public interface ModEvent {
	public abstract boolean needs_Event_Channel();
	public abstract boolean needs_Event_Server();
	public abstract boolean needs_Event_TextChannel();
	public abstract boolean needs_Event_TextPrivate();
	public abstract boolean needs_Event_TextServer();
	/**
	 * Called when all required things are ready
	 */
	public abstract void handleReady();
	/**
	 * Caled on shutdown
	 */
	public abstract void handleShutdown();
}
