package net.floodlightcontroller.qos.ResourceMonitor.Impl;

import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ListenableFuture;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.qos.ResourceMonitor.MonitorPkLossService;
import net.floodlightcontroller.statistics.IStatisticsService;
import net.floodlightcontroller.statistics.StatisticsCollector;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.ver13.OFMeterSerializerVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Map.Entry;

import java.lang.Thread.State;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 04:14
 */
public class MonitorPkLossServiceImpl implements MonitorPkLossService, IFloodlightModule, IOFMessageListener {
    private static final Logger log = LoggerFactory.getLogger(StatisticsCollector.class);

    private static HashMap<NodePortTuple, Long> DPID_PK_LOSS = new HashMap<NodePortTuple, Long>();

    protected static IFloodlightProviderService floodlightProvider;
    protected static IStatisticsService statisticsService;
    private static IOFSwitchService switchService;
    private static IThreadPoolService threadPoolService;
    private static ScheduledFuture<?> portStatsCollector;

    private static int portStatsInterval = 4;
    /**
     * This is the method Floodlight uses to call listeners with OpenFlow messages
     *
     * @param sw   the OpenFlow switch that sent this message
     * @param msg  the message
     * @param cntx a Floodlight message context object you can use to pass
     *             information between listeners
     * @return the command to continue or stop the execution
     */
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
//      Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
//      Long sourceMACHash = eth.getSourceMACAddress().getLong();
//      OFFactory factory = sw.getOFFactory();
        /**
         * 获取丢包率
         */
//      if(msg.getType() == OFType.STATS_REPLY){
//          OFStatsReply reply = (OFStatsReply) msg;
//          OFPortStatsReply psr = (OFPortStatsReply) reply;
//          OFPortStatsEntry pse = (OFPortStatsEntry) psr;
//          System.out.println("rx bytes:"+pse.getRxBytes().getValue());
//          System.out.println("rx_dropped bytes:"+pse.getRxDropped().getValue());
//          System.out.println("tx bytes:"+pse.getTxBytes().getValue());
//          System.out.println("tx_dropped bytes:"+pse.getTxDropped().getValue());
//      }
        return Command.CONTINUE;
    }

    /**
     * The name assigned to this listener
     *
     * @return
     */
    @Override
    public String getName() {
        return "pklossMonitor";
    }

    /**
     * Check if the module called name is a callback ordering prerequisite
     * for this module.  In other words, if this function returns true for
     * the given name, then this listener will be called after that
     * message listener.
     *
     * @param type the object type to which this applies
     * @param name the name of the module
     * @return whether name is a prerequisite.
     */
    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    /**
     * Check if the module called name is a callback ordering post-requisite
     * for this module.  In other words, if this function returns true for
     * the given name, then this listener will be called before that
     * message listener.
     *
     * @param type the object type to which this applies
     * @param name the name of the module
     * @return whether name is a post-requisite.
     */
    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
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
        l.add(MonitorPkLossService.class);
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
        m.put(MonitorPkLossService.class, this);
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
        l.add(IFloodlightProviderService.class);
        l.add(IStatisticsService.class);
        l.add(IOFSwitchService.class);
        l.add(IThreadPoolService.class);
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
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        statisticsService = context.getServiceImpl(IStatisticsService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
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
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        startStatisticsCollection();
    }
    /**
     * Start all stats threads.
     */
    private synchronized void startStatisticsCollection() {
        portStatsCollector = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new PortStatsCollector(), portStatsInterval, portStatsInterval, TimeUnit.SECONDS);
        log.warn("Statistics collection thread(s) started");
    }

    /**
     * Single thread for collecting switch statistics and
     * containing the reply.
     */
    private class GetStatisticsThread extends Thread {
        private List<OFStatsReply> statsReply;
        private DatapathId switchId;
        private OFStatsType statType;

        public GetStatisticsThread(DatapathId switchId, OFStatsType statType) {
            this.switchId = switchId;
            this.statType = statType;
            this.statsReply = null;
        }

        public List<OFStatsReply> getStatisticsReply() {
            return statsReply;
        }

        public DatapathId getSwitchId() {
            return switchId;
        }

        @Override
        public void run() {
//          System.out.println("run............");
            statsReply = getSwitchStatistics(switchId, statType);
        }
    }

    /**
     * Get statistics from a switch.
     * @param switchId
     * @param statsType
     * @return
     */
    @SuppressWarnings("unchecked")
    protected List<OFStatsReply> getSwitchStatistics(DatapathId switchId, OFStatsType statsType) {
        IOFSwitch sw = switchService.getSwitch(switchId);
//      System.out.println("getSwitchStatistics............");
        ListenableFuture<?> future;
        List<OFStatsReply> values = null;
        Match match;
        if (sw != null) {
            OFStatsRequest<?> req = null;
            switch (statsType) {
                case FLOW:
                    match = sw.getOFFactory().buildMatch().build();
                    req = sw.getOFFactory().buildFlowStatsRequest()
                            .setMatch(match)
                            .setOutPort(OFPort.ANY)
                            .setTableId(TableId.ALL)
                            .build();
                    break;
                case AGGREGATE:
                    match = sw.getOFFactory().buildMatch().build();
                    req = sw.getOFFactory().buildAggregateStatsRequest()
                            .setMatch(match)
                            .setOutPort(OFPort.ANY)
                            .setTableId(TableId.ALL)
                            .build();
                    break;
                case PORT:
                    req = sw.getOFFactory().buildPortStatsRequest()
                            .setPortNo(OFPort.ANY)
                            .build();
                    break;
                case QUEUE:
                    req = sw.getOFFactory().buildQueueStatsRequest()
                            .setPortNo(OFPort.ANY)
                            .setQueueId(UnsignedLong.MAX_VALUE.longValue())
                            .build();
                    break;
                case DESC:
                    req = sw.getOFFactory().buildDescStatsRequest()
                            .build();
                    break;
                case GROUP:
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                        req = sw.getOFFactory().buildGroupStatsRequest()
                                .build();
                    }
                    break;

                case METER:
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
                        req = sw.getOFFactory().buildMeterStatsRequest()
                                .setMeterId(OFMeterSerializerVer13.ALL_VAL)
                                .build();
                    }
                    break;

                case GROUP_DESC:
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                        req = sw.getOFFactory().buildGroupDescStatsRequest()
                                .build();
                    }
                    break;

                case GROUP_FEATURES:
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                        req = sw.getOFFactory().buildGroupFeaturesStatsRequest()
                                .build();
                    }
                    break;

                case METER_CONFIG:
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
                        req = sw.getOFFactory().buildMeterConfigStatsRequest()
                                .build();
                    }
                    break;

                case METER_FEATURES:
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
                        req = sw.getOFFactory().buildMeterFeaturesStatsRequest()
                                .build();
                    }
                    break;

                case TABLE:
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                        req = sw.getOFFactory().buildTableStatsRequest()
                                .build();
                    }
                    break;

                case TABLE_FEATURES:
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) > 0) {
                        req = sw.getOFFactory().buildTableFeaturesStatsRequest()
                                .build();
                    }
                    break;
                case PORT_DESC:
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_13) >= 0) {
                        req = sw.getOFFactory().buildPortDescStatsRequest()
                                .build();
                    }
                    break;
                case EXPERIMENTER:
                default:
                    log.error("Stats Request Type {} not implemented yet", statsType.name());
                    break;
            }
            try {
                if (req != null) {
                    future = sw.writeStatsRequest(req);
                    values = (List<OFStatsReply>) future.get(portStatsInterval / 2, TimeUnit.SECONDS);
                }
            } catch (Exception e) {
                log.error("Failure retrieving statistics from switch {}. {}", sw, e);
            }
        }
        return values;
    }
    /**
     * Retrieve the statistics from all switches in parallel.
     * @param dpids
     * @param statsType
     * @return
     */
    private Map<DatapathId, List<OFStatsReply>> getSwitchStatistics(Set<DatapathId> dpids, OFStatsType statsType) {
        HashMap<DatapathId, List<OFStatsReply>> model = new HashMap<DatapathId, List<OFStatsReply>>();

        List<GetStatisticsThread> activeThreads = new ArrayList<GetStatisticsThread>(dpids.size());
        List<GetStatisticsThread> pendingRemovalThreads = new ArrayList<GetStatisticsThread>();
        GetStatisticsThread t;
        for (DatapathId d : dpids) {
            t = new GetStatisticsThread(d, statsType);
            activeThreads.add(t);
            t.start();
        }

        /* Join all the threads after the timeout. Set a hard timeout
         * of 12 seconds for the threads to finish. If the thread has not
         * finished the switch has not replied yet and therefore we won't
         * add the switch's stats to the reply.
         */
        for (int iSleepCycles = 0; iSleepCycles < portStatsInterval; iSleepCycles++) {
            for (GetStatisticsThread curThread : activeThreads) {
                if (curThread.getState() == State.TERMINATED) {
                    model.put(curThread.getSwitchId(), curThread.getStatisticsReply());
                    pendingRemovalThreads.add(curThread);
                }
            }

            /* remove the threads that have completed the queries to the switches */
            for (GetStatisticsThread curThread : pendingRemovalThreads) {
                activeThreads.remove(curThread);
            }

            /* clear the list so we don't try to double remove them */
            pendingRemovalThreads.clear();

            /* if we are done finish early */
            if (activeThreads.isEmpty()) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for statistics", e);
            }
        }

        return model;
    }

    /**
     * Run periodically to collect all port statistics. This only collects
     * bandwidth stats right now, but it could be expanded to record other
     * information as well. The difference between the most recent and the
     * current RX/TX bytes is used to determine the "elapsed" bytes. A
     * timestamp is saved each time stats results are saved to compute the
     * bits per second over the elapsed time. There isn't a better way to
     * compute the precise bandwidth unless the switch were to include a
     * timestamp in the stats reply message, which would be nice but isn't
     * likely to happen. It would be even better if the switch recorded
     * bandwidth and reported bandwidth directly.
     *
     * Stats are not reported unless at least two iterations have occurred
     * for a single switch's reply. This must happen to compare the byte
     * counts and to get an elapsed time.
     *
     */
    private class PortStatsCollector implements Runnable {
        @Override
        public void run() {
//          System.out.println("Runnable run()....");
            Map<DatapathId, List<OFStatsReply>> replies = getSwitchStatistics(switchService.getAllSwitchDpids(), OFStatsType.PORT);
//          System.out.println("replies.size():"+replies.size());
            for (Entry<DatapathId, List<OFStatsReply>> e : replies.entrySet()) {
                for (OFStatsReply r : e.getValue()) {
                    OFPortStatsReply psr = (OFPortStatsReply) r;
                    for (OFPortStatsEntry pse : psr.getEntries()) {
//                      System.out.println("dpid:"+e.getKey().toString());
//                      System.out.println("for (OFPortStatsEntry pse : psr.getEntries())");

                        long pk_loss = 0;

                        if(e.getKey().toString().equals("") || e.getKey() == null){
//                          System.out.println("e.getKey() is null....");
                        }
//                      System.out.println("--------------------------------------------");
                        NodePortTuple npt = new NodePortTuple(e.getKey(), pse.getPortNo());
//                      System.out.println("--------------------------------------------");
//                      System.out.println(pse.getRxDropped().getValue() + pse.getTxDropped().getValue());
//                      System.out.println(pse.getRxBytes().getValue() + pse.getTxBytes().getValue());
                        if((pse.getRxBytes().getValue() + pse.getTxBytes().getValue()) != 0l){
                            pk_loss = (pse.getRxDropped().getValue() + pse.getTxDropped().getValue())/(pse.getRxBytes().getValue() + pse.getTxBytes().getValue()) ;
                        }else{
                            pk_loss = 0;
                        }

//                      System.out.println("PK_LOSS:"+pk_loss+",dpid:"+e.getKey().toString());
                        DPID_PK_LOSS.put(npt, pk_loss);
                    }
                }
            }
        }
    }

}
