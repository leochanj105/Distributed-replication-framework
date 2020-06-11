package com;

import java.rmi.RemoteException;

import consensus.ProposalFeedbackKind;

public interface DistributedConsensusClient extends DistributedClient {
	public void setBroadcastMode(boolean value) throws RemoteException;
	public ProposalFeedbackKind accept(String name, Object val) throws RemoteException;
}
