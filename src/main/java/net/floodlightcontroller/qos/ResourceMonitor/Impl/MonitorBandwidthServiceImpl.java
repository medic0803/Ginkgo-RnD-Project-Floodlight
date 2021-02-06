package net.floodlightcontroller.qos.ResourceMonitor.Impl;

import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.qos.ResourceMonitor.MonitorBandwidthService;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.StatisticsCollector;
import net.floodlightcontroller.statistics.SwitchPortBandwidth;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 宽带获取模块
 * kwm：get方法使用的是Floodlight提供的service；
 * 在此模块中，它实现的一个线程调用了get方法，周期调用了此service方法更新bandwidth;
 * bandwith 包括所有路径的带宽。
 * @author Michael Kang
 * @create 2021-01-29 上午 10:53
 */
public class MonitorBandwidthServiceImpl implements MonitorBandwidthService, IFloodlightModule {

    private static final Logger log = LoggerFactory.getLogger(StatisticsCollector.class);

    //Floodlight最核心的service类，其他service类需要该类提供
    protected static IFloodlightProviderService floodlightProvider;
    //链路数据分析模块，已经由Floodlight实现了，我们只需要调用一下就可以，然后对结果稍做加工，便于我们自己使用
    protected static IStatisticsService statisticsService;
    //交换机相关的service,通过这个服务，我们可以获取所有的交换机，即DataPatmmh
    private static IOFSwitchService switchService;

    //Floodllight实现的线程池，当然我们也可以使用Java自带的，但推荐使用这个
    private static IThreadPoolService threadPoolService;
    //Future类，不明白的可以百度 Java现成future,其实C++11也有这个玩意了
    private static ScheduledFuture<?> portBandwidthCollector;

    //kwm: 自定义域
    //存放每条俩路的带宽使用情况
    private static Map<NodePortTuple,SwitchPortBandwidth> bandwidth;
    //搜集数据的周期
    private static final int PORT_BANDWIDTH_INTERVAL = 4;

    /**
     * 需要简单的换算
     * 根据 switchPortBand.getBitsPerSecondRx().getValue()/(8*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8*1024)
     */
    @Override
    public Map<NodePortTuple, SwitchPortBandwidth> getBandwidthMap() {
        bandwidth = statisticsService.getBandwidthConsumption();
        Iterator<Entry<NodePortTuple,SwitchPortBandwidth>> iter = bandwidth.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<NodePortTuple,SwitchPortBandwidth> entry = iter.next();
            NodePortTuple tuple  = entry.getKey();
            SwitchPortBandwidth switchPortBand = entry.getValue();
            System.out.print(tuple.getNodeId()+","+tuple.getPortId().getPortNumber()+",");
            System.out.println(switchPortBand.getBitsPerSecondRx().getValue()/(8*1024) + switchPortBand.getBitsPerSecondTx().getValue()/(8*1024));
        }
        return bandwidth;
    }

    //tell the module sys about the service
    //IMonitorBandwidthService
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(MonitorBandwidthService.class);
        return l;
    }

    //tell the module sys about the service
    //The getServiceImpls() call tells the module system that we are the class that provides the service.
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(MonitorBandwidthService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IStatisticsService.class);
        l.add(IOFSwitchService.class);
        l.add(IThreadPoolService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        statisticsService = context.getServiceImpl(IStatisticsService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        startCollectBandwidth();
    }

    //自定义的开始收集数据的方法，使用了线程池，定周期的执行
    private synchronized void  startCollectBandwidth() {
        portBandwidthCollector = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new GetBandwidthThread(), PORT_BANDWIDTH_INTERVAL, PORT_BANDWIDTH_INTERVAL, TimeUnit.SECONDS);
        log.warn("Statistics collection thread(s) started");
    }
    //自定义的线程类，在上面的方法中实例化，并被调用
    /**
     * Single thread for collecting switch statistics and
     * containing the reply.
     */
    private class GetBandwidthThread extends Thread implements Runnable  {
        private Map<NodePortTuple,SwitchPortBandwidth> bandwidth;

        public Map<NodePortTuple, SwitchPortBandwidth> getBandwidth() {
            return bandwidth;
        }

//      public void setBandwidth(Map<NodePortTuple, SwitchPortBandwidth> bandwidth) {
//          this.bandwidth = bandwidth;
//      }

        @Override
        public void run() {
            System.out.println("GetBandwidthThread run()....");
            bandwidth =getBandwidthMap();
            System.out.println("bandwidth.size():"+bandwidth.size());
        }
    }


}
