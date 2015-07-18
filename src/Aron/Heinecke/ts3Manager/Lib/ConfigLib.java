package Aron.Heinecke.ts3Manager.Lib;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.scanner.ScannerException;

import Aron.Heinecke.ts3Manager.Config;
import Aron.Heinecke.ts3Manager.Instance;

/**
 * ConfigLib for TS3Manager
 * @author Aron Heinecke
 */
public class ConfigLib {
	private Logger logger = LogManager.getLogger();
	private HashMap<String, Object> config;
	private String DEFAULT_PATH = "/Aron/Heinecke/ts3Manager/files/config.yml";
	private String CONFIG_FILE = "config.yml";
	Yaml yaml = new Yaml();
	private File FILE = new File(System.getProperty("user.dir")+"/"+CONFIG_FILE);
	
	public void writeTestMockup() throws IOException{
		config = new HashMap<String,Object>();
		HashMap<String, Boolean> features = new HashMap<String, Boolean>();
		for(int i = 0; i <= 100; i++){
			features.put("feature_"+i, true);
		}
		config.put("key_0", features);
		for(int i = 1; i <= 100; i++){
			config.put("key_"+i, "asd");
		}
		FileWriter writer = new FileWriter(FILE);
		yaml.dump(config, writer);
		writer.close();
	}
	
	/**
	 * Load the configuration, writing the instances<br>
	 * breaks on loading/parsing failure<br>
	 * <br>
	 * int TS3_IP<br>
	 * String TS3_USER<br>
	 * String TS3_PORT<br>
	 * String TS3_PASSWORD<br>
	 * String MYSQL_PORT<br>
	 * String MYSQL_USER<br>
	 * String MYSQL_PASSWORD<br>
	 * String MYSQL_IP<br>
	 * boolean CONNECTIONS_RETRY<br>
	 * @throws IOException, NullPointerException
	 */
	@SuppressWarnings("unchecked")
	public void loadConfig() throws IOException, NullPointerException, ScannerException{
		logger.entry();
		logger.debug("config: {}",FILE.getAbsolutePath());
		FileReader reader = new FileReader(FILE);
		config = (HashMap<String, Object>) yaml.load(reader);
		reader.close();
		
		Config.setValue("MYSQL_PORT", config.get("MYSQL_PORT"));
		Config.setValue("MYSQL_USER", config.get("MYSQL_USER"));
		Config.setValue("MYSQL_PASSWORD", config.get("MYSQL_PASSWORD"));
		Config.setValue("MYSQL_IP", config.get("MYSQL_IP"));
		
		Config.setValue("TS3_IP", config.get("TS3_IP"));
		Config.setValue("TS3_USER", config.get("TS3_USER"));
		Config.setValue("TS3_PORT", config.get("TS3_PORT"));
		Config.setValue("TS3_PASSWORD", config.get("TS3_PASSWORD"));
		
		Config.setValue("CONNECTIONS_RETRY", config.get("CONNECTIONS_RETRY"));
		
		logger.exit();
	}
	
	/**
	 * Load all available instances & create instance objects
	 * X.instance_enabled = true
	 * X.instance_ts3_name = bot
	 * X.instance_ts3_ID = 123
	 * X.instance_ts3_channel = 123
	 * X.instance_features:
	 * @param config
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<Instance> loadInstances(){
		List<Instance> list = new ArrayList<Instance>();
		int i = 1;
		while(config.containsKey(i+".instance_enabled")){
			try{
				int ts3_id = (int) config.get(i+".instance_ts3_ID");
				String name = (String) config.get(i+".instance_ts3_name");
				int channel = (int) config.get(i+".instance_ts3_channel");
				HashMap<String,Boolean> features = (HashMap<String, Boolean>) config.get(i+".instance_features");
				list.add(new Instance(ts3_id,name,channel,features));
			}catch( Exception e){
				//TODO: differentiate between errors
				logger.error("Error on instance loading {}",e);
			}
			i++;
		}
		logger.info("Loaded {} instances.",i-1);
		return list;
	}
	
	/**
	 * Writes the default config to the external file
	 */
	public void writeDefaults(){
		logger.entry();
		try {
			InputStream in = getClass().getResourceAsStream(DEFAULT_PATH);
			
			OutputStream out = new FileOutputStream(FILE);
			byte[] buffer = new byte[1024];
			int len = in.read(buffer);
			while (len != -1) {
			    out.write(buffer, 0, len);
			    len = in.read(buffer);
			}
			out.flush();
			out.close();
			
			in.close();
		}catch(FileNotFoundException | NullPointerException e){
			logger.fatal("Interal error!", e);
		}catch(IOException e){
			logger.fatal("Interal error!", e);
		}
		logger.exit();
	}
}
