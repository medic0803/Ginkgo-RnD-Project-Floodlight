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

    public void setPinSwitchId(DatapathId pinSwitchId) {
        this.pinSwitchId = pinSwitchId;
    }

    public DatapathId getPinSwitchInPort() {
        return pinSwitchInPort;
    }

    public void setPinSwitchInPort(DatapathId pinSwitchInPort) {
        this.pinSwitchInPort = pinSwitchInPort;
    }
}
