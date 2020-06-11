package com;
import java.rmi.registry.LocateRegistry;
import java.util.Scanner;

import assignments.util.mainArgs.RegistryArgsProcessor;
import util.annotations.Tags;
import util.tags.DistributedTags;
import util.trace.factories.FactoryTraceUtility;
import util.trace.misc.ThreadDelayed;
import util.trace.port.PortTraceUtility;
import util.trace.port.consensus.ConsensusTraceUtility;
import util.trace.port.nio.NIOTraceUtility;
import util.trace.port.rpc.rmi.RMIRegistryCreated;
import util.trace.port.rpc.rmi.RMITraceUtility;

@Tags({DistributedTags.REGISTRY, DistributedTags.RMI})
public class SimulationRegistry {
	private int port;
	public static void main(String args[]) {
		new SimulationRegistry().start(args);
	}
	
	public void start(String args[]) {
		Scanner scanner = new Scanner(System.in);
		port = RegistryArgsProcessor.getRegistryPort(args);
		PortTraceUtility.setTracing();
		NIOTraceUtility.setTracing();
		RMITraceUtility.setTracing();
		
		FactoryTraceUtility.setTracing();		
		ConsensusTraceUtility.setTracing();
		ThreadDelayed.enablePrint();
		try {
			RMIRegistryCreated.newCase(this, port);
			LocateRegistry.createRegistry(port);
			scanner.nextLine();
		} catch (Exception e) {
			e.printStackTrace();
		}
		scanner.close();
	}
}
