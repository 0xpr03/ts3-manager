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
package Aron.Heinecke.ts3Manager.Lib;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.Statement;
import java.util.TimeZone;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Config;

/**
 * MariaDB/MYSQL Connector pool<br>
 * Reconnects if necessary.
 * @author Aron Heinecke
 */
public class MYSQLConnector {
	private Logger logger = LogManager.getLogger();
	private Connection connection;
	private PostConnectHook hook = null;
	
	private final int MS_CONNECTION_CHECK_TIMEOUT = 100;
	
	/**
	 * Creates a new MYSQL (mariaDB) connector<br>
	 * Connector should be closed at the end via disconnect()
	 */
	public MYSQLConnector(){
		this(null);
	}
	/**
	 * Creates a new MYSQL (mariaDB) connector<br>
	 * Connector should be closed at the end via disconnect()
	 * @param hook Post connection hook to execute
	 */
	public MYSQLConnector(PostConnectHook hook) {
		this.hook = hook;
		connect();
	}
	
	/**
	 * Connector function
	 */
	private void connect(){
		String base = "jdbc:mariadb://";
		
		base = base+Config.getStrValue("MYSQL_IP")+":"+Config.getIntValue("MYSQL_PORT");
		base += "/"+Config.getStrValue("MYSQL_DB");
		base +="?tcpKeepAlive=true";
		base +="&serverTimezone=";
		base +=TimeZone.getDefault().getID();
		boolean success = false;
		try{
			connection = DriverManager.getConnection(base, Config.getStrValue("MYSQL_USER"), Config.getStrValue("MYSQL_PASSWORD"));
			if(hook != null) {
				try {
					hook.connectHook(connection);
				} catch (Exception e) {
					logger.warn("Connection Hook\n{}",e);
				}
			}
			success = true;
		}catch(SQLNonTransientConnectionException e){
			logger.error("No connection to DB! {}",e);
		}catch(SQLException e){
			logger.error("DBError {}",e);
		}finally{
			if(!success && connection != null){
				try{connection.close();}catch(SQLException e){}
			}
		}
	}
	
	/**
	 * Disconnect current connection.<br>
	 * Please close all running transactions before!
	 */
	public void disconnect(){
		try {
			if(!connection.isClosed())
			connection.close();
		} catch (SQLException e) {
			logger.error("Error on mysql connection closing {}",e);
		}
	}
	
	/**
	 * Basic statement preparer
	 * @param sql
	 * @return PreparedStatment, unbound
	 * @throws SQLException to be handled with DBExceptionConverter at best
	 * @throws ConnectionException if the connection is dead and wasn't able to recover
	 * @author "Aron Heinecke"
	 */
	public PreparedStatement prepareStm(String sql) throws SQLException{
		if(checkConnection())
			return connection.prepareStatement(sql);
		else
			throw new ConnectionException();
	}
	
	/**
	 * Check if the connection is alive
	 * Retry to connect otherwise
	 * @return false if the connection couldn't be re-established
	 */
	private boolean checkConnection(){
		try{
			if(connection.isValid(MS_CONNECTION_CHECK_TIMEOUT)){
				return true;
			}else{
				logger.warn("DB connection broken!");
				try{
					connection.close();
				}catch(Exception e){}
				connect();
				return connection.isValid(MS_CONNECTION_CHECK_TIMEOUT);
			}
		}catch(Exception e){
			logger.error("db connection check {}",e);
			return false;
		}
	}
	
	/**
	 * Execute an update query.
	 * Not injection safe!
	 * @param sql single-sql
	 * @return affected lines
	 * @throws SQLException 
	 * @author "Aron Heinecke"
	 */
	public int execUpdateQuery(String sql) throws SQLException{
		if(!checkConnection()){
			throw new ConnectionException();
		}
		int affectedLines = -1;
		Statement stm = null;
		try{
			stm = connection.createStatement();
			stm.executeUpdate(sql);
			affectedLines = stm.getUpdateCount();
		}finally{
			if(stm != null){
				try{stm.close();}catch(Exception e){}
			}
		}
		
		return affectedLines;
	}
	
	/**
	 * Post connect hook
	 * @author Aron Heinecke
	 *
	 */
	public interface PostConnectHook{
		/**
		 * Called after successfull connection
		 */
		public void connectHook(Connection connection);
	}
	
	/**
	 * get the actual connection
	 * @return Connection
	 * @throws ConnectionException if the connection couldn't be re-established
	 */
	public Connection getConnector() throws ConnectionException{
		if(checkConnection())
			return connection;
		else
			throw new ConnectionException();
	}
	
	public class ConnectionException extends SQLException{
		private static final long serialVersionUID = 7303912993162712101L;
		public ConnectionException(){
			super("Connection dead exception");
		}
	}
}
