package net.floodlightcontroller.multicasting;

import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

public class MulticastSourceInfoTable extends ConcurrentHashMap<IPv4Address, Vector<MulticastSource>> {
}
