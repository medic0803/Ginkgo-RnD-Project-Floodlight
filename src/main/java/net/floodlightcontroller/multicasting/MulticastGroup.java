package net.floodlightcontroller.multicasting;

import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.HashMap;
import java.util.HashSet;

public class MulticastGroup {

    // instance field
    private IPv4Address multicastAddress;

    private HashSet<IPv4Address> multicastHosts; // Hosts IPv4 Address Set
    private HashMap<IPv4Address, MulticastSource> multicastSources; // Sources objects map
    private HashMap<IPv4Address, MulticastTree> multicastTreeInfoTable; // MulticastTree objects map


    public MulticastGroup(IPv4Address multicastAddress, IPv4Address hostAddress) {
        this.multicastAddress = multicastAddress;
        this.multicastHosts = new HashSet<>();
        this.multicastSources = new HashMap<>();
        this.multicastTreeInfoTable = new HashMap<>();

        this.multicastHosts.add(hostAddress);
    }

    public MulticastGroup(IPv4Address multicastAddress, IPv4Address sourceAddress, MulticastSource multicastSource) {
        this.multicastAddress = multicastAddress;
        this.multicastHosts = new HashSet<>();
        this.multicastSources = new HashMap<>();
        this.multicastTreeInfoTable = new HashMap<>();

        this.multicastSources.put(sourceAddress, multicastSource);
    }

    public IPv4Address getMulticastAddress() {
        return multicastAddress;
    }

    public HashSet<IPv4Address> getMulticastHosts() {
        return multicastHosts;
    }

    public HashMap<IPv4Address, MulticastSource> getMulticastSources() {
        return multicastSources;
    }

    public HashMap<IPv4Address, MulticastTree> getMulticastTreeInfoTable() {
        return multicastTreeInfoTable;
    }
}

