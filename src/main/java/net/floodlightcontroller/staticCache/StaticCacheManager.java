package net.floodlightcontroller.staticCache;

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
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.firewall.FirewallRule;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.qos.ResourceMonitor.QosResourceMonitor;
import net.floodlightcontroller.qos.ResourceMonitor.pojo.LinkEntry;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.routing.RoutingDecision;
import net.floodlightcontroller.staticCache.web.StaticCacheStrategy;
import net.floodlightcontroller.staticCache.web.StaticCacheWebRoutable;
import net.floodlightcontroller.util.*;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionSetQueue;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.floodlightcontroller.routing.ForwardingBase.FORWARDING_APP_ID;


public class StaticCacheManager implements IOFMessageListener, IFloodlightModule, IStaticCacheService {

    // instance fied
    // Floodlight Service
    protected IFloodlightProviderService floodlightProvider;
    protected IOFSwitchService switchService;
    protected IRestApiService restApi;
    protected IDeviceService deviceService;
    protected IRoutingService routingEngineService;
    protected List<StaticCacheStrategy> strategies;
    protected OFMessageDamper messageDamper;
    protected QosResourceMonitor qosResourceMonitor;
    protected static Logger log = LoggerFactory.getLogger(StaticCacheManager.class);

    // FlowMod constants
    public static int FLOWMOD_DEFAULT_IDLE_TIMEOUT = 5; // in seconds
    public static int FLOWMOD_DEFAULT_HARD_TIMEOUT = 0; // infinite
    public static int FLOWMOD_DEFAULT_PRIORITY = 1; // 0 is the default table-miss flow in OF1.3+, so we need to use 1

    protected static TableId FLOWMOD_DEFAULT_TABLE_ID = TableId.ZERO;

    protected static boolean FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG = false;

    protected static boolean FLOWMOD_DEFAULT_MATCH_IN_PORT = true;
    protected static boolean FLOWMOD_DEFAULT_MATCH_VLAN = true;
    protected static boolean FLOWMOD_DEFAULT_MATCH_MAC = true;
    protected static boolean FLOWMOD_DEFAULT_MATCH_IP = true;
    protected static boolean FLOWMOD_DEFAULT_MATCH_TRANSPORT = true;

    protected static boolean FLOWMOD_DEFAULT_MATCH_MAC_SRC = true;
    protected static boolean FLOWMOD_DEFAULT_MATCH_MAC_DST = true;
    protected static boolean FLOWMOD_DEFAULT_MATCH_IP_SRC = true;
    protected static boolean FLOWMOD_DEFAULT_MATCH_IP_DST = true;
    protected static boolean FLOWMOD_DEFAULT_MATCH_TRANSPORT_SRC = true;
    protected static boolean FLOWMOD_DEFAULT_MATCH_TRANSPORT_DST = true;
    protected static boolean FLOWMOD_DEFAULT_MATCH_TCP_FLAG = true;

    protected static boolean FLOOD_ALL_ARP_PACKETS = false;

    protected static boolean REMOVE_FLOWS_ON_LINK_OR_PORT_DOWN = true;

    protected static FlowSetIdRegistry flowSetIdRegistry;

    private static final short DECISION_BITS = 24;
    private static final short DECISION_SHIFT = 0;
    private static final long DECISION_MASK = ((1L << DECISION_BITS) - 1) << DECISION_SHIFT;

    private static final short FLOWSET_BITS = 28;
    protected static final short FLOWSET_SHIFT = DECISION_BITS;
    private static final long FLOWSET_MASK = ((1L << FLOWSET_BITS) - 1) << FLOWSET_SHIFT;
    private static final long FLOWSET_MAX = (long) (Math.pow(2, FLOWSET_BITS) - 1);

    protected static final U64 DEFAULT_FORWARDING_COOKIE = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

    private static int OFMESSAGE_DAMPER_CAPACITY = 10000;
    private static int OFMESSAGE_DAMPER_TIMEOUT = 250; // ms

    protected static class FlowSetIdRegistry {
        private volatile Map<NodePortTuple, Set<U64>> nptToFlowSetIds;
        private volatile Map<U64, Set<NodePortTuple>> flowSetIdToNpts;

        private volatile long flowSetGenerator = -1;

        private static volatile FlowSetIdRegistry instance;

        private FlowSetIdRegistry() {
            nptToFlowSetIds = new ConcurrentHashMap<>();
            flowSetIdToNpts = new ConcurrentHashMap<>();
        }

        protected static FlowSetIdRegistry getInstance() {
            if (instance == null) {
                instance = new FlowSetIdRegistry();
            }
            return instance;
        }

        /**
         * Only for use by unit test to help w/ordering
         *
         * @param seed
         */
        protected void seedFlowSetIdForUnitTest(int seed) {
            flowSetGenerator = seed;
        }

        protected synchronized U64 generateFlowSetId() {
            flowSetGenerator += 1;
            if (flowSetGenerator == FLOWSET_MAX) {
                flowSetGenerator = 0;
                log.warn("Flowset IDs have exceeded capacity of {}. Flowset ID generator resetting back to 0", FLOWSET_MAX);
            }
            U64 id = U64.of(flowSetGenerator << FLOWSET_SHIFT);
            log.debug("Generating flowset ID {}, shifted {}", flowSetGenerator, id);
            return id;
        }

        private void registerFlowSetId(NodePortTuple npt, U64 flowSetId) {
            if (nptToFlowSetIds.containsKey(npt)) {
                Set<U64> ids = nptToFlowSetIds.get(npt);
                ids.add(flowSetId);
            } else {
                Set<U64> ids = new HashSet<>();
                ids.add(flowSetId);
                nptToFlowSetIds.put(npt, ids);
            }

            if (flowSetIdToNpts.containsKey(flowSetId)) {
                Set<NodePortTuple> npts = flowSetIdToNpts.get(flowSetId);
                npts.add(npt);
            } else {
                Set<NodePortTuple> npts = new HashSet<>();
                npts.add(npt);
                flowSetIdToNpts.put(flowSetId, npts);
            }
        }

        private Set<U64> getFlowSetIds(NodePortTuple npt) {
            return nptToFlowSetIds.get(npt);
        }

        private Set<NodePortTuple> getNodePortTuples(U64 flowSetId) {
            return flowSetIdToNpts.get(flowSetId);
        }

        private void removeNodePortTuple(NodePortTuple npt) {
            nptToFlowSetIds.remove(npt);

            Iterator<Set<NodePortTuple>> itr = flowSetIdToNpts.values().iterator();
            while (itr.hasNext()) {
                Set<NodePortTuple> npts = itr.next();
                npts.remove(npt);
            }
        }

        private void removeExpiredFlowSetId(U64 flowSetId, NodePortTuple avoid, Iterator<U64> avoidItr) {
            flowSetIdToNpts.remove(flowSetId);

            Iterator<Map.Entry<NodePortTuple, Set<U64>>> itr = nptToFlowSetIds.entrySet().iterator();
            boolean removed = false;
            while (itr.hasNext()) {
                Map.Entry<NodePortTuple, Set<U64>> e = itr.next();
                if (e.getKey().equals(avoid) && !removed) {
                    avoidItr.remove();
                    removed = true;
                } else {
                    Set<U64> ids = e.getValue();
                    ids.remove(flowSetId);
                }
            }
        }
    }

    /**
     * Builds a cookie that includes routing decision information.
     *
     * @param decision The routing decision providing a descriptor, or null
     * @return A cookie with our app id and the required fields masked-in
     */
    protected U64 makeForwardingCookie(IRoutingDecision decision, U64 flowSetId) {
        long user_fields = 0;

        U64 decision_cookie = (decision == null) ? null : decision.getDescriptor();
        if (decision_cookie != null) {
            user_fields |= AppCookie.extractUser(decision_cookie) & DECISION_MASK;
        }

        if (flowSetId != null) {
            user_fields |= AppCookie.extractUser(flowSetId) & FLOWSET_MASK;
        }

        // TODO: Mask in any other required fields here

        if (user_fields == 0) {
            return DEFAULT_FORWARDING_COOKIE;
        }
        return AppCookie.makeCookie(FORWARDING_APP_ID, user_fields);
    }

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        switch (msg.getType()) {
            case PACKET_IN:
                OFPacketIn pi = (OFPacketIn) msg;
                if (eth.getEtherType() == EthType.IPv4) {
                    if (((IPv4) eth.getPayload()).getProtocol() == IpProtocol.TCP) {

                        IPv4Address srcAddress = ((IPv4) eth.getPayload()).getSourceAddress();
                        IPv4Address dstAddress = ((IPv4) eth.getPayload()).getDestinationAddress();
                        int tp_src = ((TCP) eth.getPayload().getPayload()).getSourcePort().getPort();
                        int tp_dst = ((TCP) eth.getPayload().getPayload()).getDestinationPort().getPort();

                        if (tp_dst == 80 || tp_dst == 8080 || tp_dst == 8081 || tp_dst == 9098) {
                            return process_http_from_host(srcAddress, dstAddress, tp_src, tp_dst, sw, pi, cntx, eth);
                        } else if (tp_src == 80 || tp_src == 8080 || tp_src == 8081 || tp_src == 9098) {
                            return process_http_from_cache(srcAddress, dstAddress, tp_src, tp_dst, sw, pi, cntx, eth);
                        }
                    }

                }
                break;
        }
        return Command.CONTINUE;
    }

    /**
     * The destination transport is already be determined as a HTTP packet,
     * which is then needed to be matched with a exist strategy to do the following processes,
     * e.g. getPath, pushRoute
     *
     * @param tp_src Transport for source
     * @param tp_dst Transport for destination
     * @return Command.STOP if match any strategy, otherwise return Command.CONTINUE
     */
    private Command process_http_from_host(IPv4Address srcAddress, IPv4Address dstAddress, int tp_src, int tp_dst, IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx, Ethernet eth) {
        StaticCacheStrategy matched_strategy = null;
        for (StaticCacheStrategy strategy : strategies) {
            StaticCacheStrategy temp_strategy = strategy.ifMatch(srcAddress, dstAddress, TransportPort.of(tp_dst), "HOST");
            if (temp_strategy != null) {
                if (matched_strategy == null) {
                    matched_strategy = temp_strategy;
                } else {    // matched_strategy !=null
                    if (temp_strategy.priority < matched_strategy.priority) {    // find a high priority strategy
                        matched_strategy = temp_strategy;
                    }
                }
            }
        }

        // Matched strategy exists
        if (matched_strategy != null) {
            log.info("match one strategy");
            matched_strategy.tp_src = TransportPort.of(tp_src);
            matched_strategy.src_dpid = sw.getId();
            matched_strategy.src_inPort = OFMessageUtils.getInPort(pi);

            OFActionSetQueue setQueue = getQueueAction(srcAddress, eth, sw);
            Map<LinkEntry<DatapathId, DatapathId>, Integer> linkDelay = qosResourceMonitor.getLinkDelay();
            Map<LinkEntry<DatapathId, DatapathId>, Integer> linkJitter = qosResourceMonitor.getLinkJitter();

            Path path_forward = routingEngineService.getPath(matched_strategy.src_dpid, matched_strategy.src_inPort, matched_strategy.dst_dpid, matched_strategy.dst_inPort, linkJitter, linkDelay, setQueue.getQueueId());

            U64 flowSetId = flowSetIdRegistry.generateFlowSetId();
            U64 cookie = makeForwardingCookie(RoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION), flowSetId);
            pushRoute(path_forward, pi, matched_strategy, sw.getId(), cookie, cntx, false, "HOST", setQueue);

            return Command.STOP;
        } else
            return Command.CONTINUE;
    }

    /**
     * The source transport is already be determined as a HTTP packet,
     * which is then needed to be matched with a exist strategy to do the following processes,
     * e.g. getPath, pushRoute
     *
     * @param tp_src Transport for source
     * @param tp_dst Transport for destination
     * @return Command.STOP if match any strategy, otherwise return Command.CONTINUE
     */
    private Command process_http_from_cache(IPv4Address srcAddress, IPv4Address dstAddress, int tp_src, int tp_dst, IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx, Ethernet eth) {

        StaticCacheStrategy matched_strategy = null;
        for (StaticCacheStrategy strategy : strategies) {
            StaticCacheStrategy temp_strategy = strategy.ifMatch(srcAddress, dstAddress, TransportPort.of(tp_dst), "CACHE");
            if (temp_strategy != null) {
                if (matched_strategy == null) {
                    matched_strategy = temp_strategy;
                } else {    // matched_strategy !=null
                    if (temp_strategy.priority < matched_strategy.priority) {    // find a high priority strategy
                        matched_strategy = temp_strategy;
                    }
                }
            }
        }

        // Matched strategy exists
        if (matched_strategy != null) {
            log.info("match one strategy");

            OFActionSetQueue setQueue = getQueueAction(srcAddress, eth, sw);
            Map<LinkEntry<DatapathId, DatapathId>, Integer> linkDelay = qosResourceMonitor.getLinkDelay();
            Map<LinkEntry<DatapathId, DatapathId>, Integer> linkJitter = qosResourceMonitor.getLinkJitter();
            Path path_inverse = routingEngineService.getPath(matched_strategy.dst_dpid, matched_strategy.dst_inPort, matched_strategy.src_dpid, matched_strategy.src_inPort, linkJitter, linkDelay, setQueue.getQueueId());

            U64 flowSetId = flowSetIdRegistry.generateFlowSetId();
            U64 cookie = makeForwardingCookie(RoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION), flowSetId);

            pushRoute(path_inverse, pi, matched_strategy, sw.getId(), cookie, cntx, false, "CACHE",setQueue);

            return Command.STOP;
        } else
            return Command.CONTINUE;
    }

    @Override
    public String getName() {
        return "staticCache";
    }

    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return (type.equals(OFType.PACKET_IN) && (name.equals("forwarding")));
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IStaticCacheService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IRestApiService.class);
        l.add(IOFSwitchService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        strategies = new ArrayList<StaticCacheStrategy>();
        restApi = context.getServiceImpl(IRestApiService.class);
        deviceService = context.getServiceImpl(IDeviceService.class);
        routingEngineService = context.getServiceImpl(IRoutingService.class);
        flowSetIdRegistry = FlowSetIdRegistry.getInstance();
        switchService = context.getServiceImpl(IOFSwitchService.class);
        qosResourceMonitor = context.getServiceImpl(QosResourceMonitor.class);
        messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
                EnumSet.of(OFType.FLOW_MOD),
                OFMESSAGE_DAMPER_TIMEOUT);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        restApi.addRestletRoutable(new StaticCacheWebRoutable());
    }

    /**
     * @param strategy A new strategy, which needed to be complemented some information
     */
    @Override
    public void addStrategy(StaticCacheStrategy strategy) {
        this.strategies.add(strategy);

        Collection<? extends IDevice> devices = deviceService.getAllDevices();
        for (IDevice device : devices) {
            for (IPv4Address srcAddress : device.getIPv4Addresses()) {
                if (srcAddress.equals(strategy.nw_cache_ipv4)) {
                    strategy.nw_cache_dl_dst = device.getMACAddress();
                    strategy.dst_inPort = device.getAttachmentPoints()[0].getPortId();
                    strategy.dst_dpid = device.getAttachmentPoints()[0].getNodeId();
                }
            }
        }
    }

    /**
     * @return strategies arraylist to present on the WEB UI
     */
    @Override
    public List<StaticCacheStrategy> getStrategies() {
        return this.strategies;
    }

    @Override
    public void deleteRule(int id) {
        Iterator<StaticCacheStrategy> iter = this.strategies.iterator();
        while (iter.hasNext()) {
            StaticCacheStrategy cacheStrategy = iter.next();
            if (cacheStrategy.strategyid == id) {
                // found the rule, now remove it
                iter.remove();
                break;
            }
        }
    }

    /**
     * Push routes from back to front
     *
     * @param route       Route to push
     * @param strategy    Matched strategy to write flow with flow item modification
     * @param cookie      The cookie to set in each flow_mod
     * @param cntx        The floodlight context
     * @param hostOrCache Determine which flowMod should be generated and write into the switch, host or cache
     * @param setQueue    OFActionSetQueue object which is used to be added into openflow action list, and write to flow table item
     * @return true if a packet out was sent on the first-hop switch of this route
     */
    public boolean pushRoute(Path route, OFPacketIn pi, StaticCacheStrategy strategy,
                             DatapathId pinSwitch, U64 cookie, FloodlightContext cntx,
                             boolean requestFlowRemovedNotification, String hostOrCache, OFActionSetQueue setQueue) {

        List<NodePortTuple> switchPortList = route.getPath();

        for (int indx = switchPortList.size() - 1; indx > 0; indx -= 2) {
            // indx and indx-1 will always have the same switch DPID.
            DatapathId switchDPID = switchPortList.get(indx).getNodeId();
            IOFSwitch sw = switchService.getSwitch(switchDPID);

            // set input and output ports on the switch
            OFPort outPort = switchPortList.get(indx).getPortId();
            OFPort inPort = switchPortList.get(indx - 1).getPortId();
            if (sw == null) {
                if (log.isWarnEnabled()) {
                    log.warn("Unable to push route, switch at DPID {} " + "not available", switchDPID);
                }
                return false;
            }
            if (pinSwitch.equals(switchDPID)) {
                switch (hostOrCache) {
                    case "HOST":
                        strategy.completeStrategy_host(sw, pi, outPort, setQueue);
                        sw.write(strategy.flowAdd_host);
                        log.info("Redirect the packet from " + strategy.nw_dst_ipv4 + " to " + strategy.nw_cache_ipv4);
                        break;
                    case "CACHE":
                        strategy.completeStrategy_cache(sw, pi, outPort, setQueue);
                        sw.write(strategy.flowAdd_cache);
                        log.info("Inverse proxy the packet from " + strategy.nw_dst_ipv4 + " to " + strategy.nw_cache_ipv4);
                        break;
                }
                pushPacket(sw, pi, outPort, true, cntx);

            } else {
                // need to build flow mod based on what type it is. Cannot set command later
                OFFlowMod.Builder fmb;
                fmb = sw.getOFFactory().buildFlowAdd();

                OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
                List<OFAction> actions = new ArrayList<>();

                Match match = createMatchFromPacket(sw, inPort, pi, cntx);
                Match.Builder mb = MatchUtils.convertToVersion(match, sw.getOFFactory().getVersion());


                if (FLOWMOD_DEFAULT_MATCH_IN_PORT) {
                    mb.setExact(MatchField.IN_PORT, inPort);
                }

                aob.setPort(outPort);
                aob.setMaxLen(Integer.MAX_VALUE);
                actions.add(setQueue);
                actions.add(aob.build());

                if (FLOWMOD_DEFAULT_SET_SEND_FLOW_REM_FLAG || requestFlowRemovedNotification) {
                    Set<OFFlowModFlags> flags = new HashSet<>();
                    flags.add(OFFlowModFlags.SEND_FLOW_REM);
                    fmb.setFlags(flags);
                }

                fmb.setMatch(mb.build())
                        .setIdleTimeout(FLOWMOD_DEFAULT_IDLE_TIMEOUT)
                        .setHardTimeout(FLOWMOD_DEFAULT_HARD_TIMEOUT)
                        .setBufferId(OFBufferId.NO_BUFFER)
                        .setCookie(cookie)
                        .setOutPort(outPort)
                        .setPriority(FLOWMOD_DEFAULT_PRIORITY);

                FlowModUtils.setActions(fmb, actions, sw);

                /* Configure for particular switch pipeline */
                if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_10) != 0) {
                    fmb.setTableId(FLOWMOD_DEFAULT_TABLE_ID);
                }

                if (log.isTraceEnabled()) {
                    log.trace("Pushing Route flowmod routeIndx={} " +
                                    "sw={} inPort={} outPort={}",
                            new Object[]{indx,
                                    sw,
                                    fmb.getMatch().get(MatchField.IN_PORT),
                                    outPort});
                }

                if (OFDPAUtils.isOFDPASwitch(sw)) {
                    OFDPAUtils.addLearningSwitchFlow(sw, cookie,
                            FLOWMOD_DEFAULT_PRIORITY,
                            FLOWMOD_DEFAULT_HARD_TIMEOUT,
                            FLOWMOD_DEFAULT_IDLE_TIMEOUT,
                            fmb.getMatch(),
                            null, // TODO how to determine output VLAN for lookup of L2 interface group
                            outPort);
                } else {
                    messageDamper.write(sw, fmb.build());
                }
            }
        }

        return true;
    }

    /**
     * Pushes a packet-out to a switch. The assumption here is that
     * the packet-in was also generated from the same switch. Thus, if the input
     * port of the packet-in and the outport are the same, the function will not
     * push the packet-out.
     *
     * @param sw                switch that generated the packet-in, and from which packet-out is sent
     * @param pi                packet-in
     * @param outport           output port
     * @param useBufferedPacket use the packet buffered at the switch, if possible
     * @param cntx              context of the packet
     */
    protected void pushPacket(IOFSwitch sw, OFPacketIn pi, OFPort outport, boolean useBufferedPacket, FloodlightContext cntx) {
        if (pi == null) {
            return;
        }

        // The assumption here is (sw) is the switch that generated the
        // packet-in. If the input port is the same as output port, then
        // the packet-out should be ignored.
        if ((pi.getVersion().compareTo(OFVersion.OF_12) < 0 ? pi.getInPort() : pi.getMatch().get(MatchField.IN_PORT)).equals(outport)) {
            if (log.isDebugEnabled()) {
                log.debug("Attempting to do packet-out to the same " +
                                "interface as packet-in. Dropping packet. " +
                                " SrcSwitch={}, pi={}",
                        new Object[]{sw, pi});
                return;
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("PacketOut srcSwitch={} pi={}",
                    new Object[]{sw, pi});
        }

        OFPacketOut.Builder pob = sw.getOFFactory().buildPacketOut();
        List<OFAction> actions = new ArrayList<>();
        actions.add(sw.getOFFactory().actions().output(outport, Integer.MAX_VALUE));
        pob.setActions(actions);

        /* Use packet in buffer if there is a buffer ID set */
        if (useBufferedPacket) {
            pob.setBufferId(pi.getBufferId()); /* will be NO_BUFFER if there isn't one */
        } else {
            pob.setBufferId(OFBufferId.NO_BUFFER);
        }

        if (pob.getBufferId().equals(OFBufferId.NO_BUFFER)) {
            byte[] packetData = pi.getData();
            pob.setData(packetData);
        }

        OFMessageUtils.setInPort(pob, OFMessageUtils.getInPort(pi));
        messageDamper.write(sw, pob.build());
    }

    /**
     * Instead of using the Firewall's routing decision Match, which might be as general
     * as "in_port" and inadvertently Match packets erroneously, construct a more
     * specific Match based on the deserialized OFPacketIn's payload, which has been
     * placed in the FloodlightContext already by the Controller.
     *
     * @param sw,     the switch on which the packet was received
     * @param inPort, the ingress switch port on which the packet was received
     * @param cntx,   the current context which contains the deserialized packet
     * @return a composed Match object based on the provided information
     */
    protected Match createMatchFromPacket(IOFSwitch sw, OFPort inPort, OFPacketIn pi, FloodlightContext cntx) {
        // The packet in match will only contain the port number.
        // We need to add in specifics for the hosts we're routing between.
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        VlanVid vlan = null;
        if (pi.getVersion().compareTo(OFVersion.OF_11) > 0 && /* 1.0 and 1.1 do not have a match */
                pi.getMatch().get(MatchField.VLAN_VID) != null) {
            vlan = pi.getMatch().get(MatchField.VLAN_VID).getVlanVid(); /* VLAN may have been popped by switch */
        }
        if (vlan == null) {
            vlan = VlanVid.ofVlan(eth.getVlanID()); /* VLAN might still be in packet */
        }

        MacAddress srcMac = eth.getSourceMACAddress();
        MacAddress dstMac = eth.getDestinationMACAddress();

        Match.Builder mb = sw.getOFFactory().buildMatch();
        if (FLOWMOD_DEFAULT_MATCH_IN_PORT) {
            mb.setExact(MatchField.IN_PORT, inPort);
        }

        if (FLOWMOD_DEFAULT_MATCH_MAC) {
            if (FLOWMOD_DEFAULT_MATCH_MAC_SRC) {
                mb.setExact(MatchField.ETH_SRC, srcMac);
            }
            if (FLOWMOD_DEFAULT_MATCH_MAC_DST) {
                mb.setExact(MatchField.ETH_DST, dstMac);
            }
        }

        if (FLOWMOD_DEFAULT_MATCH_VLAN) {
            if (!vlan.equals(VlanVid.ZERO)) {
                mb.setExact(MatchField.VLAN_VID, OFVlanVidMatch.ofVlanVid(vlan));
            }
        }

        // TODO Detect switch type and match to create hardware-implemented flow
        if (eth.getEtherType() == EthType.IPv4) { /* shallow check for equality is okay for EthType */
            IPv4 ip = (IPv4) eth.getPayload();
            IPv4Address srcIp = ip.getSourceAddress();
            IPv4Address dstIp = ip.getDestinationAddress();

            if (FLOWMOD_DEFAULT_MATCH_IP) {
                mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
                if (FLOWMOD_DEFAULT_MATCH_IP_SRC) {
                    mb.setExact(MatchField.IPV4_SRC, srcIp);
                }
                if (FLOWMOD_DEFAULT_MATCH_IP_DST) {
                    mb.setExact(MatchField.IPV4_DST, dstIp);
                }
            }

            if (FLOWMOD_DEFAULT_MATCH_TRANSPORT) {
                /*
                 * Take care of the ethertype if not included earlier,
                 * since it's a prerequisite for transport ports.
                 */
                if (!FLOWMOD_DEFAULT_MATCH_IP) {
                    mb.setExact(MatchField.ETH_TYPE, EthType.IPv4);
                }

                if (ip.getProtocol().equals(IpProtocol.TCP)) {
                    TCP tcp = (TCP) ip.getPayload();
                    mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP);
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_SRC) {
                        mb.setExact(MatchField.TCP_SRC, tcp.getSourcePort());
                    }
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_DST) {
                        mb.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
                    }
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_15) >= 0) {
                        if (FLOWMOD_DEFAULT_MATCH_TCP_FLAG) {
                            mb.setExact(MatchField.TCP_FLAGS, U16.of(tcp.getFlags()));
                        }
                    } else if (sw.getSwitchDescription().getHardwareDescription().toLowerCase().contains("open vswitch") && (
                            Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[0]) > 2 || (
                                    Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[0]) == 2 &&
                                            Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[1]) >= 1))
                    ) {
                        if (FLOWMOD_DEFAULT_MATCH_TCP_FLAG) {
                            mb.setExact(MatchField.OVS_TCP_FLAGS, U16.of(tcp.getFlags()));
                        }
                    }
                } else if (ip.getProtocol().equals(IpProtocol.UDP)) {
                    UDP udp = (UDP) ip.getPayload();
                    mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_SRC) {
                        mb.setExact(MatchField.UDP_SRC, udp.getSourcePort());
                    }
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_DST) {
                        mb.setExact(MatchField.UDP_DST, udp.getDestinationPort());
                    }
                }
            }
        } else if (eth.getEtherType() == EthType.ARP) { /* shallow check for equality is okay for EthType */
            mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
        } else if (eth.getEtherType() == EthType.IPv6) {
            IPv6 ip = (IPv6) eth.getPayload();
            IPv6Address srcIp = ip.getSourceAddress();
            IPv6Address dstIp = ip.getDestinationAddress();

            if (FLOWMOD_DEFAULT_MATCH_IP) {
                mb.setExact(MatchField.ETH_TYPE, EthType.IPv6);
                if (FLOWMOD_DEFAULT_MATCH_IP_SRC) {
                    mb.setExact(MatchField.IPV6_SRC, srcIp);
                }
                if (FLOWMOD_DEFAULT_MATCH_IP_DST) {
                    mb.setExact(MatchField.IPV6_DST, dstIp);
                }
            }

            if (FLOWMOD_DEFAULT_MATCH_TRANSPORT) {
                /*
                 * Take care of the ethertype if not included earlier,
                 * since it's a prerequisite for transport ports.
                 */
                if (!FLOWMOD_DEFAULT_MATCH_IP) {
                    mb.setExact(MatchField.ETH_TYPE, EthType.IPv6);
                }

                if (ip.getNextHeader().equals(IpProtocol.TCP)) {
                    TCP tcp = (TCP) ip.getPayload();
                    mb.setExact(MatchField.IP_PROTO, IpProtocol.TCP);
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_SRC) {
                        mb.setExact(MatchField.TCP_SRC, tcp.getSourcePort());
                    }
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_DST) {
                        mb.setExact(MatchField.TCP_DST, tcp.getDestinationPort());
                    }
                    if (sw.getOFFactory().getVersion().compareTo(OFVersion.OF_15) >= 0) {
                        if (FLOWMOD_DEFAULT_MATCH_TCP_FLAG) {
                            mb.setExact(MatchField.TCP_FLAGS, U16.of(tcp.getFlags()));
                        }
                    } else if (
                            sw.getSwitchDescription().getHardwareDescription().toLowerCase().contains("open vswitch") && (
                                    Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[0]) > 2 || (
                                            Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[0]) == 2 &&
                                                    Integer.parseInt(sw.getSwitchDescription().getSoftwareDescription().toLowerCase().split("\\.")[1]) >= 1))
                    ) {
                        if (FLOWMOD_DEFAULT_MATCH_TCP_FLAG) {
                            mb.setExact(MatchField.OVS_TCP_FLAGS, U16.of(tcp.getFlags()));
                        }
                    }
                } else if (ip.getNextHeader().equals(IpProtocol.UDP)) {
                    UDP udp = (UDP) ip.getPayload();
                    mb.setExact(MatchField.IP_PROTO, IpProtocol.UDP);
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_SRC) {
                        mb.setExact(MatchField.UDP_SRC, udp.getSourcePort());
                    }
                    if (FLOWMOD_DEFAULT_MATCH_TRANSPORT_DST) {
                        mb.setExact(MatchField.UDP_DST, udp.getDestinationPort());
                    }
                }
            }
        }
        return mb.build();
    }
    private OFActionSetQueue getQueueAction(IPv4Address hostIPAddress, Ethernet eth, IOFSwitch sw){
        Long queueID;

        if (hostIPAddress.toString().equals("192.168.2.2") || hostIPAddress.toString().equals("192.168.2.3")){

            if (isRTP(eth) || ((IPv4) eth.getPayload()).getDestinationAddress().isMulticast()){
                queueID = 0L;
            }else {
                queueID = 2L;
            }
        } else {
            if (isRTP(eth)  || ((IPv4) eth.getPayload()).getDestinationAddress().isMulticast()){
                queueID = 1L;
            }else {
                queueID = 3L;
            }
        }
        return sw.getOFFactory().actions().buildSetQueue().setQueueId(queueID).build();

    }
    /**
     * Judge this is an RTP
     * @param eth
     * @return
     */
    private boolean isRTP(Ethernet eth){
        byte[] ipv4Packet = eth.getPayload().serialize();
        byte[] rawDstPort = new byte[2];
        System.arraycopy(ipv4Packet, 22, rawDstPort, 0, 2);

        int dstPort= (int) ( ((rawDstPort[0] & 0xFF)<<8)
                |(rawDstPort[1] & 0xFF));
        if (dstPort == 5004){
            log.info("This is a video stream");
            return true;
        }
        return false;
    }
}
