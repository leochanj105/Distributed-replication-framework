package com;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Message {
	public SocketChannel channel;
	public ByteBuffer buffer;
	public int length;
	public Message(SocketChannel channel, ByteBuffer buffer,int length) {
		this.channel = channel;
		this.buffer = buffer;
		this.length = length;
	}
	public SocketChannel getChannel() {
		return channel;
	}
	public void setChannel(SocketChannel channel) {
		this.channel = channel;
	}
	public ByteBuffer getBuffer() {
		return buffer;
	}
	public void setBuffer(ByteBuffer buffer) {
		this.buffer = buffer;
	}
	public int getLength() {
		return length;
	}
	public void setLength(int length) {
		this.length = length;
	}
	
	
}
