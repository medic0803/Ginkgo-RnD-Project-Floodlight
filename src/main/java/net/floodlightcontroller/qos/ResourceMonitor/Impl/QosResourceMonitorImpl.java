package net.floodlightcontroller.qos.ResourceMonitor.Impl;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.qos.ResourceMonitor.MonitorDelayService;
import net.floodlightcontroller.qos.ResourceMonitor.MonitorPkLossService;
import net.floodlightcontroller.qos.ResourceMonitor.QosResourceMonitor;
import net.floodlightcontroller.qos.ResourceMonitor.pojo.LinkEntry;
import net.floodlightcontroller.qos.ResourceMonitor.pojo.SwitchPortCounter;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

import java.util.*;

/**
 * sub services: IStatisticsService, MonitorDelayService, MonitorPkLossService
 * @author Michael Kang
 * @create 2021-01-29 下午 06:15
 */
public class QosResourceMonitorImpl implements QosResourceMonitor, IFloodlightModule {
    //kwmtodo 完成资源监视模块
    // 注册module
    private static IStatisticsService bandwidthService;
    private static MonitorDelayService delayService;
    private static ILinkDiscoveryService linkDiscoveryService;
    private static MonitorPkLossService pkLossService;

    private static Map<NodePortTuple,SwitchPortBandwidth> bandwidthMap;
    private static Map<LinkEntry<DatapathId, DatapathId>, Integer> linkDelaySecMap;
    private static Map<LinkEntry<DatapathId, DatapathId>, Double> pklossMap;

    /**
     *  bandwidth methods
     */
    @Override
    public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthMap() {
        bandwidthMap = bandwidthService.getBandwidthConsumption();
//        Iterator<Map.Entry<NodePortTuple,SwitchPortBandwidth>> iter = bandwidth.entrySet().iterator();
//        while (iter.hasNext()) {
//            Map.Entry<NodePortTuple,SwitchPortBandwidth> entry = iter.next();
//            NodePortTuple tuple  = entry.getKey();
//            SwitchPortBandwidth switchPortBand = entry.getValue();
//            System.out.print(tuple.getNodeId()+","+tuple.getPortId().getPortNumber()+",");
//            System.out.println(switchPortBand.getBitsPerSecondRx().getValue()/(8*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8*1024));
//        }
        return bandwidthMap;
    }

    @Override
    public void setBandwidthCollection(boolean collect) {
        bandwidthService.collectStatistics(collect);
    }

    /**
     * linkdelay services
     */
    @Override
    public Map<LinkEntry<DatapathId, DatapathId>, Integer> getLinkDelay() {
        linkDelaySecMap = delayService.getLinkDelay();
        return linkDelaySecMap;
    }

    /**
     * parket loss services
     */
    @Override
    public void setPkLossCollection(boolean collect) {
        pkLossService.collectStatistics(collect);
    }



    @Override
    public Map<LinkEntry<DatapathId,DatapathId>,Double> getPkLoss() {
        if (!pklossMap.isEmpty()){
            pklossMap.clear();
        }
        Set<Link> links = linkDiscoveryService.getLinks().keySet();
        Map<NodePortTuple, SwitchPortCounter> portStatsMap = new HashMap<>();
        portStatsMap.putAll(pkLossService.getPortStatsMap());
        for (Link linkEntry:links) {
            NodePortTuple headPortTuple = new NodePortTuple(linkEntry.getSrc(), linkEntry.getSrcPort());
            NodePortTuple tailPortTuple = new NodePortTuple(linkEntry.getDst(), linkEntry.getDstPort());
            LinkEntry<DatapathId,DatapathId> linkAsKey = new LinkEntry<>(headPortTuple.getNodeId(),tailPortTuple.getNodeId());
            U64 tx = portStatsMap.get(headPortTuple).getTx();
            U64 rx = portStatsMap.get(tailPortTuple).getRx();
            pklossMap.put(linkAsKey,countPkloss(tx.getValue(),rx.getValue()));
        }
        return pklossMap;
    }
    private double countPkloss(long send, long receive){
        return 1-(receive*1.0)/send;
    }


    /**
     * Floodlight Module skeleton
     */
    /**
     * Return the list of interfaces that this module implements.
     * All interfaces must inherit IFloodlightService
     * @return
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(QosResourceMonitor.class);
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
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(QosResourceMonitor.class, this);
        return m;
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
        l.add(IStatisticsService.class);
        l.add(MonitorDelayService.class);
        l.add(MonitorPkLossService.class);
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
        bandwidthService = context.getServiceImpl(IStatisticsService.class);
        delayService = context.getServiceImpl(MonitorDelayService.class);
        pkLossService = context.getServiceImpl(MonitorPkLossService.class);
        linkDiscoveryService = context.getServiceImpl(ILinkDiscoveryService.class);

        pklossMap = new HashMap<>();
        bandwidthMap = new HashMap<>();
        linkDelaySecMap = new HashMap<>();
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
        System.out.println("----------------QosResourceMonitor actived-------------------");
        this.setBandwidthCollection(true);
        this.setPkLossCollection(true);
        
    }
}
