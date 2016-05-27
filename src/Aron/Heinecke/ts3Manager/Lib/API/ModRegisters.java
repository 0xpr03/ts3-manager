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
package Aron.Heinecke.ts3Manager.Lib.API;

/**
 * BuilderPattern for mod event settings<br>
 * @author Aron Heinecke
 */
public class ModRegisters {
	private boolean eventChannel;
	private boolean eventServer;
	private boolean eventTextChannel;
	private boolean eventTextPrivate;
	private boolean eventTextServer;
	
	/**
	 * Builder for ModRegisters, default setting for all events is off.
	 * @author Aron Heinecke
	 *
	 */
	public static class Builder {
		private boolean eventChannel = false;
		private boolean eventServer = false;
		private boolean eventTextChannel = false;
		private boolean eventTextPrivate = false;
		private boolean eventTextServer = false;

		public Builder eventChannel(boolean eventChannel) {
			this.eventChannel = eventChannel;
			return this;
		}

		public Builder eventServer(boolean eventServer) {
			this.eventServer = eventServer;
			return this;
		}

		public Builder eventTextChannel(boolean eventTextChannel) {
			this.eventTextChannel = eventTextChannel;
			return this;
		}

		public Builder eventTextPrivate(boolean eventTextPrivate) {
			this.eventTextPrivate = eventTextPrivate;
			return this;
		}

		public Builder eventTextServer(boolean eventTextServer) {
			this.eventTextServer = eventTextServer;
			return this;
		}

		public ModRegisters build() {
			return new ModRegisters(this);
		}
	}

	private ModRegisters(Builder builder) {
		eventChannel = builder.eventChannel;
		eventServer = builder.eventServer;
		eventTextChannel = builder.eventTextChannel;
		eventTextPrivate = builder.eventTextPrivate;
		eventTextServer = builder.eventTextServer;
	}

	/**
	 * @return the eventChannel
	 */
	public synchronized boolean isEventChannel() {
		return eventChannel;
	}

	/**
	 * @return the eventServer
	 */
	public synchronized boolean isEventServer() {
		return eventServer;
	}

	/**
	 * @return the eventTextChannel
	 */
	public synchronized boolean isEventTextChannel() {
		return eventTextChannel;
	}

	/**
	 * @return the eventTextPrivate
	 */
	public synchronized boolean isEventTextPrivate() {
		return eventTextPrivate;
	}

	/**
	 * @return the eventTextServer
	 */
	public synchronized boolean isEventTextServer() {
		return eventTextServer;
	}
}
