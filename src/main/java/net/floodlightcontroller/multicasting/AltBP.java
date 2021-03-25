package net.floodlightcontroller.multicasting;

import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.HashSet;

public class AltBP {    // Alternate Branch Point

    // instance field
    private HashSet<OFPort> outPortSet;
    private OFFlowMod.Builder fmb;
    private int groupNumber;

    public AltBP(OFPort outPort) {
        this.outPortSet = new HashSet<>();
        outPortSet.add(outPort);
        this.groupNumber = 0;
    }

    public HashSet<OFPort> getOutPortSet() {
        return outPortSet;
    }

    public OFFlowMod.Builder getFmb() { // clone a OFFlowMod Builder and return, which keep the pure of the local fmb
        return fmb.build().createBuilder();
    }

    public int getGroupNumber() {
        return groupNumber;
    }

    public void setFmb(OFFlowMod.Builder fmb) {
        this.fmb = fmb;
    }

    public void setGroupNumber(int groupNumber) {
        this.groupNumber = groupNumber;
    }
}
