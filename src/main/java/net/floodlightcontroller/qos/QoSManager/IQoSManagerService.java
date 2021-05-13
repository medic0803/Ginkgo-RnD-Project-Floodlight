package net.floodlightcontroller.qos.QoSManager;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

public interface IQoSManagerService extends IFloodlightService {
    public ConcurrentHashMap<DatapathId, Vector<Queue>> getQueues();
}
