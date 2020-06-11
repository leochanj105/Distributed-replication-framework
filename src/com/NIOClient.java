package com;

import inputport.nio.manager.listeners.SocketChannelConnectListener;
import inputport.nio.manager.listeners.SocketChannelReadListener;
import inputport.nio.manager.listeners.SocketChannelWriteListener;

public interface NIOClient extends SocketChannelConnectListener, SocketChannelWriteListener, SocketChannelReadListener, ReadMaster{

}
