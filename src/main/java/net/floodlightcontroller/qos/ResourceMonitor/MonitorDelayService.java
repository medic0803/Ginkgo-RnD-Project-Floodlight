package net.floodlightcontroller.qos.ResourceMonitor;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.qos.ResourceMonitor.pojo.LinkEntry;

import java.util.Map;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 06:01
 */
public interface MonitorDelayService extends IFloodlightService {
    /**
     * 获取链路之间的时间延迟
     * @return Map<MyEntry<NodePortTuple,NodePortTuple>,Integer> 链路：时延(b)
     */
    public Map<LinkEntry<NodePortTuple,NodePortTuple>,Integer> getLinkDelay();
}
