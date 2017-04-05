package Aron.Heinecke.ts3Manager.Lib;

/**
 * Generic error for connection problems to the ts3 server
 * @author Aron Heinecke
 */
public class TS3ConnectionException extends Exception {
	private static final long serialVersionUID = 7303912993162712101L;
	public TS3ConnectionException(){
		super("TS3 Connection exception!");
	}
}
