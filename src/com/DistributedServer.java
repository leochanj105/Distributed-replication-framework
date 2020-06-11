package com;

import java.rmi.Remote;
import java.rmi.RemoteException;
import util.interactiveMethodInvocation.IPCMechanism;

public interface DistributedServer extends Remote{
//	public void join(String clientName, DistributedClient client, IPCMechanism value) throws RemoteException;
	public void broadcast(String clientName, String command, IPCMechanism value) throws RemoteException;
	public void broadcastIPCMetaState(String clientName, IPCMechanism value) throws RemoteException;
}
