package Aron.Heinecke.ts3Manager.Lib;

/**
 * ServerIdentifier holding the connection data for a server
 * @author Aron Heinecke
 *
 */
public class ServerIdentifier {
	public final int ID;
	public final boolean isSID;
	public final String IP;
	public final int queryPort;
	
	/**
	 * Creates a new ServerIdentifier using a port
	 * @param port
	 * @param IP server IP
	 * @param queryPort server query port
	 */
	public ServerIdentifier (final int port, final String IP, final int queryPort) {
		this(port,false,IP,queryPort);
	}
	
	/**
	 * Creates a new ServerIdentifier
	 * @param ID port/sid
	 * @param isSID true for SID, false for port
	 * @param IP server IP
	 * @param queryPort server query port
	 */
	public ServerIdentifier(final int ID, final boolean isSID, final String IP,
			final int queryPort) {
		this.ID = ID;
		this.isSID = isSID;
		this.IP = IP;
		this.queryPort = queryPort;
	}
}
