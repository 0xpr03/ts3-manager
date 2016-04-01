/**************************************************************************
 * Modular bot for teamspeak 3 (c)
 * Copyright (C) 2015 Aron Heinecke
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

import java.util.Vector;

/**
 * Swapping Buffer providing non blocking vector usage for reading & deleting old contents
 * @author Aron Heinecke
 * @param <U>
 *
 */
public class SBuffer<U> {
	private Vector<Vector<U>> buffer;
	private int channel = 0;
	private int MAX_CHANNEL;
	
	/**
	 * Initalizes a non blocking buffer
	 * @param size size of pool, size >= 2 recommended
	 */
	public SBuffer(int size){
		this.MAX_CHANNEL = size-1;
		buffer = new Vector<Vector<U>>(size);
		for(int i = 0; i <= MAX_CHANNEL; i++){
			buffer.addElement(new Vector<U>());
		}
	}
	
	public int getCurrentChannelID(){
		return channel;
	}
	
	/**
	 * Returns a copy of the specified channel
	 * @param channel
	 * @return
	 */
	public Vector<U> getChannel(int channel){
		return new Vector<U>(buffer.get(channel));
	}
	
	/**
	 * Swap to next channel, has to be called to gain the Data inserted till now
	 */
	public void swap(){
		if(channel == MAX_CHANNEL)
			channel = 0;
		else
			channel++;
	}
	
	/**
	 * Add multiple elements (synchronized through definition of Vector)
	 * @param data
	 */
	public void add(Vector<U> data){
		buffer.get(channel).addAll(data);
	}
	
	/**
	 * Add element (synchronized through definition of Vector)
	 * @param in
	 */
	public void add(U in){
		buffer.get(channel).add(in);
	}
	
	/**
	 * Get element at
	 * @param i
	 * @return
	 */
	public U get(int i){
		return buffer.get(channel).get(i);
	}
	
	/**
	 * Clear old channel
	 */
	public synchronized void clearOldChannel(){
		buffer.get(getLastChanID()).clear();
	}
	
	/**
	 * Return a copy of the channel in use before last swap call
	 * @return
	 */
	public Vector<U> getLastChannel(){
		return new Vector<U>(buffer.get(getLastChanID()));
	}
	
	/**
	 * Return size of last channel before swap
	 * @return
	 */
	public int getLastChannelSize(){
		return buffer.get(getLastChanID()).size();
	}
	
	/**
	 * return last channel id before swap
	 * @return
	 */
	private int getLastChanID(){
		if(channel == MAX_CHANNEL)
			return 0;
		else
			return channel+1;
	}
}


