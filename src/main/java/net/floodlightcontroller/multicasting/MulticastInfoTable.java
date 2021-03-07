package net.floodlightcontroller.multicasting;

import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

public class MulticastInfoTable extends ConcurrentHashMap<IPv4Address, Vector<IPv4Address>>{

}
