package net.floodlightcontroller.multicasting;

import org.projectfloodlight.openflow.types.DatapathId;

public class PinSwitch {

    // instance field
    private DatapathId pinSwitchId;
    private DatapathId pinSwitchInPort;

    public PinSwitch(DatapathId pinSwitchId, DatapathId pinSwitchInPort) {
        this.pinSwitchId = pinSwitchId;
        this.pinSwitchInPort = pinSwitchInPort;
    }

    public DatapathId getPinSwitchId() {
        return pinSwitchId;
    }
    public DatapathId getPinSwitchInPort() {
        return pinSwitchInPort;
    }

}
