/**************************************************************************
 * Modular bot for teamspeak 3 (c)
 * Copyright (C) 2015-2018 Aron Heinecke
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
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * Swapping Buffer providing nearly non blocking List usage for reading & deleting old contents<br>
 * The idea is like two+ persons, one writing on paper and another person taking all the written paper
 * to use it for further processing. Essentially you can give the writer a new paper every time you
 * want to read everything that was written until now, both sides don't block each other or have concurrent access.
 * <br><br>
 * The add functions are thread safe, meaning you can write from multiple threads at once.
 * @author Aron Heinecke
 * @param <U> Type of elements to write
 *
 */
public class SBuffer<U> {
	private final ReentrantReadWriteLock lock;
	private final List<U>[] buffer;
	private int channel = 0;
	private final int MAX_CHANNEL;
	
	/**
	 * Initializes a non blocking buffer
	 * @param bufferAmount size of pool, a size >= 2 recommended<br>
	 * This size depends on how your swap/rw ratio & timing is
	 * @throws IllegalArgumentException on size < 2
	 */
	@SuppressWarnings("unchecked")
	public SBuffer(int bufferAmount){
		if(bufferAmount < 2) {
			throw new IllegalArgumentException("Buffer pool size < 2 is not allowed!");
		}
		lock = new ReentrantReadWriteLock();
		this.MAX_CHANNEL = bufferAmount-1;
		buffer = new LinkedList[bufferAmount];
		for(int i = 0; i <= MAX_CHANNEL; i++){
			buffer[i] = new LinkedList<>();
		}
	}
	
	/**
	 * Initializes a non blocking buffer with a buffer amount of 2.
	 */
	public SBuffer() {
		this(2);
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
	public List<U> getChannel(int channel){
		return new ArrayList<U>(buffer[channel]);
	}
	
	/**
	 * Returns the actual internal channel.
	 * Testing method, unsafe, non copying<br>
	 * Use at your own risk.
	 * @param channel
	 * @return
	 */
	public List<U> getChannelUnsafe(int channel) {
		return buffer[channel];
	}
	
	/**
	 * Swap to next channel, has to be called to gain the Data inserted till now<br>
	 * <b>Note:</b> blocks until all current add/get calls are finished.
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
	 * Add multiple elements<br>
	 * Thread safe function
	 * @param data
	 */
	public synchronized void add(Collection<U> data){
		ReadLock rl = lock.readLock();
		rl.lock();
		buffer[channel].addAll(data);
		rl.unlock();
	}
	
	/**
	 * Add element<br>
	 * Thread safe function
	 * @param in
	 */
	public synchronized void add(U in){
		ReadLock rl = lock.readLock();
		rl.lock();
		try {
			buffer[channel].add(in);
		} finally {
			rl.unlock();
		}
	}
	
	/**
	 * Get element in current channel at
	 * @param i
	 * @return
	 */
	public U get(int i){
		ReadLock rl = lock.readLock();
		rl.lock();
		try {
			return buffer[channel].get(i);
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
			buffer[getLastChanID()].clear();
		} finally {
			rl.unlock();
		}
	}
	
	/**
	 * Return a copy of the channel in use before last swap call
	 * @return
	 */
	public List<U> getLastChannel(){
		ReadLock rl = lock.readLock();
		rl.lock();
		try {
			return buffer[getLastChanID()];
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
			return buffer[getLastChanID()].size();
		} finally {
			rl.unlock();
		}
	}
	
	/**
	 * Returns the ID of the channel before the last swap
	 * @return
	 */
	public int getLastChannelID() {
		ReadLock rl = lock.readLock();
		rl.lock();
		try {
			return getLastChanID();
		} finally {
			rl.unlock();
		}
	}
	
	/**
	 * return last channel id before swap
	 * @return
	 */
	private int getLastChanID(){
		if(channel == 0)
			return MAX_CHANNEL;
		else
			return channel-1;
	}
}


