package com;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.ArrayBlockingQueue;

import assignments.util.MiscAssignmentUtils;
import assignments.util.inputParameters.AnAbstractSimulationParametersBean;
import assignments.util.mainArgs.ClientArgsProcessor;
import consensus.ProposalFeedbackKind;
import coupledsims.Simulation;
import coupledsims.Simulation1;
import coupledsims.StandAloneTwoCoupledHalloweenSimulations;
import inputport.nio.manager.NIOManager;
import inputport.nio.manager.NIOManagerFactory;
import inputport.nio.manager.factories.classes.AConnectCommandFactory;
import inputport.nio.manager.factories.selectors.ConnectCommandFactorySelector;
import inputport.rpc.ACachingAbstractRPCProxyInvocationHandler;
import inputport.rpc.GIPCLocateRegistry;
import inputport.rpc.GIPCRegistry;
import main.BeauAndersonFinalProject;
import port.ATracingConnectionListener;
import stringProcessors.HalloweenCommandProcessor;
import util.interactiveMethodInvocation.ConsensusAlgorithm;
import util.interactiveMethodInvocation.IPCMechanism;
import util.interactiveMethodInvocation.SimulationParametersControllerFactory;
import util.misc.ThreadSupport;
import util.trace.TooManyTracesException;
import util.trace.Tracer;
import util.trace.bean.BeanTraceUtility;
import util.trace.factories.FactoryTraceUtility;
import util.trace.misc.ThreadDelayed;
import util.trace.port.PerformanceExperimentEnded;
import util.trace.port.PerformanceExperimentStarted;
import util.trace.port.PortTraceUtility;
import util.trace.port.consensus.ConsensusTraceUtility;
import util.trace.port.consensus.ProposalAcceptRequestReceived;
import util.trace.port.consensus.ProposalAcceptedNotificationSent;
import util.trace.port.consensus.ProposalLearnedNotificationReceived;
import util.trace.port.consensus.ProposalMade;
import util.trace.port.consensus.ProposedStateSet;
import util.trace.port.consensus.RemoteProposeRequestSent;
import util.trace.port.consensus.communication.CommunicationStateNames;
import util.trace.port.nio.NIOTraceUtility;
import util.trace.port.rpc.gipc.GIPCRPCTraceUtility;
import util.trace.port.rpc.rmi.RMIObjectLookedUp;
import util.trace.port.rpc.rmi.RMIRegistryLocated;
import util.trace.port.rpc.rmi.RMITraceUtility;


public class HalloweenClient extends AnAbstractSimulationParametersBean implements DistributedConsensusClient, NIOClient,
												StandAloneTwoCoupledHalloweenSimulations, PropertyChangeListener{
	protected int NUM_EXPERIMENT_COMMANDS = 500;
	public static final String EXPERIMENT_COMMAND_1 = "move 1 -1";
	public static final String EXPERIMENT_COMMAND_2 = "undo";
	private HalloweenCommandProcessor commandProcessor;
	
	private DistributedConsensusServer server;
	private DistributedConsensusServer GIPCServer;
	private DistributedConsensusServer currentServer;
	private String registryHost;
	private int registryPort;
	private String serverHost;
	private String clientName;
	private String headless;
	private int GIPCPort;
	private int NIOPort;
	
	
	protected NIOManager nioManager = NIOManagerFactory.getSingleton();
	protected SocketChannel socketChannel;
	
	
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
	
	void processArgs(String[] args) {	
		registryHost = ClientArgsProcessor.getRegistryHost(args);
		registryPort = ClientArgsProcessor.getRegistryPort(args);
		serverHost = ClientArgsProcessor.getServerHost(args);
		headless = ClientArgsProcessor.getHeadless(args);
		clientName = ClientArgsProcessor.getClientName(args);
		GIPCPort = ClientArgsProcessor.getGIPCPort(args);
		NIOPort = ClientArgsProcessor.getNIOServerPort(args);
		// Make sure you set this property when processing args
		System.setProperty("java.awt.headless", headless);
		
	}
	
	protected void setFactories() {		
		ConnectCommandFactorySelector.setFactory(new AConnectCommandFactory(SelectionKey.OP_READ));
	}
	
	protected void init(String[] args) {
		setTracing();
		setFactories();
		processArgs(args);
		commandProcessor = BeauAndersonFinalProject.createSimulation(
						   Simulation1.SIMULATION1_PREFIX,
						   Simulation1.SIMULATION1_X_OFFSET, 
						   Simulation.SIMULATION_Y_OFFSET, 
						   Simulation.SIMULATION_WIDTH, 
						   Simulation.SIMULATION_HEIGHT, 
						   Simulation1.SIMULATION1_X_OFFSET, 
						   Simulation.SIMULATION_Y_OFFSET);
		commandProcessor.addPropertyChangeListener(this);
	}

	@Override
	public void start(String[] args) {
		init(args);
		// register a callback to process actions denoted by the user commands
		ACachingAbstractRPCProxyInvocationHandler.setInvokeObjectMethodsRemotely(false);
		try {
			Registry rmiRegistry = LocateRegistry.getRegistry(registryPort);
			RMIRegistryLocated.newCase(this, registryHost, registryPort, rmiRegistry);
			UnicastRemoteObject.exportObject(this, 0);
			server = (DistributedConsensusServer) rmiRegistry.lookup(DistributedConsensusServer.class.getName());
			RMIObjectLookedUp.newCase(this, server, DistributedServer.class.getName(), rmiRegistry);
			server.join(clientName, this, IPCMechanism.RMI);
			
			GIPCRegistry gipcRegistry = GIPCLocateRegistry.getRegistry(serverHost, GIPCPort, clientName);
			GIPCServer = (DistributedConsensusServer) gipcRegistry.lookup(DistributedConsensusServer.class, "GIPCServer");
			GIPCServer.join(clientName, this, IPCMechanism.GIPC);
			gipcRegistry.getInputPort().addConnectionListener(new ATracingConnectionListener(gipcRegistry.getInputPort()));
			currentServer = GIPCServer;
			
			socketChannel = SocketChannel.open();
			InetAddress aServerAddress = InetAddress.getByName(serverHost);
			nioManager.connect(socketChannel, aServerAddress, NIOPort, this);
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}		
		this.setBroadcastMetaState(true);
		SimulationParametersControllerFactory.getSingleton().addSimulationParameterListener(this);
		SimulationParametersControllerFactory.getSingleton().processCommands();		
	}

	
	@Override	
	public void quit(int aCode) {
		readThread.interrupt();
		System.exit(aCode);
	}	
	@Override
	/*
	 * You will need to delay not command input but sends(non-Javadoc)
	 */
	public void simulationCommand(String aCommand) {
		long aDelay = getDelay(); 
		if (aDelay > 0) {
			ThreadSupport.sleep(aDelay);
		}
		ProposalMade.newCase(this, CommunicationStateNames.COMMAND, -1, aCommand);
		commandProcessor.setInputString(aCommand); 
	}
	@Override	
	public void trace(boolean newValue) {
		super.trace(newValue);
		Tracer.showInfo(isTrace());
	}
	@Override
	public void localProcessingOnly(boolean newValue) {
		super.localProcessingOnly(newValue);
	}
	@Override
	/**
	 * Relevant in consistency assignments only 
	 */
	public void atomicBroadcast(boolean newValue) {
		super.atomicBroadcast(newValue);
		commandProcessor.setConnectedToSimulation(!isAtomicBroadcast());
		if(isBroadcastMetaState())
		{
			try {
				RemoteProposeRequestSent.newCase(this, CommunicationStateNames.BROADCAST_MODE, -1, newValue);
				server.broadcastBroadcastMode(clientName, newValue);
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (TooManyTracesException e) {
				e.printStackTrace();
			}
		}
		
	}
	@Override
	public void experimentInput() {
		long aStartTime = System.currentTimeMillis();
		PerformanceExperimentStarted.newCase(this, aStartTime, NUM_EXPERIMENT_COMMANDS);
		boolean anOldValue = isTrace();
		this.trace(false);
		for (int i = 0; i < NUM_EXPERIMENT_COMMANDS; i++) {
			commandProcessor.setInputString(EXPERIMENT_COMMAND_1);
			commandProcessor.setInputString(EXPERIMENT_COMMAND_2);
		}
		trace(anOldValue);
		long anEndTime = System.currentTimeMillis();
		PerformanceExperimentEnded.newCase(this, aStartTime, anEndTime, anEndTime - aStartTime, NUM_EXPERIMENT_COMMANDS);
		
	}
	@Override
	/*
	 * This override is not really needed, provided here to show that this method
	 * exists.
	 */
	public void delaySends(int aMillisecondDelay) {
		// invoke setDelaySends so getDelaySends can be used to determine the delay
		// in other parts of the program
		super.delaySends(aMillisecondDelay);
	}


	@Override
	public void updateCommand(String newCommand) {
		ProposalLearnedNotificationReceived.newCase(this, CommunicationStateNames.COMMAND, -1, newCommand);
		commandProcessor.processCommand(newCommand);
		ProposedStateSet.newCase(this, CommunicationStateNames.COMMAND, -1, newCommand);
	}

	@Override
	public void propertyChange(PropertyChangeEvent evt) {
		try {
			if (evt.getPropertyName().equals("InputString")){
				if(isLocalProcessingOnly()) return;
				String newCommand = (String) evt.getNewValue();
				RemoteProposeRequestSent.newCase(this, CommunicationStateNames.COMMAND, -1, newCommand);
				if(ipcMechanism == IPCMechanism.NIO) {
					nioManager.write(socketChannel, ByteBuffer.wrap(newCommand.getBytes()), this);
				}
				else {
					currentServer.broadcast(clientName, newCommand, ipcMechanism, this.consensusAlgorithm);
				}
			}
		} catch (RemoteException e) {
			e.printStackTrace();
		} catch (TooManyTracesException e) {
			e.printStackTrace();
		}
	}
	@Override
	public void consensusAlgorithm(ConsensusAlgorithm newVal) {
		this.consensusAlgorithm = newVal;
		if(newVal == ConsensusAlgorithm.CENTRALIZED_SYNCHRONOUS) this.atomicBroadcast(true);
	}
	
	@Override
	public void ipcMechanism(IPCMechanism newValue) {
		super.ipcMechanism(newValue);
		changeIPC(newValue);
		if(isBroadcastMetaState())
		{
			try {
				RemoteProposeRequestSent.newCase(this, CommunicationStateNames.IPC_MECHANISM, -1, newValue);
				server.broadcastIPCMetaState(clientName, newValue);
			} catch (RemoteException e) {
				e.printStackTrace();
			} catch (TooManyTracesException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void changeIPC(IPCMechanism newValue) {
		switch(newValue) {
			case RMI:
				currentServer = server;
				return;
			case GIPC:
				currentServer = GIPCServer;
				return;
			case NIO:
				return ;
		}
	}
	@Override
	public void setIPCMetaState(IPCMechanism value) {
		ProposalLearnedNotificationReceived.newCase(this, CommunicationStateNames.IPC_MECHANISM, -1, value);
		setIPCMechanism(value);
		changeIPC(value);
	}

	@Override
	public void connected(SocketChannel aSocketChannel) {
		nioManager.addReadListener(aSocketChannel, this);
		readThread = new Thread(new ReadSlave(this));
		readThread.setName(READ_THREAD_NAME);
		readThread.start();
	}
	@Override
	public void notConnected(SocketChannel theSocketChannel, Exception e) {
		
	}
	@Override
	public void written(SocketChannel socketChannel, ByteBuffer theWriteBuffer, int sendId) {
		
	}
	@Override
	public void socketChannelRead(SocketChannel aSocketChannel, ByteBuffer aMessage, int aLength) {
		if(!bufferQueue.add(new Message(aSocketChannel, MiscAssignmentUtils.deepDuplicate(aMessage), aLength))) {
			System.out.println("Buffer is full!");
		}
	}

	@Override
	public void finishRead(Message message){
		String newCommand = new String(message.getBuffer().array(), message.getBuffer().position(), message.getLength());
		updateCommand(newCommand);
	}

	@Override
	public ArrayBlockingQueue<Message> getBufferQueue() {
		return this.bufferQueue;
	}

	@Override
	public void setBroadcastMode(boolean value) {
		ProposalLearnedNotificationReceived.newCase(this, CommunicationStateNames.BROADCAST_MODE, -1, value);
		setAtomicBroadcast(value);
		commandProcessor.setConnectedToSimulation(!isAtomicBroadcast());
	}

	@Override
	public ProposalFeedbackKind accept(String name, Object val) {
		ProposalAcceptRequestReceived.newCase(this, CommunicationStateNames.COMMAND, -1, val);
		ProposalFeedbackKind feedback = ProposalFeedbackKind.SERVICE_DENIAL;
		if(name.equals(CommunicationStateNames.COMMAND)) {	
//			commandProcessor.processCommand("");
			feedback = ProposalFeedbackKind.SUCCESS;
		}
		ProposalAcceptedNotificationSent.newCase(this, CommunicationStateNames.COMMAND, -1, val, feedback);
		return feedback;
	}
	
}
