package Aron.Heinecke.ts3Manager.Mods;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.TS3Connector;
import Aron.Heinecke.ts3Manager.Lib.API.ModEvent;
import Aron.Heinecke.ts3Manager.Lib.API.TS3Event;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;

public class ModTools implements ModEvent, TS3Event {
	private Instance instance;
	public ModTools(Instance<?> instance){
		this.instance = instance;
	}
	
	/**
	 * User rocket function, moving a client through all available channels upwards
	 * @param args
	 * @param ap
	 * @return
	 */
	private boolean rocketchan(String target, int ap) {
		try {
			TS3Connector<?> ts3conn = instance.getTS3Connection();
			int tid = Integer.parseInt(args[1]); // get targetid
			HashMap<String, String> tmap = ts3conn.getConnector().getInfo(JTS3ServerQuery.INFOMODE_CLIENTINFO, tid); // target infos
			if ( tmap.get("client_database_id") == null ) {
				throw new NumberFormatException();
			} else if ( tmap.get("client_database_id") == "" ) {
				throw new NumberFormatException();
			}
			List<Integer> channels = new ArrayList<Integer>();
			Vector<HashMap<String, String>> a = query.getList(2);
			for ( HashMap<String, String> b : a ) {
				channels.add(Integer.valueOf(b.get("cid")));
			}
			int i = channels.size() - 1;
			while (i >= 0) {
				ts3conn.getConnector().moveClient(tid, channels.get(i), "");
				i--;
				try {
					Thread.sleep(60);
				} catch ( InterruptedException e ) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			ts3conn.getConnector().kickClient(tid, false, "You where rocketed!");
			ts3conn.getConnector().sendTextMessage(ap, JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, "[b]Client " + tmap.get("client_nickname")+ "[" + tmap.get("client_database_id") + "] wurde rocketed![/b]");
			return true;
		} catch ( NumberFormatException e ) {
			query.sendTextMessage(ap, JTS3ServerQuery.TEXTMESSAGE_TARGET_CLIENT, "[b]Client " + args[1] + " nicht gefunden![/b]");
			return false;
		}
	}

	@Override
	public void handleTextMessage(String eventType, HashMap<String, String> eventInfo) {
		String[] args = eventInfo.get("msg");
		if(args[0].equals("!tools")){
			boolean misCMD = false;
			switch(args[1]){
			case "rocket":
				if(args.length == 3)
					
				else
					misCMD = true;
				break;
			case "help":
				//TODO: send help
			default:
				logger.warn("unknown cmd");
			}
			if(misCMD){
				//TODO: send help
			}
		}
	}
	
	@Override
	public void handleClientJoined(HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleClientLeft(HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleClientMoved(HashMap<String, String> eventInfo) {
	}

	@Override
	public boolean needs_Event_Channel() {
		return false;
	}

	@Override
	public boolean needs_Event_Server() {
		return false;
	}

	@Override
	public boolean needs_Event_TextChannel() {
		return true;
	}

	@Override
	public boolean needs_Event_TextPrivate() {
		return true;
	}

	@Override
	public boolean needs_Event_TextServer() {
		return false;
	}

	@Override
	public void handleReady() {
		
	}

	@Override
	public void handleShutdown() {
		
	}

}
