package com;
import java.util.concurrent.ArrayBlockingQueue;

public interface ReadMaster {
	public void finishRead(Message message);
	public ArrayBlockingQueue<Message> getBufferQueue();
}
