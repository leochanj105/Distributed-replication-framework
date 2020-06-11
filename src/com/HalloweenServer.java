package com;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import assignments.util.MiscAssignmentUtils;
import assignments.util.inputParameters.AnAbstractSimulationParametersBean;
import assignments.util.mainArgs.ServerArgsProcessor;
import consensus.ProposalFeedbackKind;
import inputport.nio.manager.NIOManager;
import inputport.nio.manager.NIOManagerFactory;
import inputport.nio.manager.factories.classes.AnAcceptCommandFactory;
import inputport.nio.manager.factories.selectors.AcceptCommandFactorySelector;
import inputport.rpc.ACachingAbstractRPCProxyInvocationHandler;
import inputport.rpc.GIPCLocateRegistry;
import inputport.rpc.GIPCRegistry;
import port.ATracingConnectionListener;
import util.interactiveMethodInvocation.ConsensusAlgorithm;
import util.interactiveMethodInvocation.IPCMechanism;
import util.interactiveMethodInvocation.SimulationParametersControllerFactory;
import util.trace.TooManyTracesException;
import util.trace.Tracer;
import util.trace.bean.BeanTraceUtility;
import util.trace.factories.FactoryTraceUtility;
import util.trace.misc.ThreadDelayed;
import util.trace.port.PortTraceUtility;
import util.trace.port.consensus.ConsensusTraceUtility;
import util.trace.port.consensus.ProposalAcceptRequestSent;
import util.trace.port.consensus.ProposalAcceptedNotificationReceived;
import util.trace.port.consensus.ProposalLearnedNotificationSent;
import util.trace.port.consensus.RemoteProposeRequestReceived;
import util.trace.port.consensus.RemoteProposeRequestSent;
import util.trace.port.consensus.communication.CommunicationStateNames;
import util.trace.port.nio.NIOTraceUtility;
import util.trace.port.nio.SocketChannelBound;
import util.trace.port.rpc.gipc.GIPCRPCTraceUtility;
import util.trace.port.rpc.rmi.RMIObjectRegistered;
import util.trace.port.rpc.rmi.RMIRegistryLocated;
import util.trace.port.rpc.rmi.RMITraceUtility;


public class HalloweenServer extends AnAbstractSimulationParametersBean implements DistributedConsensusServer, NIOServer{
	protected int port;
	protected String host; 
	protected int GIPCPort;
	protected int serverPort;
	protected int NIOPort;
	protected int count = 0;
	protected List<DistributedConsensusClient> clientList = new ArrayList<>();
	protected List<String> nameList = new ArrayList<>();
//	private int GIPCCount = 0;
	private List<DistributedConsensusClient> GIPCClientList = new ArrayList<>();
	protected List<String> GIPCNameList = new ArrayList<>();
	private List<DistributedConsensusClient> currentClientList;
	private List<String> currentNameList;
//	private int currentCount = 0;
	
	protected List<SocketChannel> channels = new ArrayList<SocketChannel>();
	protected NIOManager nioManager = NIOManagerFactory.getSingleton();
	private final static int BUFFER_SIZE = 512;
	public static final String READ_THREAD_NAME = "Read Thread";

	private ArrayBlockingQueue<Message> bufferQueue = new ArrayBlockingQueue<>(BUFFER_SIZE);
	private Thread readThread;
	
	
	
	protected void setTracing() {
		PortTraceUtility.setTracing();
		NIOTraceUtility.setTracing();
		RMITraceUtility.setTracing();
		BeanTraceUtility.setTracing();
		FactoryTraceUtility.setTracing();		
		ConsensusTraceUtility.setTracing();
		ThreadDelayed.enablePrint();
		GIPCRPCTraceUtility.setTracing();
		trace(true);
	}
	
	@Override
	public void trace(boolean newValue) {
		super.trace(newValue);
		Tracer.showInfo(isTrace());
	}
	
	protected void setFactories() {
		AcceptCommandFactorySelector.setFactory(new AnAcceptCommandFactory(SelectionKey.OP_READ));
	}
	
	protected void init(String[] args) {
		port = ServerArgsProcessor.getRegistryPort(args);
		host = ServerArgsProcessor.getRegistryHost(args);
		GIPCPort = ServerArgsProcessor.getGIPCServerPort(args);
		serverPort = ServerArgsProcessor.getServerPort(args);
		NIOPort = ServerArgsProcessor.getNIOServerPort(args);
		setTracing();
		setFactories();
	}
	public void start(String args[]) {
		init(args);
		ACachingAbstractRPCProxyInvocationHandler.setInvokeObjectMethodsRemotely(false);
		try {
			Registry rmiRegistry = LocateRegistry.getRegistry(port);
			RMIRegistryLocated.newCase(this, host, port, rmiRegistry);
			RMIObjectRegistered.newCase(this, DistributedConsensusServer.class.getName(), this, rmiRegistry);
			UnicastRemoteObject.exportObject(this, 0);
			rmiRegistry.rebind(DistributedConsensusServer.class.getName(), this);
		} catch (Exception e) {
			e.printStackTrace();
		}

		GIPCRegistry gipcRegistry = GIPCLocateRegistry.createRegistry(GIPCPort);
		gipcRegistry.rebind("GIPCServer", this);
		gipcRegistry.getInputPort().addConnectionListener(new ATracingConnectionListener(gipcRegistry.getInputPort()));
//		ipcMechanism = IPCMechanism.NIO;
		currentClientList = clientList;
		currentNameList = nameList;
		
		try {
			ServerSocketChannel aServerFactoryChannel = ServerSocketChannel.open();
			InetSocketAddress anInternetSocketAddress = new InetSocketAddress(NIOPort);
			aServerFactoryChannel.socket().bind(anInternetSocketAddress);
			SocketChannelBound.newCase(this, aServerFactoryChannel, anInternetSocketAddress);
			nioManager.enableListenableAccepts(aServerFactoryChannel, this);
			
		} catch (IOException e) {
			e.printStackTrace();
		}		
		this.setBroadcastMetaState(true);
		readThread = new Thread(new ReadSlave(this));
		readThread.setName(READ_THREAD_NAME);
		readThread.start();
		SimulationParametersControllerFactory.getSingleton().addSimulationParameterListener(this);
		SimulationParametersControllerFactory.getSingleton().processCommands();		
	}

	@Override	
	public void quit(int aCode) {
		readThread.interrupt();
		System.exit(aCode);
	}
	
	@Override
	public void join(String clientName, DistributedConsensusClient client, IPCMechanism value) {
//		System.out.println("$$$$$$$$$$$$$$$$$$$$$$$: " + clientName);
//		for(StackTraceElement s:Thread.currentThread().getStackTrace())
//			System.out.println("$: " + s.getMethodName());
		switch(value) {
			case RMI:
				count++;
				clientList.add(client);
				nameList.add(clientName);
				return;
			case GIPC:
//				GIPCCount++;
				GIPCClientList.add(client);
				GIPCNameList.add(clientName);
			case NIO:
				return ;
		}
		
	}

	@Override
	public void broadcast(String clientName, String newCommand, IPCMechanism ipcMechanism) {
		changeIPC(ipcMechanism);
		
		int num = currentClientList.size();
			
		for(int i = 0; i < num; i++) {
			if(!(clientName.equals(currentNameList.get(i)) && !isAtomicBroadcast())) {
				ProposalLearnedNotificationSent.newCase(this, CommunicationStateNames.COMMAND, -1, newCommand);
				try {
					currentClientList.get(i).updateCommand(newCommand);
				} catch(RemoteException e) {
					e.printStackTrace();
				}
			}
		}

	}
	
	@Override
	public void broadcast(String clientName, String newCommand, IPCMechanism value, ConsensusAlgorithm algorithm){
		RemoteProposeRequestReceived.newCase(this, CommunicationStateNames.COMMAND, -1, newCommand);
		if(algorithm == ConsensusAlgorithm.CENTRALIZED_ASYNCHRONOUS) {
			this.broadcast(clientName, newCommand, value);
		}
		else if(algorithm == ConsensusAlgorithm.CENTRALIZED_SYNCHRONOUS){
			
			int num = clientList.size();
			ProposalFeedbackKind feedback;
			for(int i = 0; i < num; i++) {
				ProposalAcceptRequestSent.newCase(this, CommunicationStateNames.COMMAND, -1, newCommand);
				try {
					feedback = clientList.get(i).accept(CommunicationStateNames.COMMAND, newCommand);
					ProposalAcceptedNotificationReceived.newCase(this, CommunicationStateNames.COMMAND, -1, newCommand, feedback);
					if(feedback != ProposalFeedbackKind.SUCCESS) {
						newCommand = "";
						break;
					}
				} catch (RemoteException e) {
					e.printStackTrace();
				}
			}
			
			broadcast(clientName, newCommand, value);
//			ProposalLearnedNotificationSent.newCase(this, CommunicationStateNames.COMMAND, -1, newCommand);
//			num = currentClientList.size();
//			for(int i = 0; i < num; i++) {
//				try {
//					currentClientList.get(i).updateCommand(newCommand);
//				} catch (RemoteException e) {
//					e.printStackTrace();
//				}	
//			}
			
		}
		
	}
	@Override
	public void ipcMechanism(IPCMechanism newValue) {
		super.ipcMechanism(newValue);
		changeIPC(newValue);
		if(isBroadcastMetaState())
		{
			try {
				RemoteProposeRequestSent.newCase(this, CommunicationStateNames.IPC_MECHANISM, -1, newValue);
//				server.broadcastIPCMetaState(clientName, newValue);
				for(int i = 0; i < count; i++) {
					clientList.get(i).setIPCMetaState(newValue);
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (TooManyTracesException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void atomicBroadcast(boolean newValue) {
		super.atomicBroadcast(newValue);
		if(isBroadcastMetaState())
		{
			try {
				RemoteProposeRequestSent.newCase(this, CommunicationStateNames.BROADCAST_MODE, -1, newValue);
//				server.broadcastIPCMetaState(clientName, newValue);
				for(int i = 0; i < count; i++) {
					clientList.get(i).setBroadcastMode(newValue);
				}
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (TooManyTracesException e) {
				e.printStackTrace();
			}
		}
		
	}
	@Override
	public void consensusAlgorithm(ConsensusAlgorithm newVal) {
		this.consensusAlgorithm = newVal;
		if(newVal == ConsensusAlgorithm.CENTRALIZED_SYNCHRONOUS) this.atomicBroadcast(true);
	}
	
	
	public void changeIPC(IPCMechanism newValue) {
		switch(newValue) {
			case RMI:
				currentNameList = nameList;
				currentClientList = clientList;
				return;
			case GIPC:
				currentNameList = GIPCNameList;
				currentClientList = GIPCClientList;
				return;
			case NIO:
				return ;
		}
	}
	
	@Override
	public void broadcastIPCMetaState(String clientName, IPCMechanism value) {
		RemoteProposeRequestReceived.newCase(this, CommunicationStateNames.IPC_MECHANISM, -1, value);
		ProposalLearnedNotificationSent.newCase(this, CommunicationStateNames.IPC_MECHANISM, -1, value);
		setIPCMechanism(value);
		changeIPC(value);
		try {
			for(int i = 0; i < count; i++) {
				if(!(clientName.equals(nameList.get(i)))) {
					clientList.get(i).setIPCMetaState(value);
				}
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void socketChannelAccepted(ServerSocketChannel aServerSocketChannel, SocketChannel aSocketChannel) {
		nioManager.addReadListener(aSocketChannel, this);
		channels.add(aSocketChannel);
	}
	@Override
	public void socketChannelRead(SocketChannel aSocketChannel, ByteBuffer aMessage, int aLength) {
		if(!bufferQueue.add(new Message(aSocketChannel, MiscAssignmentUtils.deepDuplicate(aMessage), aLength))) {
			System.out.println("Buffer is full!");
		}
	}
	@Override
	public void written(SocketChannel aSocketChannel, ByteBuffer aMessage, int aLength) {
		
	}

	@Override
	public void finishRead(Message message) {
		String newCommand = new String(message.getBuffer().array(), message.getBuffer().position(), message.getLength());
		RemoteProposeRequestReceived.newCase(this, CommunicationStateNames.COMMAND, -1, newCommand);
		for(SocketChannel ch : channels) {
			if(!(message.getChannel().equals(ch) && !isAtomicBroadcast())) {
				ProposalLearnedNotificationSent.newCase(this, CommunicationStateNames.COMMAND, -1, newCommand);
				nioManager.write(ch, message.getBuffer(), this);
			}
		}	
	}

	@Override
	public ArrayBlockingQueue<Message> getBufferQueue() {
		return this.bufferQueue;
	}

	@Override
	public void broadcastBroadcastMode(String clientName, boolean value){
		RemoteProposeRequestReceived.newCase(this, CommunicationStateNames.BROADCAST_MODE, -1, value);
		ProposalLearnedNotificationSent.newCase(this, CommunicationStateNames.BROADCAST_MODE, -1, value);
		super.setAtomicBroadcast(value);
		try {
			for(int i = 0; i < count; i++) {
				if(!clientName.equals(nameList.get(i))) {
					clientList.get(i).setBroadcastMode(value);
				}
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
}
