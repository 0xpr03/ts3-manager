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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Simple tester for SBuffer
 * @author Aron Heinecke
 */
public class bufferTest {
	private static SBuffer<Integer> bufferThreadsTest;
	private boolean insertRunning = false;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		bufferThreadsTest = new SBuffer<Integer>(100);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}
	
	private int getChannelTestValue(final SBuffer<Integer> buffer, final int channel) {
		return buffer.getChannelUnsafe(channel).get(0);
	}
	
	private void addChannelTestValue(final SBuffer<Integer> buffer, final int channel) {
		buffer.getChannelUnsafe(channel).add(channel);
	}
	
	@Test
	public void testChannelSwapping() {
		int chnStartID = 0;
		int maxChnCount = 5;
		int maxChnID = maxChnCount-1;
		
		SBuffer<Integer> buffer = new SBuffer<>(maxChnCount);
		for(int i = 0; i < maxChnCount; i++) {
			addChannelTestValue(buffer, i);
		}
		
		for(int i = 0; i < maxChnCount; i++) {
			assertEquals(i,getChannelTestValue(buffer, i));
		}
		
		assertEquals(chnStartID,buffer.getCurrentChannelID());
		assertEquals(maxChnID,buffer.getLastChannelID());
		assertTrue(buffer.getChannelUnsafe(maxChnID) == buffer.getLastChannel());
		assertTrue(buffer.getChannelUnsafe(chnStartID) == buffer.getChannelUnsafe(buffer.getCurrentChannelID()));
		
		buffer.swap();
		
		assertEquals(chnStartID+1,buffer.getCurrentChannelID());
		assertEquals(chnStartID, buffer.getLastChannelID());
		assertTrue(buffer.getChannelUnsafe(chnStartID) == buffer.getLastChannel());
		assertTrue(buffer.getChannelUnsafe(chnStartID+1) == buffer.getChannelUnsafe(buffer.getCurrentChannelID()));
		assertFalse(buffer.getLastChannel() == buffer.getChannelUnsafe(buffer.getCurrentChannelID()));
	}
	
	@Test
	public void TestSwapping() {
		int chnAmount = 100;
		SBuffer<Integer> buffer = new SBuffer<>(chnAmount);
		
		assertEquals(0, buffer.getCurrentChannelID());
		
		for(int i = 0; i < chnAmount; i++) {
			buffer.add(i);
			buffer.swap();
			assertEquals(i, (int)buffer.getLastChannel().get(0));
		}
		
		assertEquals(chnAmount-1,buffer.getLastChannelID());
		
		for(int i = 0; i < chnAmount; i++) {
			assertEquals(i, (int)buffer.get(0));
			buffer.swap();
			assertEquals(i, (int)buffer.getLastChannel().get(0));
		}
	}

	@Test
	public void testThreadedAccess() throws InterruptedException {
		System.out.println("Starting..");
		final int max = 20000000;
		final int maxCount = max*3;
		final int[] toSend = new int[maxCount];
		
		for(int i : toSend) {
			assertTrue(i == 0);
		}
		long time = System.currentTimeMillis();
		Thread inserter = new Thread(){
			@Override
			public void run(){
				for(int i = 0; i < max; i++){
					bufferThreadsTest.add(i);
				}
			}
		};
		
		Thread inserter2 = new Thread(){
			@Override
			public void run(){
				for(int i = 0; i < max; i++){
					bufferThreadsTest.add(i+max);
				}
			}
		};
		
		Thread inserter3 = new Thread(){
			@Override
			public void run(){
				for(int i = 0; i < max; i++){
					bufferThreadsTest.add(i+max*2);
				}
			}
		};
		
		Thread reader = new Thread(){
			@Override
			public void run(){
				long i = 0;
				while(insertRunning){
					i++;
					bufferThreadsTest.swap();
					//for(int i2: bufferThreadsTest.getChannelUnsafe(bufferThreadsTest.getLastChannelID())){
					for(int i2: bufferThreadsTest.getLastChannel()) {
						toSend[i2] = 1;
					}
					bufferThreadsTest.clearOldChannel();
				}
				System.out.println("Swapped "+i+" times");
			}
		};
		insertRunning = true;
		reader.start();
		inserter.start();
		inserter2.start();
		inserter3.start();
		
		inserter.join();
		inserter2.join();
		inserter3.join();
		insertRunning = false;
		reader.join();

		System.out.println("Took "+(System.currentTimeMillis() - time)+" ms");
		System.out.println("Verifying");
		
		for(int i : toSend) {
			assertTrue(String.valueOf(i),i == 1);
		}
	}

}
