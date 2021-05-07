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
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.routing.RoutingDecision;
import net.floodlightcontroller.staticCache.web.StaticCacheStrategy;
import net.floodlightcontroller.staticCache.web.StaticCacheWebRoutable;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.*;
import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
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
    protected ITopologyService topologyService;
    protected OFMessageDamper messageDamper;
    protected IRestApiService restApi;
    protected IDeviceService deviceService;
    protected IRoutingService routingEngineService;
    protected List<StaticCacheStrategy> strategies;

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
                // TODO:
                OFPacketIn pi = (OFPacketIn) msg;
                if (eth.getEtherType() == EthType.IPv4) {
                    if (((IPv4) eth.getPayload()).getProtocol() == IpProtocol.TCP) {

                        IPv4Address srcAddress = ((IPv4) eth.getPayload()).getSourceAddress();
                        IPv4Address dstAddress = ((IPv4) eth.getPayload()).getDestinationAddress();
                        int tp_src = ((TCP) eth.getPayload().getPayload()).getSourcePort().getPort();
                        int tp_dst = ((TCP) eth.getPayload().getPayload()).getDestinationPort().getPort();

                        if (tp_dst == 80 || tp_dst == 8080 || tp_dst == 8081 || tp_dst == 9098) {
                            return process_http_from_host(srcAddress, dstAddress, tp_src, tp_dst, sw, pi, cntx);
                        } else if (tp_src == 80 || tp_src == 8080 || tp_src == 8081 || tp_src == 9098) {
                            log.info("Receive a HTTP packet from cache/server on port " + tp_src);
                            return process_http_from_cache(srcAddress, dstAddress, tp_src, tp_dst, sw, pi, cntx);
                        }
                    }

                }
                break;
        }
        return Command.CONTINUE;
    }

    private Command process_http_from_host(IPv4Address srcAddress, IPv4Address dstAddress, int tp_src, int tp_dst, IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
        log.info("Receive a HTTP packet from host on port " + tp_dst);
        //wrf: 匹配策略表，一旦匹配，下发修改流表选项

        log.info("src ip is " + srcAddress);
        log.info("dst ip is " + dstAddress);

        StaticCacheStrategy matched_strategy = null;
        // wrf: find highest priority strategy，找到所有匹配项，然后选出最高的那一个
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
            //wrf: apply the strategy
            log.info("match the strategy");
            matched_strategy.tp_src = TransportPort.of(tp_src);
            matched_strategy.src_dpid = sw.getId();
            matched_strategy.src_inPort = OFMessageUtils.getInPort(pi);

            Path path_forward = routingEngineService.getPath(matched_strategy.src_dpid, matched_strategy.src_inPort, matched_strategy.dst_dpid, matched_strategy.dst_inPort);

            // wrf:push forward & inverse path
            U64 flowSetId = flowSetIdRegistry.generateFlowSetId();
            U64 cookie = makeForwardingCookie(RoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION), flowSetId);

            pushRoute(path_forward, matched_strategy.match_host, pi, matched_strategy, sw.getId(), cookie, cntx, false, "HOST");

            //wrf: break?
            return Command.STOP;
        } else
            return Command.CONTINUE;
    }

    private Command process_http_from_cache(IPv4Address srcAddress, IPv4Address dstAddress, int tp_src, int tp_dst, IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
        log.info("Receive a HTTP packet from cache on port " + tp_src);
        //wrf: 匹配策略表，一旦匹配，下发修改流表选项

        log.info("src ip is " + srcAddress);
        log.info("dst ip is " + dstAddress);

        StaticCacheStrategy matched_strategy = null;
        // wrf: find highest priority strategy，找到所有匹配项，然后选出最高的那一个
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
            //wrf: apply the strategy
            log.info("match the strategy");
            Path path_inverse = routingEngineService.getPath(matched_strategy.dst_dpid, matched_strategy.dst_inPort, matched_strategy.src_dpid, matched_strategy.src_inPort);

            // wrf:push forward & inverse path
            U64 flowSetId = flowSetIdRegistry.generateFlowSetId();
            U64 cookie = makeForwardingCookie(RoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION), flowSetId);

            pushRoute(path_inverse, matched_strategy.match_cache, pi, matched_strategy, sw.getId(), cookie, cntx, false, "CACHE");

            //wrf: break?
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
        messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
                EnumSet.of(OFType.FLOW_MOD),
                OFMESSAGE_DAMPER_TIMEOUT);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        restApi.addRestletRoutable(new StaticCacheWebRoutable());
    }

    /*
     * kwm:IStaticCacheService implements
     * */
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

    @Override
    public List<StaticCacheStrategy> getStrategies() {
        return this.strategies;
    }

    public void applyStrategy(StaticCacheStrategy strategy, IOFSwitch sw, OFPacketIn pi, Path path) {

    }

    /**
     * Push routes from back to front
     *
     * @param route                          Route to push
     * @param match                          OpenFlow fields to match on
     * @param cookie                         The cookie to set in each flow_mod
     * @param cntx                           The floodlight context
     * @param requestFlowRemovedNotification if set to true then the switch would
     *                                       send a flow mod removal notification when the flow mod expires
     *                                       OFFlowMod.OFPFC_MODIFY etc.
     * @return true if a packet out was sent on the first-hop switch of this route
     */
    public boolean pushRoute(Path route, Match match, OFPacketIn pi, StaticCacheStrategy strategy,
                             DatapathId pinSwitch, U64 cookie, FloodlightContext cntx,
                             boolean requestFlowRemovedNotification, String hostOrCache) {

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
            //
            if (pinSwitch.equals(switchDPID)){
                switch (hostOrCache) {
                    case "HOST":
                        strategy.completeStrategy_host(sw, pi, outPort);
                        sw.write(strategy.flowAdd_host);
                        break;
                    case "CACHE":
                        strategy.completeStrategy_cache(sw, pi, outPort);
                        sw.write(strategy.flowAdd_cache);
                        break;
                }
                pushPacket(sw, pi, outPort, true, cntx);

            } else {
                // need to build flow mod based on what type it is. Cannot set command later
                OFFlowMod.Builder fmb;
                fmb = sw.getOFFactory().buildFlowAdd();

                OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
                List<OFAction> actions = new ArrayList<>();
                Match.Builder mb = MatchUtils.convertToVersion(match, sw.getOFFactory().getVersion());


                if (FLOWMOD_DEFAULT_MATCH_IN_PORT) {
                    mb.setExact(MatchField.IN_PORT, inPort);
                }

                // wrf:determine the src/dst switch
                aob.setPort(outPort);
                aob.setMaxLen(Integer.MAX_VALUE);
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
}