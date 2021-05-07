package net.floodlightcontroller.qos.ResourceMonitor;

import net.floodlightcontroller.core.module.IFloodlightService;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 06:01
 */
public interface MonitorDelayService extends IFloodlightService {
    /**
     * 获取链路之间的时间延迟
     * @return Map<MyEntry<DatapathId,DatapathId>,Integer> 链路：时延(b)
     */
    public ConcurrentHashMap<String,Integer> getLinkDelay();

    public ConcurrentHashMap<String,Integer> getLinkJitter();
}
