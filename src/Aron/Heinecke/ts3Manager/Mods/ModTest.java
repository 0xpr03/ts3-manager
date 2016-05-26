package Aron.Heinecke.ts3Manager.Mods;

import java.util.HashMap;
import java.util.Vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Instance;
import Aron.Heinecke.ts3Manager.Lib.TS3Connector;
import Aron.Heinecke.ts3Manager.Lib.API.Mod;
import Aron.Heinecke.ts3Manager.Lib.API.ModRegisters;

/**
 * ModTest includes various tests<br>
 * Commands:<br>
 * test DC : massive leave/join spam<br>
 * @author Aron Heinecke
 *
 */
public class ModTest implements Mod {
	private Logger logger = LogManager.getLogger();
	private Instance<?> instance;
	private boolean RUN = false;
	private int BASE_MS = 50;
	private int AMOUNT_THREADS = 30;
	private Vector<Thread> threads = new Vector<>();
	
	/**
	 * ModTest with various test bots
	 * @param instance
	 */
	public ModTest(Instance<?> instance){
		this.instance = instance;
	}

	@Override
	public ModRegisters registerEvents() {
		return new ModRegisters.Builder().eventTextChannel(true).build();
	}

	@Override
	public void handleReady() {
	}

	@Override
	public void handleShutdown() {
		stopDCThreads();
	}
	
	private void stopDCThreads(){
		RUN = false;
		for(Thread t : threads){
			try {
				if(t.isAlive())
					t.join(100+BASE_MS+AMOUNT_THREADS);
			} catch (InterruptedException e) {
				logger.error("Waiting for thread: {}",e);
			}
		}
	}
	
	private void startDCThreads(){
		for(int i = 0; i < AMOUNT_THREADS; i++){
			Runnable r = new DCThread("thread_"+i,BASE_MS+i);
			Thread t = new Thread(r);
			t.setDaemon(false);
			threads.addElement(t);
		}
		logger.info("Starting connect/disconnect spam tests");
		RUN = true;
		for(Thread t: threads){
			t.start();
		}
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
		if(args[0].equals("test")){
			if(args[1].equals("DC")){
				if(!RUN)
					startDCThreads();
				else
					stopDCThreads();
			}else if(args[1].equals("stop")){
				System.exit(0);
			}
		}
	}

	@Override
	public void handleClientMoved(HashMap<String, String> eventInfo) {
	}
	
	public class DCThread implements Runnable {
		private String name;
		private int MS;

		public DCThread(String name, int MS) {
			this.name = name;
			this.MS = MS;
		}

		public void run() {
			try{
				TS3Connector<?> conn;
				while(RUN){
					conn = instance.getNewTS3Connector(null, name, -1);
					try {
						Thread.sleep(MS);
					} catch (InterruptedException e) {
						logger.error(e);
					}
					conn.disconnect();
				}
			}catch(Exception e){
				logger.warn("Test Thread error: {}",e);
			}
		}
	}
	
}
