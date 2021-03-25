package net.floodlightcontroller.multicasting;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.internal.OFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.qos.DSCPField;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.routing.RoutingDecision;
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

public class MulticastManager implements IOFMessageListener, IFloodlightModule, IFetchMulticastGroupService {

    // Instance field
    protected IFloodlightProviderService floodlightProvider;
    protected IOFSwitchService switchService;
    protected ITopologyService topologyService;

    private ConcurrentHashMap<IPv4Address, MulticastGroup> multicastGroupInfoTable = new ConcurrentHashMap<>();

    protected MulticastRoutingDecision multicastRoutingDecision;
    protected HashSet<Match> receivedMatch;
    protected static Logger log = LoggerFactory.getLogger(MulticastManager.class);
    //    private MulticastInfoTable multicastInfoTable = new MulticastInfoTable();
    private ConcurrentHashMap<IPv4Address, PinSwitch> pinSwitchInfoMap = new ConcurrentHashMap<>();
    //    private MulticastSourceInfoTable multicastSourceInfoTable = new MulticastSourceInfoTable();
    protected static int groupNumber = 1;

    private static final short DECISION_BITS = 24;
    private static final short DECISION_SHIFT = 0;
    private static final long DECISION_MASK = ((1L << DECISION_BITS) - 1) << DECISION_SHIFT;

    private static final short FLOWSET_BITS = 28;
    protected static final short FLOWSET_SHIFT = DECISION_BITS;
    private static final long FLOWSET_MASK = ((1L << FLOWSET_BITS) - 1) << FLOWSET_SHIFT;
    private static final long FLOWSET_MAX = (long) (Math.pow(2, FLOWSET_BITS) - 1);
    protected static FlowSetIdRegistry flowSetIdRegistry;

    protected static final U64 DEFAULT_FORWARDING_COOKIE = AppCookie.makeCookie(FORWARDING_APP_ID, 0);

    protected IRoutingService routingService;
    //zzy
    Vector<Path> pathsList = new Vector<>();
    ConcurrentHashMap<DatapathId, Vector<OFPort>> rendezvousPoints = new ConcurrentHashMap<>();

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

    protected OFMessageDamper messageDamper;
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

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        switch (msg.getType()) {
            case PACKET_IN:
                OFPacketIn pi = (OFPacketIn) msg;
                if (eth.getEtherType() == EthType.IPv4) {
                    IPv4Address srcAddress = ((IPv4) eth.getPayload()).getSourceAddress();
                    IPv4Address destAddress = ((IPv4) eth.getPayload()).getDestinationAddress();

                    //  Process IGMP Message
                    if (((IPv4) eth.getPayload()).getProtocol() == IpProtocol.IGMP) {
//                            if (!receivedMatch.contains(pi.getMatch())) {   // only receive one packet_in for streaming on wait list
//                                receivedMatch.add(pi.getMatch());
                        processIGMPMessage(sw, pi, cntx);
//                            }

                    } else if (eth.isMulticast()) {
                        // register multicast source
                        U64 flowSetId = flowSetIdRegistry.generateFlowSetId();
                        U64 cookie = makeForwardingCookie(RoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION), flowSetId);
                        OFPort srcPort = OFMessageUtils.getInPort(pi);
                        //wrf: Change the structure: Packet_In only from source switch !!!
                        Match match = sw.getOFFactory().buildMatch()
                                .setExact(MatchField.IN_PORT, srcPort)
                                .setExact(MatchField.IPV4_SRC, ((IPv4) eth.getPayload()).getSourceAddress())
                                .build();
                        MulticastSource newMulticastSource = new MulticastSource(sw.getId(), srcPort, cookie, match, cntx, pi, srcAddress);
                        if (multicastSourceInfoTable.containsKey(destAddress) && !multicastSourceInfoTable.get(destAddress).containsKey(srcAddress)) {
                            multicastSourceInfoTable.get(destAddress).put(srcAddress, newMulticastSource);
                        } else if (!multicastSourceInfoTable.containsKey(destAddress)) { // new multicast address with a new source
                            ConcurrentHashMap<IPv4Address, MulticastSource> tempMulticastSourceInfoRegister = new ConcurrentHashMap<>();
                            // TODO: add what infor?
                            tempMulticastSourceInfoRegister.put(srcAddress, newMulticastSource);
                            multicastSourceInfoTable.put(destAddress, tempMulticastSourceInfoRegister);
                        }

                        // There is/are host/hosts which waits/wait for receiving packet from a source
                        if (multicastInfoTable.containsKey(destAddress)) {
                            // TODO: if this statement is needed?
                            if (!receivedMatch.contains(pi.getMatch())) {   // only receive one packet_in for streaming on wait list
                                // TODO: delete print
                                System.out.println(destAddress);
                                System.out.println(((IPv4) eth.getPayload()).getProtocol());
                                receivedMatch.add(pi.getMatch());
                                processMulticastPacketInMessage(sw, pi, null, cntx);
                            }
                        }
                    }
                }
        }

        return Command.CONTINUE;
    }

    /**
     * The method to process IGMP join or leave message,
     * and maintain multicast information table <K: Multicast address, V: Set of host address>,
     * and pin switch IPv4 address mapping map <K: Ipv4 address, V: Map<DatapthID, Inport>>, the inprot here is used as the Outport in the routing path
     *
     * @param sw   the attachment point of host
     * @param pi
     * @param cntx
     * @return
     */
    public Command processIGMPMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        // TODO: Delete the print
        System.out.println("-----------receive igmp packet ----------------");
        System.out.println("Source Address is: " + ((IPv4) eth.getPayload()).getSourceAddress());
        System.out.println("Switch ID is " + sw.getId());

        byte[] igmpPayload = eth.getPayload().serialize();
        byte[] rawMulticastAddress = new byte[4];
        System.arraycopy(igmpPayload, 36, rawMulticastAddress, 0, 4);
        IPv4Address multicastAddress = IPv4Address.of(rawMulticastAddress);
        IPv4Address hostIPAddress = ((IPv4) eth.getPayload()).getSourceAddress();

        System.out.println("Destination Address is: " + multicastAddress);
        System.out.println("Payload length = " + igmpPayload.length);

        // the total length of this packet is 54, the previous 14(0-13) is for header, the rest 40 is for paylod, and the 46/32 is for record type
        if (igmpPayload[32] == 4) {
            System.out.println(igmpPayload + "IGMP join message");
            boolean ifExist = false;

            //  process IGMP message sender host
            if (multicastGroupInfoTable.isEmpty()) {    // no multicast group registerd
                multicastGroupInfoTable.put(multicastAddress, new MulticastGroup(multicastAddress, hostIPAddress));

                // A new host join, add it's match item
                if (topologyService.isEdge(sw.getId(), OFMessageUtils.getInPort(pi))) {
                    pinSwitchInfoMap.put(hostIPAddress, new PinSwitch(sw.getId(), OFMessageUtils.getInPort(pi)));
                }
            } else {    // non-empty table
                if (multicastGroupInfoTable.containsKey(multicastAddress)) {
                    MulticastGroup tempMulticastGroup = multicastGroupInfoTable.get(multicastAddress);
                    if (tempMulticastGroup.getMulticastHosts().contains(hostIPAddress)) {   // host already join the multicast group
                        // already exist, no need for route push
                        ifExist = true;
                    } else {    // host has not joined the multicast group yes
                        tempMulticastGroup.getMulticastHosts().add(hostIPAddress);
                        // A new host join, add it's match item
                        if (topologyService.isEdge(sw.getId(), OFMessageUtils.getInPort(pi))) {
                            pinSwitchInfoMap.put(hostIPAddress, new PinSwitch(sw.getId(), OFMessageUtils.getInPort(pi)));
                        }
                    }
                } else {    // multicast group IP address do not exist
                    multicastGroupInfoTable.put(multicastAddress, new MulticastGroup(multicastAddress, hostIPAddress));

                    // A new host join, add it's match item
                    if (topologyService.isEdge(sw.getId(), OFMessageUtils.getInPort(pi))) {
                        pinSwitchInfoMap.put(hostIPAddress, new PinSwitch(sw.getId(), OFMessageUtils.getInPort(pi)));
                    }
                }
            }

            // determine the need for pushing route
            // Not exist before && Already has source/sources
            if (!ifExist && !multicastGroupInfoTable.get(multicastAddress).getMulticastSources().isEmpty()) {
                //wrf: push Route
                DSCPField dscpField = DSCPField.Default;
                DatapathId dstId = sw.getId();
                OFPort dstPort = OFMessageUtils.getInPort(pi);

                for (MulticastSource multicastSource : multicastGroupInfoTable.get(multicastAddress).getMulticastSources().values()) {
                    DatapathId srcId = multicastSource.getSrcId();
                    OFPort srcPort = multicastSource.getSrcPort();

                    Path path = getMulticastRoutingDecision(srcId, srcPort, dstId, dstPort, dscpField);
                    System.out.println("----------------------------------" + path.getPath().get(path.getPath().size() - 1));
                    pushMulticastingRoute(path, multicastSource.getMatch(), multicastSource.getPi(), multicastSource.getCookie(), multicastSource.getCntx(), false, OFFlowModCommand.ADD, false);
                }
            }

        } else if (igmpPayload[32] == 3) {  // leave message
            // TODO: delete print and replace it with log
            System.out.println(igmpPayload + "IGMP leave message");

            // host leave, delete the match item
            pinSwitchInfoMap.remove(hostIPAddress);
            multicastGroupInfoTable.get(multicastAddress).getMulticastHosts().remove(hostIPAddress);
        }
        return Command.CONTINUE;
    }

    public Command processMulticastPacketInMessage(IOFSwitch sw, OFPacketIn pi, IRoutingDecision decision, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        IPv4Address destinationAddress = ((IPv4) eth.getPayload()).getDestinationAddress();
        IPv4Address streamingSourceIPAddress = ((IPv4) eth.getPayload()).getSourceAddress();

        DatapathId srcId = sw.getId();
        DatapathId dstId = null;
        OFPort srcPort = OFMessageUtils.getInPort(pi);
        OFPort dstPort = null;
        DSCPField dscpField = DSCPField.Default;
        Path path = null;

        if (!topologyService.isEdge(srcId, srcPort)) {
            System.out.println("Not a PACKET_IN from EDGE");
            return Command.CONTINUE;
        }
        U64 flowSetId = flowSetIdRegistry.generateFlowSetId();
        U64 cookie = makeForwardingCookie(RoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION), flowSetId);


//        Match match = createMatchFromPacket(sw, srcPort, pi, cntx);
        //wrf: Change the structure: Packet_In only from source switch !!!
        Match match = sw.getOFFactory().buildMatch()
                .setExact(MatchField.IN_PORT, srcPort)
                .setExact(MatchField.IPV4_SRC, ((IPv4) eth.getPayload()).getSourceAddress())
                .build();

        for (IPv4Address hostAddress : multicastInfoTable.get(destinationAddress)) {
            dstId = (DatapathId) pinSwitchInfoMap.get(hostAddress).keySet().toArray()[0];
            dstPort = pinSwitchInfoMap.get(hostAddress).get(dstId);
            path = getMulticastRoutingDecision(srcId, srcPort, dstId, dstPort, dscpField);
            System.out.println("----------------------------------" + path.getPath().get(path.getPath().size() - 1));
            pushMulticastingRoute(path, match, pi, cookie, cntx, false, OFFlowModCommand.ADD, false);
        }

        return Command.CONTINUE;
    }

    /**
     * This method is used for writing the flow table item to each switches on the route,
     * and write the table table to RP switch which determined by routing decision
     *
     * @param route                          QoS route calculated by routing decision algorithm
     * @param match
     * @param pi
     * @param cookie
     * @param cntx
     * @param requestFlowRemovedNotification default is false
     * @param flowModCommand                 default is OFFlowMod.ADD
     * @param packetOutSent                  default is false
     * @return
     */
    public boolean pushMulticastingRoute(Path route, Match match, OFPacketIn pi, U64 cookie, FloodlightContext cntx,
                                         boolean requestFlowRemovedNotification, OFFlowModCommand flowModCommand, boolean packetOutSent) {

        List<NodePortTuple> switchPortList = null;
        switch (multicastRoutingDecision.getRoutingAction()) {
            case JOIN_WITHOUT_RP:
                switchPortList = route.getPath();
                break;
            case JOIN_WITH_RP:
                switchPortList = multicastRoutingDecision.getuPath().getPath();
                break;
        }

        for (int indx = switchPortList.size() - 1; indx > 0; indx -= 2) {
            // indx and indx-1 will always have the same switch DPID.
            DatapathId switchDPID = switchPortList.get(indx).getNodeId();
            IOFSwitch sw = switchService.getSwitch(switchDPID);

            if (sw == null) {
                if (log.isWarnEnabled()) {
                    log.warn("Unable to push route, switch at DPID {} " + "not available", switchDPID);
                }
                return false;
            }

            // need to build flow mod based on what type it is. Cannot set command later
            OFFlowMod.Builder fmb;
            switch (flowModCommand) {
                case ADD:
                    fmb = sw.getOFFactory().buildFlowAdd();
                    break;
                case DELETE:
                    fmb = sw.getOFFactory().buildFlowDelete();
                    break;
                case DELETE_STRICT:
                    fmb = sw.getOFFactory().buildFlowDeleteStrict();
                    break;
                case MODIFY:
                    fmb = sw.getOFFactory().buildFlowModify();
                    break;
                default:
                    log.error("Could not decode OFFlowModCommand. Using MODIFY_STRICT. (Should another be used as the default?)");
                case MODIFY_STRICT:
                    fmb = sw.getOFFactory().buildFlowModifyStrict();
                    break;
            }

            Match.Builder mb = MatchUtils.convertToVersion(match, sw.getOFFactory().getVersion());

            // set input and output ports on the switch
            OFPort outPort = switchPortList.get(indx).getPortId();
            OFPort inPort = switchPortList.get(indx - 1).getPortId();

            if (FLOWMOD_DEFAULT_MATCH_IN_PORT) {
                mb.setExact(MatchField.IN_PORT, inPort);
            }


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

            switch (multicastRoutingDecision.getRoutingAction()) {

                case JOIN_WITH_RP:
                    if (indx == 1) {    // Process RP's actions as a group
                        // Compose a Group
                        ArrayList<OFBucket> bucketList = new ArrayList<OFBucket>();
                        OFSwitch rp = (OFSwitch) switchService.getSwitch(multicastRoutingDecision.getrP());

                        // add all out ports as buckets
                        for (OFPort forwardPort : rendezvousPoints.get(multicastRoutingDecision.getrP())) {
                            bucketList.add(rp.getOFFactory().buildBucket()
                                    .setWatchGroup(OFGroup.ANY)
                                    .setWatchPort(OFPort.ANY)
                                    .setActions(Collections.singletonList((OFAction) rp.getOFFactory().actions().buildOutput()
                                            .setMaxLen(0xffFFffFF)
                                            .setPort(forwardPort)
                                            .build()))
                                    .build());
                        }

                        OFGroupAdd addGroup = rp.getOFFactory().buildGroupAdd()
                                .setGroupType(OFGroupType.ALL)
                                .setGroup(OFGroup.of(groupNumber))
                                .setBuckets(bucketList)
                                .build();

                        rp.write(addGroup);

                        //wrf: change to set pure group
                        fmb.setActions(Collections.singletonList((OFAction) rp.getOFFactory().actions().buildGroup()
                                .setGroup(OFGroup.of(groupNumber++))
                                .build()));
                        break;
                    }
                case JOIN_WITHOUT_RP:
                    OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
                    List<OFAction> actions = new ArrayList<>();
                    aob.setPort(outPort);
                    aob.setMaxLen(Integer.MAX_VALUE);
                    actions.add(aob.build());
                    FlowModUtils.setActions(fmb, actions, sw);
                    break;

            }


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

    @Override
    public String getName() {
        return "multicasting";
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
        l.add(IFetchMulticastGroupService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IFetchMulticastGroupService.class, this);
        return m;
    }

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l =
                new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        routingService = context.getServiceImpl(IRoutingService.class);
        topologyService = context.getServiceImpl(ITopologyService.class);
        messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
                EnumSet.of(OFType.FLOW_MOD),
                OFMESSAGE_DAMPER_TIMEOUT);

        flowSetIdRegistry = FlowSetIdRegistry.getInstance();

        receivedMatch = new HashSet<>();
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }

    @Override
    public MulticastInfoTable getMulticastInfoTable() {
        return this.multicastInfoTable;
    }

    private Path getMulticastRoutingDecision(DatapathId src, OFPort srcPort,
                                             DatapathId dst, OFPort dstPort,
                                             DSCPField dscpField) {

        DatapathId surRP = null;
        Vector<OFPort> portSet = new Vector<>();
        Stack<DatapathId> tempRP = new Stack<>();
        Stack<DatapathId> possibleRP = new Stack<>();
        Path nPath = routingService.getPath(src, srcPort, dst, dstPort, dscpField);

        //diff condition of pathList
        if (!this.pathsList.isEmpty()) {
            for (Path nextPath : this.pathsList) {
                for (int k = 0; k < nPath.getPath().size(); k += 2) {
                    for (int l = 0; l < nextPath.getPath().size(); l += 2) {
                        if (nPath.getPath().get(k).getNodeId().equals(nextPath.getPath().get(l).getNodeId())) {
                            tempRP.push(nPath.getPath().get(k).getNodeId());
                        }
                    }
                }
                if (!tempRP.isEmpty()) {
                    possibleRP.add(tempRP.peek());
                }
                tempRP.empty();
            }
            List<NodePortTuple> nodePortTupleList = nPath.getPath();
            for (int i = nodePortTupleList.size() - 1; i >= 0; i--) {
                if (possibleRP.contains(nodePortTupleList.get(i).getNodeId())) {
                    surRP = nodePortTupleList.get(i).getNodeId();
                }
            }
            //find the ports for the RP point
            pathsList.add(nPath);
            for (Path p : pathsList) {
                List<NodePortTuple> pathPortList = p.getPath();
                for (int i = pathPortList.size() - 1; i >= 0; i--) {
                    if (surRP.equals(pathPortList.get(i).getNodeId())) {
                        portSet.add(pathPortList.get(i).getPortId());
                        break;
                    }
                }
            }
            rendezvousPoints.put(surRP, portSet);
        } else {
            pathsList.add(nPath);
        }
        if (surRP == null) {
            multicastRoutingDecision = new MulticastRoutingDecision(MulticastRoutingDecision.MulticastRoutingAction.JOIN_WITHOUT_RP, nPath);
        } else {
            multicastRoutingDecision = new MulticastRoutingDecision(MulticastRoutingDecision.MulticastRoutingAction.JOIN_WITH_RP, nPath, surRP);
        }
        return nPath;
    }
}
