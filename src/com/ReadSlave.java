package com;

import java.util.concurrent.ArrayBlockingQueue;

public class ReadSlave implements Runnable{
	
	private ArrayBlockingQueue<Message> bufferQueue;
	ReadMaster master;
	public ReadSlave(ReadMaster master) {
		this.master = master;
		bufferQueue = master.getBufferQueue();
	}
	
	@Override
	public void run() {
		try {
			while(true) {
				Message message = bufferQueue.take();
				master.finishRead(message);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
}
