package net.floodlightcontroller.qos.ResourceMonitor.Impl;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.qos.ResourceMonitor.ExampleBandwidth;
import net.floodlightcontroller.qos.ResourceMonitor.MonitorDelayService;
import net.floodlightcontroller.qos.ResourceMonitor.MonitorPkLossService;
import net.floodlightcontroller.qos.ResourceMonitor.QosResourceMonitor;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;

import java.util.*;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 06:15
 */
public class QosResourceMonitorImpl implements QosResourceMonitor, IFloodlightModule {
    /**
     * fixme: 这里的成员声明为什么是protected
     * todo: 完成资源监视模块
     */
    protected static IStatisticsService bandwidthStatus;
    protected static MonitorDelayService DelayStatus;
    protected static MonitorPkLossService PkLossStatus;

    //存放每条俩路的带宽使用情况
    private static Map<NodePortTuple,SwitchPortBandwidth> bandwidth;

    @Override
    public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthMap() {
        bandwidth = bandwidthStatus.getBandwidthConsumption();
//        Iterator<Map.Entry<NodePortTuple,SwitchPortBandwidth>> iter = bandwidth.entrySet().iterator();
//        while (iter.hasNext()) {
//            Map.Entry<NodePortTuple,SwitchPortBandwidth> entry = iter.next();
//            NodePortTuple tuple  = entry.getKey();
//            SwitchPortBandwidth switchPortBand = entry.getValue();
//            System.out.print(tuple.getNodeId()+","+tuple.getPortId().getPortNumber()+",");
//            System.out.println(switchPortBand.getBitsPerSecondRx().getValue()/(8*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8*1024));
//        }
        return bandwidth;
    }

    /**
     * Return the list of interfaces that this module implements.
     * All interfaces must inherit IFloodlightService
     *
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
        bandwidthStatus = context.getServiceImpl(IStatisticsService.class);
        DelayStatus = context.getServiceImpl(MonitorDelayService.class);
        PkLossStatus = context.getServiceImpl(MonitorPkLossService.class);
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
    }
}
