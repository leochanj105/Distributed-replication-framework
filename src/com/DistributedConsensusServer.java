package com;

import java.rmi.RemoteException;

import util.interactiveMethodInvocation.ConsensusAlgorithm;
import util.interactiveMethodInvocation.IPCMechanism;

public interface DistributedConsensusServer extends DistributedServer {
	public void join(String clientName, DistributedConsensusClient client, IPCMechanism value) throws RemoteException;
	public void broadcastBroadcastMode(String clientName, boolean value) throws RemoteException;
	public void broadcast(String clientName, String newCommand, IPCMechanism value, ConsensusAlgorithm algorithm) throws RemoteException;
}
