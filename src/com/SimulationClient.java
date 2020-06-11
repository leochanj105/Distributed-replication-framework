package com;

import util.annotations.Tags;
import util.tags.DistributedTags;

@Tags({DistributedTags.CLIENT, DistributedTags.RMI, DistributedTags.GIPC, DistributedTags.NIO})
public class SimulationClient {
	public static void main(String args[]) {
		new HalloweenClient().start(args);
	}
}
