package net.floodlightcontroller.qos.StatusMonitor;

import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 06:01
 */
public interface MonitorDelayService extends IFloodlightService, IFloodlightModule, IOFMessageListener, IOFSwitchListener {
    /**
     * 获取链路之间的时间延迟
     * @return Map<MyEntry<NodePortTuple,NodePortTuple>,Integer> 链路：时延
     */
//  public Map<MyEntry<NodePortTuple,NodePortTuple>,Integer> getLinkDelay();
}
