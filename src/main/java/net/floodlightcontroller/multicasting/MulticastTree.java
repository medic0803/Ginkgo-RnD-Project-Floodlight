package net.floodlightcontroller.multicasting;

import net.floodlightcontroller.routing.Path;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.HashMap;
import java.util.HashSet;

public class MulticastTree {

    // instance field
    private HashMap<IPv4Address, Path> pathList;    // K: Host's IPv4 Address, V: path
    private HashMap<DatapathId, HashSet<OFPort>> outPortsRegister;  // K: Switch, V: out ports

    public MulticastTree () {
        this.pathList = new HashMap<>();
        this.outPortsRegister = new HashMap<>();
    }

    public HashMap<IPv4Address, Path> getPathList() {
        return pathList;
    }

    public HashMap<DatapathId, HashSet<OFPort>> getOutPortsRegister() {
        return outPortsRegister;
    }
}
