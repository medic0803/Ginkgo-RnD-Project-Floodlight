package net.floodlightcontroller.qos.ResourceMonitor;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.qos.ResourceMonitor.pojo.LinkEntry;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;

import java.util.Map;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 06:13
 */
public interface QosResourceMonitor extends IFloodlightService {
    /**
     * kwmtodo:翻译
     * 使用Floodlight方法获取带宽
     * 需要简单的换算
     根据 switchPortBand.getBitsPerSecondRx().getValue()/(8*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8*1024)
     计算带宽
     */
    public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthMap();

    /**
     * @return key:Link  value: delay （ms）
     */
    public Map<LinkEntry<NodePortTuple,NodePortTuple>,Integer> getLinkDelay();
//    public Map<> getPkLoss(Object o1,Object o2);
}
