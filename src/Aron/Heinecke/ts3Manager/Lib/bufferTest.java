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

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Simple tester for SBuffer
 * @author Aron Heinecke
 */
public class bufferTest {
	private static Buffer<Integer> buffer;
	private boolean insertRunning = false;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		buffer = new Buffer<Integer>(2);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Test
	public void test() {
		System.out.println("Starting..");
		long time = System.currentTimeMillis();
		Thread inserter = new Thread(){
			@Override
			public void run(){
				insertRunning = true;
				for(int i = 0; i < 2000000; i++){
					buffer.add(i);
					try {
						Thread.sleep(3);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				insertRunning = false;
			}
		};
		Thread inserter2 = new Thread(){
			@Override
			public void run(){
				for(int i = 0; i < 200000; i++){
					buffer.add(i);
					try {
						Thread.sleep(2);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		
		Thread reader = new Thread(){
			@Override
			public void run(){
				long i = 0;
				while(insertRunning){
					try {
						Thread.sleep(477);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					i++;
					buffer.swap();
					for(int i2: buffer.getLastChannel()){
						System.out.print(i2+",");
					}
					System.out.println();
					buffer.clearOldChannel();
				}
				System.out.println("Swapped "+i+" times");
			}
		};
		inserter.start();
		inserter2.start();
		reader.start();
		
		try {
			inserter.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			inserter2.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			reader.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.println("Took "+(System.currentTimeMillis() - time)+" ms");
	}

}
