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
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.TS3Connector;
import Aron.Heinecke.ts3Manager.Lib.API.Mod;
import Aron.Heinecke.ts3Manager.Lib.API.ModRegisters;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;

public class ModTools implements Mod {
	private static final String CLIENT_DB_KEY = "client_database_id";
	private static final String CLIENT_ID_KEY = "clid";
	// private static final String CLIENT_UID_KEY = "client_unique_identifier";
	private Instance instance;
	private Logger logger = LogManager.getLogger();
	private String CMD_HELP = "TS3Manager - ToolsMod\n!tools rocket [client_id] [ignore]\nuse [ignore] to rocket through used channels.";
	private AtomicBoolean rocketRunning = new AtomicBoolean(false);
	private AtomicInteger clientID = new AtomicInteger(-1);
	private AtomicInteger dbID = new AtomicInteger(-1);

	private final String PERM_MSG = "Not enough permissions to perform this action !";

	private boolean vetoActive = false;

	public ModTools(Instance instance) {
		this.instance = instance;
	}

	/**
	 * User rocket function, moving a client through all available channels upwards
	 * 
	 * @param target
	 *            target session ID
	 * @param applicant
	 *            applicant session ID
	 * @param ignore_clients
	 *            set to true to move also through channels with clients
	 */
	private void rocketchan(AtomicInteger target, AtomicInteger dbID, AtomicBoolean running, int applicant,
			boolean ignore_clients) {
		if (!running.compareAndSet(false, true)) {
			try {
				this.instance.getTS3Connection().getConnector().sendTextMessage(applicant,
						JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, "[b]Already in use![/b]");
			} catch (TS3ServerQueryException e) {
				logger.error(e);
			}
			return;
		}
		TS3Connector<?> ts3conn = instance.getNewTS3Connector(null, "Rocket", -1);
		try {
			if (vetoActive) {
				target.set(applicant);
			}
			HashMap<String, String> tmap = ts3conn.getConnector().getInfo(JTS3ServerQuery.INFOMODE_CLIENTINFO,
					target.get()); // target
			// infos
			if (tmap.get(CLIENT_DB_KEY) == "") {
				throw new NumberFormatException("missing " + CLIENT_DB_KEY);
			}
			dbID.set(Integer.valueOf(tmap.get(CLIENT_DB_KEY)));
			List<Integer> channels = new ArrayList<Integer>();
			Vector<HashMap<String, String>> rawChannelList = ts3conn.getConnector()
					.getList(JTS3ServerQuery.LISTMODE_CHANNELLIST);
			for (HashMap<String, String> channel : rawChannelList) {
				if (channel.get("total_clients").equals("0") || ignore_clients) {
					channels.add(Integer.valueOf(channel.get("cid")));
				}
			}
			logger.debug("Channels used for rocket:\n{}", channels);
			int failed = 0;
			for (int x = channels.size() - 1; x >= 0;) {
				int sleep = 40;
				try {
					if (target.get() != -1) {
						ts3conn.getConnector().moveClient(target.get(), channels.get(x), "");
						ts3conn.getConnector().pokeClient(target.get(), "move");
						x--;
					} else {
						sleep = 500;
					}
				} catch (Exception e) {
					logger.warn("rocket move/poke error {} {}", e.getMessage(), target.get());
					sleep = 400;
					failed++;
					if (failed > (channels.size() / 2)) {
						logger.debug("rocket break, too many retries");
						break;
					}
				}
				try {
					Thread.sleep(sleep);
				} catch (InterruptedException e) {
					logger.warn(e);
				}
			}
			ts3conn.getConnector().kickClient(target.get(), false, "You were rocketed!");
			if (!vetoActive)
				ts3conn.getConnector().sendTextMessage(applicant, JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT,
						"[b]Client " + tmap.get("client_nickname") + "[" + tmap.get(CLIENT_DB_KEY)
								+ "] was rocketed![/b]");
		} catch (NumberFormatException e) {
			try {
				ts3conn.getConnector().sendTextMessage(applicant, JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT,
						"[b]Client " + target + " wasn't found![/b] Please specify a valid (local)id.");
			} catch (TS3ServerQueryException e1) {
				logger.fatal(e1);
			}
		} catch (Exception e) {
			logger.error("Error on rocket \n{}", e);
		} finally {
			if (ts3conn != null)
				ts3conn.disconnect();
			running.set(false);
			vetoActive = false;
		}
	}

	/**
	 * Thread running the rocket bot locally
	 * 
	 * @author aron
	 *
	 */
	private class InternRocket extends Thread {
		int sID;
		boolean ignore_users;
		AtomicInteger clientID;
		AtomicBoolean runningBoolean;
		AtomicInteger clientDBID;

		public InternRocket(int sID, boolean ignore_users, AtomicInteger clientID, AtomicBoolean runningBoolean,
				AtomicInteger clientDBID) {
			super();
			this.sID = sID;
			this.ignore_users = ignore_users;
			this.clientID = clientID;
			this.runningBoolean = runningBoolean;
			this.clientDBID = clientDBID;
		}

		@Override
		public void run() {
			rocketchan(clientID, clientDBID, runningBoolean, sID, ignore_users);
		}
	}

	@Override
	public void handleTextMessage(String eventType, HashMap<String, String> eventInfo) {
		String[] args = eventInfo.get("msg").split(" ");
		final int sID = Integer.parseInt(eventInfo.get("invokerid"));
		try {
			if (args[0].equals("!tools")) {
				boolean permMissing = false;
				boolean isAdmin = hasAdminPerms(sID);
				boolean misCMD = false;
				switch (args[1]) {
				case "rocket":
					if (isAdmin) {
						if (args.length >= 3) {
							final boolean ignore_users = args.length == 4 ? args[3].equals("ignore") : false;
							clientID.getAndSet(Integer.valueOf(args[2]));
							new InternRocket(sID, ignore_users, clientID, rocketRunning, dbID).start();
							logger.debug("gone");
						} else {
							misCMD = true;
						}
					} else {
						permMissing = true;
					}
					break;
				case "veto":
					logger.debug("Got veto cmd.");
					if (!rocketRunning.get()) {
						vetoActive = true;
						instance.getTS3Connection().getConnector().sendTextMessage(sID,
								JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, "Accepted.");
					}
					break;
				case "help":
				default:
					misCMD = true;
					logger.warn("unknown command");
				}
				if (misCMD) {
					instance.getTS3Connection().getConnector().sendTextMessage(sID,
							JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, CMD_HELP);
				}
				if (permMissing) {
					instance.getTS3Connection().getConnector().sendTextMessage(sID,
							JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, PERM_MSG);
				}
			}
		} catch (TS3ServerQueryException e) {
			logger.error("Error on cmd handling: \n", e);
		}
	}

	private boolean hasAdminPerms(int clientID) throws TS3ServerQueryException {
		return instance.hasGroup(clientID, instance.ADMIN_GROUP, instance.getTS3Connection().getConnector());
	}

	@Override
	public void handleClientJoined(HashMap<String, String> eventInfo) {
		// if(rocketRunning.get() && Integer.valueOf(eventInfo.get(CLIENT_DB_KEY)) ==
		// dbID.get()) {
//		logger.debug("{} {}", eventInfo.get(CLIENT_ID_KEY));
		if (rocketRunning.get()) {
			if (Integer.valueOf(eventInfo.get(CLIENT_DB_KEY)) == dbID.get()) {
				try {
					instance.getTS3Connection().getConnector().kickClient(clientID.get(), false, "Fool.");
				} catch (TS3ServerQueryException e) {
					logger.error("{}", e);
				}

				clientID.set(Integer.valueOf(eventInfo.get(CLIENT_ID_KEY)));
				logger.info("detected anti rocket move {}",clientID.get());
			}
		} else {
//			logger.debug("rocket not running");
		}
	}

	@Override
	public void handleClientLeft(HashMap<String, String> eventInfo) {
//		logger.debug(eventInfo.get(CLIENT_ID_KEY));
		if (rocketRunning.get() && !eventInfo.get("reasonid").equals("5")
				&& Integer.valueOf(eventInfo.get(CLIENT_ID_KEY)) == clientID.get()) { // 8 = kick, 5 = own leave
			clientID.set(-1); // set invalid -> pause rocket
			logger.debug("detected early leave");
		}
	}

	@Override
	public void handleClientMoved(HashMap<String, String> eventInfo) {
	}

	@Override
	public ModRegisters registerEvents() {
		return new ModRegisters.Builder().eventTextChannel(true).eventTextPrivate(true).eventTextServer(true)
				.eventServer(true).build();
	}

	@Override
	public void handleReady() {

	}

	@Override
	public void handleShutdown() {

	}

}
