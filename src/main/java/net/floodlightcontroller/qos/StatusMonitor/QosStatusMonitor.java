package net.floodlightcontroller.qos.StatusMonitor;

import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;

import java.util.Map;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 06:13
 */
public interface QosStatusMonitor extends IFloodlightService, IFloodlightModule {
    public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthMap();
//    public Map<MyEntry<NodePortTuple,NodePortTuple>,Integer> getLinkDelay();
//    public Map<> getPkLoss(Object o1,Object o2);
}
