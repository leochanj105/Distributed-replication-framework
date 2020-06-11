package com;

import util.annotations.Tags;
import util.tags.DistributedTags;

@Tags({DistributedTags.SERVER, DistributedTags.RMI, DistributedTags.GIPC, DistributedTags.NIO})
public class SimulationServer {
	public static void main(String args[]) {
		new HalloweenServer().start(args);
	}
}
