package net.floodlightcontroller.qos.ResourceMonitor;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;

import java.util.Map;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 06:13
 */
public interface QosResourceMonitor extends IFloodlightService {
    /**
     * kwm:
     * 获取带宽使用情况
     * 需要简单的换算
     根据 switchPortBand.getBitsPerSecondRx().getValue()/(8*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8*1024)
     计算带宽
     */
    public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthMap();
//    public Map<MyEntry<NodePortTuple,NodePortTuple>,Integer> getLinkDelay();
//    public Map<> getPkLoss(Object o1,Object o2);
}
