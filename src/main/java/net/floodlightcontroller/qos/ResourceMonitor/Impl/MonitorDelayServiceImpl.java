package net.floodlightcontroller.qos.ResourceMonitor.Impl;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.linkdiscovery.internal.LinkInfo;
import net.floodlightcontroller.qos.ResourceMonitor.MonitorDelayService;
import net.floodlightcontroller.qos.ResourceMonitor.pojo.LinkEntry;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.types.DatapathId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Michael Kang
 * @create 2021-01-29 PM 06:03
 */
public class MonitorDelayServiceImpl implements MonitorDelayService, IFloodlightModule{

    private static final Logger logger = LoggerFactory.getLogger(MonitorDelayServiceImpl.class);
    private ILinkDiscoveryService linkDiscoveryService;
    private Map<LinkEntry<DatapathId, DatapathId>, Integer> linkDelaySecMap;
    private Map<LinkEntry<DatapathId, DatapathId>, Integer> linkJitterSecMap;

    private Map<LinkEntry<DatapathId, DatapathId>, Integer> first_JitterStampMap;
    private Map<LinkEntry<DatapathId, DatapathId>, Integer> sceond_JitterStampMap;

    private static IThreadPoolService threadPoolService;
    private static ScheduledFuture<?> jitterCollector;
    private static int jitterInterval = 10;

    /**
     * Return the list of interfaces that this module implements.
     * All interfaces must inherit IFloodlightService
     *
     * @return
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(MonitorDelayService.class);
        return l;
    }

    /**
     * Instantiate (as needed) and return objects that implement each
     * of the services exported by this module.  The map returned maps
     * the implemented service to the object.  The object could be the
     * same object or different objects for different exported services.
     *
     * @return The map from service interface class to service implementation
     */
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> l = new HashMap<>();
        l.put(MonitorDelayService.class, this);
        return l;
    }

    /**
     * Get a list of Modules that this module depends on.  The module system
     * will ensure that each these dependencies is resolved before the
     * subsequent calls to init().
     *
     * @return The Collection of IFloodlightServices that this module depends
     * on.
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IThreadPoolService.class);
        l.add(ILinkDiscoveryService.class);
        return l;
    }

    /**
     * This is a hook for each module to do its <em>internal</em> initialization,
     * e.g., call setService(context.getService("Service"))
     * <p>
     * All module dependencies are resolved when this is called, but not every module
     * is initialized.
     *
     * @param context
     * @throws FloodlightModuleException
     */
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        linkDiscoveryService = context.getServiceImpl(ILinkDiscoveryService.class);
        first_JitterStampMap = new HashMap<>();
        sceond_JitterStampMap = new HashMap<>();
        linkDelaySecMap =  new HashMap<>();
        linkJitterSecMap = new HashMap<>();
    }

    /**
     * This is a hook for each module to do its <em>external</em> initializations,
     * e.g., register for callbacks or query for state in other modules
     * <p>
     * It is expected that this function will not block and that modules that want
     * non-event driven CPU will spawn their own threads.
     *
     * @param context
     * @throws FloodlightModuleException
     */
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
        startJitterCollection();
    }

    @Override
    public Map<LinkEntry<DatapathId, DatapathId>, Integer> getLinkDelay() {
        this.linkDelaySecMap.clear();
        Map<Link, LinkInfo> linksMap = linkDiscoveryService.getLinks();
        Iterator<Entry<Link, LinkInfo>> iterator = linksMap.entrySet().iterator();
        while (iterator.hasNext()){
            Entry<Link, LinkInfo> link = iterator.next();
            NodePortTuple src = new NodePortTuple(link.getKey().getSrc(),link.getKey().getSrcPort());
            NodePortTuple dst = new NodePortTuple(link.getKey().getDst(),link.getKey().getDstPort());
            DatapathId srcNode = src.getNodeId();
            DatapathId dstNode = dst.getNodeId();
            LinkEntry<DatapathId,DatapathId> ansKey = new LinkEntry<DatapathId,DatapathId>(srcNode,dstNode);
            if (null == link.getValue().getCurrentLatency()){
                logger.warn("link.getValue().getCurrentLatency() return null");
            }else {
                Integer ansValue = new Long(link.getValue().getCurrentLatency().getValue()).intValue();
                linkDelaySecMap.put(ansKey,ansValue);
            }
        }
        return this.linkDelaySecMap;
    }

    private void startJitterCollection() {
        jitterCollector = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new JitterCollector(),  jitterInterval, jitterInterval, TimeUnit.SECONDS);
    }
    /**
     * Stop all stats threads.
     */
    private void stopJitterCollection() {
        jitterCollector.cancel(false);
    }
    protected class JitterCollector implements Runnable{
        //kwmtodo: calculate the jitter here
        @Override
        public void run() {
            if (first_JitterStampMap.isEmpty()){
                initialLinkJitterMap();
            }else{
                //the second stamp
                sceond_JitterStampMap = getLinkDelay();
                //two stamps is valid
                if (first_JitterStampMap.size() == sceond_JitterStampMap.size()){
                    if (!linkJitterSecMap.isEmpty()){
                        linkJitterSecMap.clear();
                    }
                    //calculate jitter
                    Iterator<LinkEntry<DatapathId, DatapathId>> iterator = first_JitterStampMap.keySet().iterator();
                    while (iterator.hasNext()){
                        LinkEntry<DatapathId, DatapathId> key = iterator.next();
                        linkJitterSecMap.put(key,Math.abs(first_JitterStampMap.get(key) - sceond_JitterStampMap.get(key))/jitterInterval);
                    }
                }
                //update firstStamp
                first_JitterStampMap = sceond_JitterStampMap;
            }
        }
    }
    private void initialLinkJitterMap(){
        this.first_JitterStampMap = getLinkDelay();
    }

    @Override
    public Map<LinkEntry<DatapathId, DatapathId>, Integer> getLinkJitter() {
        return this.linkJitterSecMap;
    }
}
