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
    //宽带使用情况
    /**
     * 获取带宽使用情况
     * 需要简单的换算
     根据 switchPortBand.getBitsPerSecondRx().getValue()/(8*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8*1024)
     计算带宽
     */
    public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthMap();
}
