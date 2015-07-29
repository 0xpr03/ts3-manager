package Aron.Heinecke.ts3Manager.Lib;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.Statement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import Aron.Heinecke.ts3Manager.Config;

/**
 * MariaDB/MYSQL Connector holding the connection, acting as pool
 * @author Aron Heinecke
 */
public class MYSQLConnector {
	private Logger logger = LogManager.getLogger();
	private Connection connection;
	
	public MYSQLConnector(){
		String base = "jdbc:mariadb://";
		
		base = base+Config.getStrValue("MYSQL_IP")+":"+Config.getIntValue("MYSQL_PORT");
		base += "/"+Config.getStrValue("MYSQL_DB");
		base +="?tcpKeepAlive=true";
		boolean success = false;
		try{
			connection = DriverManager.getConnection(base, Config.getStrValue("MYSQL_USER"), Config.getStrValue("MYSQL_PASSWORD"));
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
	 * @author "Aron Heinecke"
	 */
	public PreparedStatement prepareStm(String sql) throws SQLException{
		PreparedStatement stm = connection.prepareStatement(sql);
		return stm;
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
	 * get the actual connection
	 * @return Connection
	 */
	public Connection getConnector(){
		return connection;
	}
}
