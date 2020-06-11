package com;

import inputport.nio.manager.listeners.SocketChannelAcceptListener;
import inputport.nio.manager.listeners.SocketChannelReadListener;
import inputport.nio.manager.listeners.SocketChannelWriteListener;

public interface NIOServer extends SocketChannelAcceptListener, SocketChannelReadListener, SocketChannelWriteListener, ReadMaster{

}
