package net.floodlightcontroller.qos.ResourceMonitor;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.qos.ResourceMonitor.pojo.LinkEntry;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.Map;

/**
 * @author Michael Kang
 * @create 2021-01-29 06:01 PM
 */
public interface MonitorDelayService extends IFloodlightService {
    /**
     * Fetech the delay between the links
     * @return Map<MyEntry<DatapathId,DatapathId>,Integer> Link : Delay(ms)
     */
    public Map<LinkEntry<DatapathId,DatapathId>,Integer> getLinkDelay();
}
