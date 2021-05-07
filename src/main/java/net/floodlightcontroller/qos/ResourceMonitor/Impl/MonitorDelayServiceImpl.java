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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 06:03
 */
public class MonitorDelayServiceImpl implements MonitorDelayService, IFloodlightModule{

    private static final Logger logger = LoggerFactory.getLogger(MonitorDelayServiceImpl.class);
    private ILinkDiscoveryService linkDiscoveryService;
    private ConcurrentHashMap<String, Integer> linkDelaySecMap;
    private ConcurrentHashMap<String, Integer> linkJitterSecMap;

    private Map<String, Integer> first_JitterStampMap;
    private Map<String, Integer> sceond_JitterStampMap;

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
        linkDelaySecMap =  new ConcurrentHashMap<>();
        linkJitterSecMap = new ConcurrentHashMap<>();
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
        //kwm: caution that the method should be the call when there exits the actual links;
//kwmtodo: reserve the x test function
//        while (testFunc() == 0){
//             Time.sleep(5);
//        }
//        System.out.println("=========休息十秒=============");
//        Time.sleep(10);
//        int x  = 3;
//        while (x-- >0){
//            testFunc();
//        }

    }
//kwmtodo: 有两种get延迟的方法, 记录实验结果到日志中。
//    public int testFunc(){
//        Map<Link, LinkInfo> linkInfo = linkDiscoveryService.getLinks();
//        if (linkInfo.isEmpty()){
//            return 0;
//        }else {
//            Iterator<Entry<Link, LinkInfo>> iter = linkInfo.entrySet().iterator();
//            while(iter.hasNext()){
//                Entry<Link, LinkInfo> node = iter.next();
//                System.out.println("=======================================");
//                System.out.println("源交换机:"+node.getKey().getSrc().toString()+",源端口："+node.getKey().getSrcPort());
//                System.out.println("目的交换机:"+node.getKey().getDst().toString()+",目的端口："+node.getKey().getDstPort());
//                System.out.println("链路时延:"+node.getKey().getLatency().getValue()+"ms");
//                System.out.println("当前时延："+node.getValue().getCurrentLatency().getValue()+"ms");
//                System.out.println("=======================================");
//            }
//            return 1;
//        }
//    }

    @Override
    public ConcurrentHashMap<String, Integer> getLinkDelay() {
        if (!this.linkDelaySecMap.isEmpty()){
            this.linkDelaySecMap.clear();
        }
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
                linkDelaySecMap.put(ansKey.toString(),ansValue);
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
            if (linkJitterSecMap.isEmpty()){
                initialLinkJitterMap();
            }else{
                //获取第二个stamp
                sceond_JitterStampMap = getLinkDelay();
                //两个stamp 有效
                if (first_JitterStampMap.size() == sceond_JitterStampMap.size()){
                    //计算jitter
                    Iterator<String> iterator = first_JitterStampMap.keySet().iterator();
                    while (iterator.hasNext()){
                        String key = iterator.next();
                        linkJitterSecMap.put(key,Math.abs(first_JitterStampMap.get(key) - sceond_JitterStampMap.get(key)));
                    }
                }
                //更新firstStamp
                first_JitterStampMap = sceond_JitterStampMap;
            }
        }
    }
    private void initialLinkJitterMap(){
        this.first_JitterStampMap = getLinkDelay();
    }

    @Override
    public ConcurrentHashMap<String, Integer> getLinkJitter() {
        return this.linkJitterSecMap;
    }
}
