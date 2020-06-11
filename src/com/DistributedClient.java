package com;

import java.rmi.Remote;
import java.rmi.RemoteException;
import util.interactiveMethodInvocation.IPCMechanism;

public interface DistributedClient extends Remote{
	public void updateCommand(String command) throws RemoteException;
	public void setIPCMetaState(IPCMechanism value) throws RemoteException;
}
