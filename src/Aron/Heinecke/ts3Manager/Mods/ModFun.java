package Aron.Heinecke.ts3Manager.Mods;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.TS3Connector;
import Aron.Heinecke.ts3Manager.Lib.API.Mod;
import Aron.Heinecke.ts3Manager.Lib.API.ModRegisters;
import de.stefan1200.jts3serverquery.JTS3ServerQuery;
import de.stefan1200.jts3serverquery.TS3ServerQueryException;

/**
 * Experimental fun mod which randomly creates, deletes, moves and renames channels.<br>
 * While it's only modifying it's self created channels for this actions, it's not advised to run this on productive systems!
 * @author Aron Heinecke
 *
 */
public class ModFun implements Mod {
	
	private Timer timer;
	private TimerTask timerTick;
	private Logger logger = LogManager.getLogger();
	private Instance instance;
	private TS3Connector<?> ts3conn;
	private List<Integer> createdChannels = new ArrayList<>();
	
	private final static String CHAN_CREATE_CMD = "channelcreate channel_name=%name %p";
	private final static String CHAN_MOVE_CMD = "channelmove cid=%chanid cpid=%parent";
	
	public ModFun(Instance instance){
		this.instance = instance;
		timer = new Timer();
		timerTick = new TimerTask() {
			@Override
			public void run() {
				tick();
			}
		};
	}
	
	@Override
	public ModRegisters registerEvents() {
		return new ModRegisters.Builder().build();
	}

	@Override
	public void handleReady() {
		ts3conn = instance.getNewTS3Connector(null,"h4ck3r",-1);
		timer.schedule(timerTick, 200, 200000);
	}
	
	/**
	 * Tick method, run at specified interval
	 */
	private void tick(){
		try{
			if(getAmountClients() == 0){ // do nothing when noone is connected
				return;
			}
			if(ThreadLocalRandom.current().nextFloat() > 0.5f ){ // 50%
				if(ThreadLocalRandom.current().nextFloat() > 0.5f){ // 25%
					if(ThreadLocalRandom.current().nextFloat() > (createdChannels.size() < 10 ? 0.3f : 0.5f)){
						createChannel(); // 
					}else{
						deleteRandomChannel(); // 
					}
				}else{
					if(createdChannels.size() < 1)
						return;
					if(ThreadLocalRandom.current().nextFloat() > 0.5){ // 12,5%
						moveRandomChannel(); // 
					}else{
						renameRandomChannel(); // 
					}
				}
			}
		}catch(TS3ServerQueryException e){
			logger.error("tick: {}",e);
		}catch(Exception e){
			logger.fatal("{}",e);
		}
	}
	
	private int getAmountClients() throws TS3ServerQueryException{
		HashMap<String, String> res = ts3conn.getConnector().getInfo(JTS3ServerQuery.INFOMODE_SERVERINFO, 0);
		return Integer.valueOf(res.get("virtualserver_clientsonline")) - Integer.valueOf(res.get("virtualserver_queryclientsonline"));
	}
	
	private void renameRandomChannel(){
		
	}
	
	/**
	 * Move a random channel to a random location
	 * @throws TS3ServerQueryException
	 */
	private void moveRandomChannel() throws TS3ServerQueryException{
		int moveChannel = createdChannels.get(ThreadLocalRandom.current().nextInt(createdChannels.size()));
		
		List<Integer> channels = new ArrayList<Integer>();
		Vector<HashMap<String, String>> rawChannelList = ts3conn.getConnector().getList(JTS3ServerQuery.LISTMODE_CHANNELLIST);
		for ( HashMap<String, String> channel : rawChannelList ) {
			channels.add(Integer.valueOf(channel.get("cid")));
		}
		
		String cmd = CHAN_MOVE_CMD.replace("%chanid", ""+moveChannel)
				.replace("%parent", ""+channels.get(ThreadLocalRandom.current().nextInt(rawChannelList.size()-1)));
		HashMap<String,String> result = ts3conn.getConnector().doCommand(cmd);
		if(!result.get("msg").equals("ok")){
			logger.error("Error on move: {}",result);
		}
	}
	
	private String nameGenerator(int length){
		Random r = new Random();
		
	    final String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890";
	    StringBuilder sb = new StringBuilder();
	    sb.append("FH ");
	    for (int i = 0; i < length; i++) {
	       sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
	    }
	    return ts3conn.getConnector().encodeTS3String(sb.toString());
	}
	
	/**
	 * Create a channel, with 60% pos. to create a sub channel
	 * @throws TS3ServerQueryException
	 */
	private void createChannel() throws TS3ServerQueryException{
		String options = "channel_flag_permanent=1";
		if(ThreadLocalRandom.current().nextFloat() < 0.6){
			List<Integer> channels = new ArrayList<Integer>();
			Vector<HashMap<String, String>> rawChannelList = ts3conn.getConnector().getList(JTS3ServerQuery.LISTMODE_CHANNELLIST);
			for ( HashMap<String, String> channel : rawChannelList ) {
				channels.add(Integer.valueOf(channel.get("cid")));
			}
			options += (" cpid="+channels.get(ThreadLocalRandom.current().nextInt(channels.size()-1)));
		}
		
		String cmd = CHAN_CREATE_CMD.replace("%name", nameGenerator(4)).replace("%p", options);
		HashMap<String, String> result = ts3conn.getConnector().doCommand(cmd);
		if(result.get("msg").equals("ok")){
			String cid = result.get("response");
			cid = cid.substring(cid.indexOf("cid=")+4);
			createdChannels.add(Integer.valueOf(cid));
		}else{
			logger.error("Error on creation: {}",result);
		}
	}
	
	/**
	 * Delete random channel from created list
	 * @throws TS3ServerQueryException
	 */
	private void deleteRandomChannel() throws TS3ServerQueryException{
		if(createdChannels.size() == 0)
			return;
		int listID = ThreadLocalRandom.current().nextInt(createdChannels.size()-1);
		try{ // ignore invalid channel id, if a channel was already deleted
			ts3conn.getConnector().deleteChannel(createdChannels.get(listID), true);
		}catch(TS3ServerQueryException e){
			if(e.getErrorID() != 768)
				throw e;
			else
				logger.debug("Catched already deleted msg.");
			
		}
		createdChannels.remove(listID);
	}
	
	@Override
	public void handleShutdown() {
		if(timer != null)
			timer.cancel();
		if(timerTick != null)
			timerTick.cancel();
		try {
			ts3conn.getConnector().setDisplayName("room service");
		} catch (TS3ServerQueryException e1) {
			logger.error("{}",e1);
		}
		for(int chan : createdChannels){
			try {
				ts3conn.getConnector().deleteChannel(chan, true);
			} catch (TS3ServerQueryException e) {
				logger.warn("cleanup: {}",e);
			}
		}
		ts3conn.disconnect();
	}

	@Override
	public void handleClientJoined(HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleClientLeft(HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleTextMessage(String eventType, HashMap<String, String> eventInfo) {
	}

	@Override
	public void handleClientMoved(HashMap<String, String> eventInfo) {
	}

}
