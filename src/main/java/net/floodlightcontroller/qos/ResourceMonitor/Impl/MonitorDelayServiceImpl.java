package net.floodlightcontroller.qos.ResourceMonitor.Impl;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
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
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.python.modules.time.Time;
import org.sdnplatform.sync.internal.SyncTorture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 06:03
 */
public class MonitorDelayServiceImpl implements MonitorDelayService, IFloodlightModule{

    private static final Logger logger = LoggerFactory.getLogger(MonitorDelayServiceImpl.class);

    private ILinkDiscoveryService linkDiscoveryService;
    private Map<LinkEntry<NodePortTuple, NodePortTuple>, Integer> linkDelaySecMap;

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
        //kwmtodo: caution that the method should be the call when there exits the actual links;
        while (testFunc() == 0){
            Time.sleep(5);
        }
    }


    //kwmtodo:Understand the Test.
    public int testFunc(){
        Map<Link, LinkInfo> linkInfo = linkDiscoveryService.getLinks();
        if (linkInfo.isEmpty()){
            return 0;
        }else {
            Iterator<Entry<Link, LinkInfo>> iter = linkInfo.entrySet().iterator();
            while(iter.hasNext()){
                Entry<Link, LinkInfo> node = iter.next();
                System.out.println("=======================================");
                System.out.println("源交换机:"+node.getKey().getSrc().toString()+",源端口："+node.getKey().getSrcPort());
                System.out.println("目的交换机:"+node.getKey().getDst().toString()+",目的端口："+node.getKey().getDstPort());
                System.out.println("链路时延:"+node.getKey().getLatency().getValue()/8/1024);
                System.out.println("当前时延："+node.getValue().getCurrentLatency().getValue());
                System.out.println("=======================================");
            }
            return 1;
        }
    }

    @Override
    public Map<LinkEntry<NodePortTuple, NodePortTuple>, Integer> getLinkDelay() {
        //kwmtodo: what the unit of this U64 for delay?
        this.linkDelaySecMap.clear();
        Map<Link, LinkInfo> linksMap = linkDiscoveryService.getLinks();
        Iterator<Entry<Link, LinkInfo>> iterator = linksMap.entrySet().iterator();
        while (iterator.hasNext()){
            Entry<Link, LinkInfo> link = iterator.next();
            NodePortTuple src = new NodePortTuple(link.getKey().getSrc(),link.getKey().getSrcPort());
            NodePortTuple dst = new NodePortTuple(link.getKey().getDst(),link.getKey().getDstPort());
            LinkEntry<NodePortTuple,NodePortTuple> ansKey = new LinkEntry<NodePortTuple,NodePortTuple>(src,dst);
            if (null == link.getValue().getCurrentLatency()){
                logger.warn("link.getValue().getCurrentLatency() return null");
            }else {
                Integer ansValue = new Long(link.getValue().getCurrentLatency().getValue()).intValue();
            }
        }
        return this.linkDelaySecMap;
    }
}
