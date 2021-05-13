package net.floodlightcontroller.qos.QoSManager;

import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.IFloodlightService;

import java.util.Vector;

public interface IQoSManagerService extends IFloodlightService {
    public Vector<Long> getQueues(IOFSwitch sw);
}
