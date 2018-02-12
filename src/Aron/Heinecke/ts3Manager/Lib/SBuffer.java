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

import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * Swapping Buffer providing nearly non blocking vector usage for reading & deleting old contents
 * @author Aron Heinecke
 * @param <U>
 *
 */
public class SBuffer<U> {
	private final ReentrantReadWriteLock lock;
	private final ArrayList<Vector<U>> buffer;
	private int channel = 0;
	private final int MAX_CHANNEL;
	
	/**
	 * Initalizes a non blocking buffer
	 * @param size size of pool, size >= 2 recommended
	 */
	public SBuffer(int size){
		lock = new ReentrantReadWriteLock();
		this.MAX_CHANNEL = size-1;
		buffer = new ArrayList<Vector<U>>(size);
		for(int i = 0; i <= MAX_CHANNEL; i++){
			buffer.add(new Vector<U>());
		}
	}
	
	/**
	 * Returns the current channel ID
	 * @return
	 */
	public int getCurrentChannelID(){
		ReadLock rl = lock.readLock();
		rl.lock();
		int tChan = channel;
		rl.unlock();
		return tChan;
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
		WriteLock wr = lock.writeLock();
		wr.lock();
		if(channel == MAX_CHANNEL)
			channel = 0;
		else
			channel++;
		wr.unlock();
	}
	
	/**
	 * Add multiple elements (synchronized through definition of Vector)
	 * @param data
	 */
	public void add(Vector<U> data){
		ReadLock rl = lock.readLock();
		rl.lock();
		buffer.get(channel).addAll(data);
		rl.unlock();
	}
	
	/**
	 * Add element (synchronized through definition of Vector)
	 * @param in
	 */
	public void add(U in){
		ReadLock rl = lock.readLock();
		rl.lock();
		try {
			buffer.get(channel).add(in);
		} finally {
			rl.unlock();
		}
	}
	
	/**
	 * Get element at
	 * @param i
	 * @return
	 */
	public U get(int i){
		ReadLock rl = lock.readLock();
		rl.lock();
		try {
			return buffer.get(channel).get(i);
		} finally {
			rl.unlock();
		}
	}
	
	/**
	 * Clear old channel
	 */
	public void clearOldChannel(){
		ReadLock rl = lock.readLock();
		rl.lock();
		try {
			buffer.get(getLastChanID()).clear();
		} finally {
			rl.unlock();
		}
	}
	
	/**
	 * Return a copy of the channel in use before last swap call
	 * @return
	 */
	public Vector<U> getLastChannel(){
		ReadLock rl = lock.readLock();
		rl.lock();
		try {
			return buffer.get(getLastChanID());
		} finally {
			rl.unlock();
		}
	}
	
	/**
	 * Return size of last channel before swap
	 * @return
	 */
	public int getLastChannelSize(){
		ReadLock rl = lock.readLock();
		rl.lock();
		try {
			return buffer.get(getLastChanID()).size();
		} finally {
			rl.unlock();
		}
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


