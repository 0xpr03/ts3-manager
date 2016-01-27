package Aron.Heinecke.ts3Manager;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.scanner.ScannerException;

import Aron.Heinecke.ts3Manager.Lib.ConfigLib;

/**
 * TS3 Manager Main class
 * @author Aron Heinecke
 */
public class TS3Manager {
	private static Logger logger = LogManager.getLogger();
	private static String VERSION = "0.2.3 beta";
	@SuppressWarnings("rawtypes")
	private static List<Instance> instances = new ArrayList<Instance>();
	
	/**
	 * Mainc lass<br>
	 * Loading config (exit on error)
	 * Loading instances (ignores errorous instances )
	 * @param args
	 */
	public static void main(String[] args){
		logger.info("Starting up version {}",VERSION);
		
		ConfigLib cfglib = new ConfigLib();
		boolean loaded = false;
		
		registerExitFunction();
		
		try {
			cfglib.loadConfig();
			instances = cfglib.loadInstances();
			loaded = true;
		} catch (ScannerException | NullPointerException e) {
			if(e instanceof ScannerException)
				logger.error("config syntax error at \n{}",((ScannerException) e).getProblemMark().get_snippet());
			else
				logger.error("Error parsing the config!",e);
		} catch (Exception e) {
			logger.error("Failure loading the config file! {}",e);
			cfglib.writeDefaults();
			logger.info("Wrote default config.");
		} finally {
			if(!loaded)
				System.exit(1);
		}

		while(true){
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				logger.info(e);
			}
		}
	}
	
	private static void registerExitFunction(){
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				for(Instance<?> i : instances){
					i.shutdown();
				}
			}
		});
	}
	
}
