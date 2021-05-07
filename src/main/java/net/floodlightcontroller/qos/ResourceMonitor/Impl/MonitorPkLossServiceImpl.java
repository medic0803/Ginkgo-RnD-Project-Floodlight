package net.floodlightcontroller.qos.ResourceMonitor.Impl;

import com.google.common.primitives.UnsignedLong;
import com.google.common.util.concurrent.ListenableFuture;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.qos.ResourceMonitor.MonitorPkLossService;
import net.floodlightcontroller.qos.ResourceMonitor.pojo.SwitchPortCounter;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.ver13.OFMeterSerializerVer13;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TableId;
import org.projectfloodlight.openflow.types.U64;
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
public class MonitorPkLossServiceImpl implements IFloodlightModule,MonitorPkLossService{

    private static final Logger log = LoggerFactory.getLogger(MonitorPkLossServiceImpl.class);

    private static IOFSwitchService switchService;
    private static IThreadPoolService threadPoolService;

    //kwmtodo: need method to call the service to run
    private static boolean isEnabled = false;

    private static final int portStatsInterval = 10;
    private static ScheduledFuture<?> portStatsCollector;

    private static final String INTERVAL_PORT_STATS_STR = "collectionIntervalPortStatsSeconds";
    private static final String ENABLED_STR = "enable";

    private static final HashMap<NodePortTuple, SwitchPortCounter> portStats = new HashMap<NodePortTuple, SwitchPortCounter>();
    private static final HashMap<NodePortTuple, SwitchPortCounter> tentativePortStats = new HashMap<NodePortTuple, SwitchPortCounter>();


    protected class PortStatsCollector implements Runnable {

        @Override
        public void run() {
            Map<DatapathId, List<OFStatsReply>> replies = getSwitchStatistics(switchService.getAllSwitchDpids(), OFStatsType.PORT);
            this.handleAllSwitchReplies(replies);
        }

        private void handleAllSwitchReplies( Map<DatapathId, List<OFStatsReply>> replies ){
            for (Entry<DatapathId, List<OFStatsReply>> switchPortsReplyEntry : replies.entrySet()) {
                for (OFStatsReply switchPortStatsReplies : switchPortsReplyEntry.getValue()) {
                    DatapathId switchId = switchPortsReplyEntry.getKey();
                    OFPortStatsReply switchPortsStatsReplies = (OFPortStatsReply) switchPortStatsReplies;
                    handleSingleSwitchReply(switchId,switchPortsStatsReplies);
                }
            }
        }
        private void handleSingleSwitchReply(DatapathId switchId,
                                             OFPortStatsReply switchPortsStatsReplies){
            for (OFPortStatsEntry switchPortReply : switchPortsStatsReplies.getEntries()) {
                handleSinglePortReply(switchId,switchPortReply);
            }
        }

        private void handleSinglePortReply(DatapathId switchId, OFPortStatsEntry switchPortReply){
            NodePortTuple npt = new NodePortTuple(switchId, switchPortReply.getPortNo());
            if (alreadySeeTheNodePort(npt)) {
                SwitchPortCounter switchPortCounter = getSwitchPortPkloss(npt);
                secondOperation(npt,switchPortReply, switchPortCounter);
            } else {
                initialAndFirstRecord(npt,switchPortReply);
            }
        }

        private SwitchPortCounter getSwitchPortPkloss(NodePortTuple npt){
            SwitchPortCounter switchPortCounter = new SwitchPortCounter();
            if (needUpdate(npt)) { switchPortCounter = portStats.get(npt);}
            if (isToPutFirstValue(npt)) {
                switchPortCounter = tentativePortStats.get(npt);
                tentativePortStats.remove(npt);
            } else {
                log.error("Inconsistent state between tentative and official port stats lists.");
            }
            return switchPortCounter;
        }
        private boolean alreadySeeTheNodePort(NodePortTuple npt){
            return (portStats.containsKey(npt) || tentativePortStats.containsKey(npt));
        }
        private boolean needUpdate(NodePortTuple npt){
            return portStats.containsKey(npt);
        }
        private boolean isToPutFirstValue(NodePortTuple npt){
            return tentativePortStats.containsKey(npt);
        }

        /**
         * Get counted bytes over the elapsed period.
         * @param npt
         * @param switchPortReply
         * @param switchPortCounter
         */
        private void secondOperation(NodePortTuple npt, OFPortStatsEntry switchPortReply, SwitchPortCounter switchPortCounter){

            U64 rxCounted = count(switchPortCounter.getPriorByteValueRx(),switchPortReply.getRxBytes());
            U64 txCounted = count(switchPortCounter.getPriorByteValueTx(),switchPortReply.getTxBytes());

            portStats.put(npt, SwitchPortCounter.of(
                    npt.getNodeId(),
                    npt.getPortId(),
                    switchPortReply.getRxBytes(),rxCounted,
                    switchPortReply.getTxBytes(), txCounted)
            );
        }
        private U64 count(U64 x ,U64 y){
            /* Check for counter overflow. */
            U64 answer = U64.of(0);
            if (x.compareTo(y) > 0) { /* overflow */
                U64 upper = U64.NO_MASK.subtract(x);
                U64 lower = y;
                answer = upper.add(lower);
            } else {
                answer = y.subtract(x);
            }
            return answer;
        }

        private void initialAndFirstRecord(NodePortTuple npt,OFPortStatsEntry switchPortReply){
            tentativePortStats.put(npt, SwitchPortCounter.of(
                    npt.getNodeId(),
                    npt.getPortId(),
                    switchPortReply.getRxBytes(),U64.of(0),
                    switchPortReply.getTxBytes(),U64.of(0)));
        }
    }

    /**
     * Single thread for collecting switch statistics and
     * containing the reply.
     *
     * @author Ryan Izard, ryan.izard@bigswitch.com, rizard@g.clemson.edu
     *
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
            statsReply = getSwitchStatistics(switchId, statType);
        }
    }

    /*
     * IFloodlightModule implementation
     */

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(MonitorPkLossService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m =
                new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(MonitorPkLossService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IOFSwitchService.class);
        l.add(IThreadPoolService.class);
        l.add(IRestApiService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context)
            throws FloodlightModuleException {
        switchService = context.getServiceImpl(IOFSwitchService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);

        log.info("Port Qos Parket Loss collection interval set to {}s", portStatsInterval);
    }

    @Override
    public void startUp(FloodlightModuleContext context)
            throws FloodlightModuleException {
//        restApiService.addRestletRoutable(new SwitchStatisticsWebRoutable());
        if (isEnabled) {
            startStatisticsCollection();
        }
    }



    @Override
    public Map<NodePortTuple, SwitchPortCounter> getPortStatsMap() {
        return Collections.unmodifiableMap(portStats);
    }

    @Override
    public synchronized void collectStatistics(boolean collect) {
        if (collect && !isEnabled) {
            startStatisticsCollection();
            isEnabled = true;
        } else if (!collect && isEnabled) {
            stopStatisticsCollection();
            isEnabled = false;
        }
        /* otherwise, state is not changing; no-op */
    }

    /*
     * Helper functions
     */

    /**
     * Start all stats threads.
     */
    private void startStatisticsCollection() {
        portStatsCollector = threadPoolService.getScheduledExecutor().scheduleAtFixedRate(new PortStatsCollector(), portStatsInterval, portStatsInterval, TimeUnit.SECONDS);
        tentativePortStats.clear(); /* must clear out, otherwise might have huge BW result if present and wait a long time before re-enabling stats */
        log.warn("Statistics collection thread(s) started");
    }

    /**
     * Stop all stats threads.
     */
    private void stopStatisticsCollection() {
        if (!portStatsCollector.cancel(false)) {
            log.error("Could not cancel port stats thread");
        } else {
            log.warn("Statistics collection thread(s) stopped");
        }
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
     * Get statistics from a switch.
     * @param switchId
     * @param statsType
     * @return
     */
    @SuppressWarnings("unchecked")
    protected List<OFStatsReply> getSwitchStatistics(DatapathId switchId, OFStatsType statsType) {
        IOFSwitch sw = switchService.getSwitch(switchId);
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
                    values = (List<OFStatsReply>) future.get(portStatsInterval*1000 / 2, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                log.error("Failure retrieving statistics from switch {}. {}", sw, e);
            }
        }
        return values;
    }
}
