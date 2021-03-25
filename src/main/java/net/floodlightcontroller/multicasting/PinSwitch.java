package net.floodlightcontroller.multicasting;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

public class PinSwitch {

    // instance field
    private DatapathId pinSwitchId;
    private OFPort pinSwitchInPort;

    public PinSwitch(DatapathId pinSwitchId, OFPort pinSwitchInPort) {
        this.pinSwitchId = pinSwitchId;
        this.pinSwitchInPort = pinSwitchInPort;
    }

    public DatapathId getPinSwitchId() {
        return pinSwitchId;
    }
    public OFPort getPinSwitchInPort() {
        return pinSwitchInPort;
    }

}
