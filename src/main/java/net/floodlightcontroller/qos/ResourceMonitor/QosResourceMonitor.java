package net.floodlightcontroller.qos.ResourceMonitor;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.qos.ResourceMonitor.pojo.LinkEntry;
import net.floodlightcontroller.qos.ResourceMonitor.pojo.SwitchPortPkLoss;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.Map;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 06:13
 */
public interface QosResourceMonitor extends IFloodlightService{
    /**
     * use the Floodlight method (send request to Switch) collect the switch stats, there is the bandwith
     * use example: switchPortBand.getBitsPerSecondRx().getValue()/(8*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8*1024)
     */
    public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthMap();

    //set the function to open or close the bandwith Collection module
    public void setBandwidthCollection (boolean collect);

    /**
     * MonitorDelayServices
     * use the service of Floodlight get links, and return the delay using class Link and LinkInfo of Floodlight
     * @return key-value ==> Link-delay （ms）
     */
    public Map<LinkEntry<NodePortTuple,NodePortTuple>,Integer> getLinkDelay();

    /**
     * MonitorPkLossServices
     * shape the method of the StatisticsCollector used in bandwith to collect Parket loss
     */
    public void setPkLossCollection (boolean collect);
    public Map<NodePortTuple, SwitchPortPkLoss> getPkLoss();
    public SwitchPortPkLoss getPkLoss(DatapathId dpid, OFPort p);
}