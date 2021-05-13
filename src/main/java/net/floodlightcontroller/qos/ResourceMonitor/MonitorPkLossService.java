package net.floodlightcontroller.qos.ResourceMonitor;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.qos.ResourceMonitor.pojo.SwitchPortCounter;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.Map;

/**
 * @author Michael Kang
 * @create 2021-01-29 04:12 PM
 */
public interface MonitorPkLossService extends IFloodlightService{
    public void collectStatistics(boolean collect);
    public Map<NodePortTuple, SwitchPortCounter> getPortStatsMap();
}
