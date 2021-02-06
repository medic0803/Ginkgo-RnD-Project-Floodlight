package net.floodlightcontroller.qos.ResourceMonitor;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;


import java.util.Map;

/**
 * @author Michael Kang
 * @create 2021-01-29 上午 10:52
 */
public interface MonitorBandwidthService extends IFloodlightService {
    public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthMap();
}
