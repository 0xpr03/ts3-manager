/**************************************************************************
 * Modular bot for teamspeak 3 (c)
 * Copyright (C) 2015-2016 Aron Heinecke
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *************************************************************************/
package Aron.Heinecke.ts3Manager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.yaml.snakeyaml.scanner.ScannerException;

import Aron.Heinecke.ts3Manager.Lib.ConfigLib;

/**
 * TS3 Manager Main class
 * @author Aron Heinecke
 */
public class TS3Manager {
	private static Logger logger = LogManager.getLogger();
	private static String VERSION = "0.3.3";
	private static List<Instance> instances = new ArrayList<Instance>();
	
	/**
	 * Mainc lass<br>
	 * Loading config (exit on error)
	 * Loading instances (ignores errorous instances )
	 * @param args
	 */
	public static void main(String[] args){
		checkLoggingConf();
		logger.info("Starting up TS3-Manager version {}",VERSION);
		
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
	
	public static void removeInstance(Instance instance){
		instances.remove(instance);
	}
	
	private static void registerExitFunction(){
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				for(Instance i : instances){
					i.shutdown();
				}
			}
		});
	}
	
	/**
	 * Checks if a new config is existing, to overwrite the internal logging conf<br>
	 * Otherwise trys to write the internal config into an external, editable file
	 */
	private static void checkLoggingConf() {
		java.io.File f = new java.io.File( ClassLoader.getSystemClassLoader().getResource(".").getPath()+"/log.xml");
		if (f.exists() && f.isFile()){
			if (Configurator.initialize(null, f.getAbsolutePath()) == null) {
				logger.error("Faulty log config: {}",f.getAbsolutePath());
				System.err.println("Faulty log config {}"+f.getAbsolutePath());
			}else{
				logger.debug("Using external log configuration");
			}
		}else{
			try{
				final String encoding = "UTF-8";
				f.createNewFile();
				Writer writer = new BufferedWriter(new OutputStreamWriter(
			              new FileOutputStream(f.getAbsolutePath()), encoding));
				BufferedReader reader = new BufferedReader(new InputStreamReader(Class.class.getResourceAsStream("/log4j2.xml"), encoding));
			    String line = null;
			    while ((line = reader.readLine()) != null) {
			        writer.write(line);
			        writer.write("\n");
			    }
			    writer.flush();
			    writer.close();
			    reader.close();
			    logger.info("Created editable logging config in {}",f.getAbsolutePath());
			} catch (IOException x) {
				logger.error("Writing logging config {}",x);
			    System.err.format("IOException: %s%n", x);
			}
		}
	}
}
