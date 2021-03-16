package net.floodlightcontroller.multicasting;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.concurrent.ConcurrentHashMap;

public class PinSwitchInfoTable extends ConcurrentHashMap<IPv4Address, ConcurrentHashMap<DatapathId, OFPort>> {
}
