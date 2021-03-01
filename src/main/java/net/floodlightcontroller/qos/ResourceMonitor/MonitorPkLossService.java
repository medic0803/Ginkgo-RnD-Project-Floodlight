package net.floodlightcontroller.qos.ResourceMonitor;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.qos.ResourceMonitor.pojo.SwitchPortPkLoss;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.Map;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 04:12
 */
public interface MonitorPkLossService extends IFloodlightService{
    public void collectStatistics(boolean collect);
    public Map<NodePortTuple, SwitchPortPkLoss> getPkLoss();
    public SwitchPortPkLoss getPkLoss(DatapathId dpid, OFPort p);
}