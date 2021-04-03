package net.floodlightcontroller.multicasting;

import net.floodlightcontroller.routing.Path;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.Date;
import java.util.HashMap;

public class MulticastTree {

    // instance field
    private HashMap<IPv4Address, Path> pathList;    // K: Host's IPv4 Address, V: path
    private HashMap<DatapathId, AltBP> altBPRegister;  // K: Switch, V: out ports
    private IPv4Address sourceAddress;
    private Date sourceValidTime;

    public MulticastTree (IPv4Address sourceAddress) {
        this.pathList = new HashMap<>();
        this.altBPRegister = new HashMap<>();
        this.sourceAddress = sourceAddress;
        setSourceValidTime();
    }

    public HashMap<IPv4Address, Path> getPathList() {
        return pathList;
    }

    public HashMap<DatapathId, AltBP> getAltBPRegister() {
        return altBPRegister;
    }

    public Date getSourceValidTime() {
        return sourceValidTime;
    }

    public void setSourceValidTime() {
        this.sourceValidTime = new Date(System.currentTimeMillis());
    }

    public IPv4Address getSourceAddress() {
        return sourceAddress;
    }
}
