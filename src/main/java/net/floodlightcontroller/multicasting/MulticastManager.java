package net.floodlightcontroller.multicasting;

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
import net.floodlightcontroller.core.util.SingletonTask;
import net.floodlightcontroller.multicasting.web.GinkgoRouteable;
import net.floodlightcontroller.packet.*;
import net.floodlightcontroller.qos.DSCPField;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.*;
import net.floodlightcontroller.threadpool.IThreadPoolService;
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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static net.floodlightcontroller.routing.ForwardingBase.FORWARDING_APP_ID;

public class MulticastManager implements IOFMessageListener, IFloodlightModule, IFetchMulticastGroupService {

    protected IRestApiService restApi;

    // Instance field
    protected IFloodlightProviderService floodlightProvider;
    protected IOFSwitchService switchService;
    protected ITopologyService topologyService;
    protected OFMessageDamper messageDamper;

    // Multicast Data structure
    private ConcurrentHashMap<IPv4Address, MulticastGroup> multicastGroupInfoTable = new ConcurrentHashMap<>();

    protected HashSet<Match> receivedMatch;
    protected static Logger log = LoggerFactory.getLogger(MulticastManager.class);
    private ConcurrentHashMap<IPv4Address, PinSwitch> pinSwitchInfoMap = new ConcurrentHashMap<>();
    protected static int groupNumber = 0;

    // SourceTimeout process
    protected SingletonTask sourceTimeout;
    protected IThreadPoolService threadPoolService;

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
                    IPv4Address dstAddress = ((IPv4) eth.getPayload()).getDestinationAddress();

                    //  Process IGMP Message
                    if (((IPv4) eth.getPayload()).getProtocol() == IpProtocol.IGMP) {
                        if (!multicastGroupInfoTable.containsKey(dstAddress)) {
                            processIGMPMessage(sw, pi, cntx);
                        } else if (!multicastGroupInfoTable.get(dstAddress).getMulticastHosts().contains(srcAddress)) {
                            processIGMPMessage(sw, pi, cntx);
                        }

                    } else if (eth.isMulticast()) { // determine if it is a multicast source
                        if (!multicastGroupInfoTable.isEmpty() && multicastGroupInfoTable.containsKey(dstAddress) && !multicastGroupInfoTable.get(dstAddress).getMulticastSources().isEmpty() && multicastGroupInfoTable.get(dstAddress).getMulticastSources().containsKey(srcAddress)) {   // only if src address is exist, refresh it's invalid time
                            // refresh the source valid Time
                            multicastGroupInfoTable.get(dstAddress).getMulticastTreeInfoTable().get(srcAddress).setSourceValidTime();
                        }
                        if (!multicastGroupInfoTable.isEmpty() && multicastGroupInfoTable.containsKey(dstAddress) && !multicastGroupInfoTable.get(dstAddress).getMulticastHosts().isEmpty()) {   // only if the multicast hosts exist, process the packet_in from the multicast source

                            if (multicastGroupInfoTable.get(dstAddress).getMulticastSources().isEmpty() || !multicastGroupInfoTable.get(dstAddress).getMulticastSources().containsKey(srcAddress)) {    // only if the source do not exist, process the packet_in
                                log.info("A new multicast source has been registered from " + srcAddress + ", and multicast address is " + dstAddress);
                                U64 flowSetId = flowSetIdRegistry.generateFlowSetId();
                                U64 cookie = makeForwardingCookie(RoutingDecision.rtStore.get(cntx, IRoutingDecision.CONTEXT_DECISION), flowSetId);
                                OFPort srcPort = OFMessageUtils.getInPort(pi);
                                Match match = sw.getOFFactory().buildMatch()
                                        .setExact(MatchField.IN_PORT, srcPort)
                                        .setExact(MatchField.IPV4_SRC, ((IPv4) eth.getPayload()).getSourceAddress())
                                        .build();
                                MulticastSource newMulticastSource = new MulticastSource(sw.getId(), srcPort, cookie, match, cntx, pi, srcAddress);
                                MulticastGroup tempMulticastGroup = multicastGroupInfoTable.get(dstAddress);
                                if (!tempMulticastGroup.getMulticastSources().containsKey(srcAddress)) {
                                    tempMulticastGroup.addNewMulticastSource(srcAddress, newMulticastSource);
                                }
                                processSourcePacketInMessage(sw, pi, cntx);
                            } else {    // source already exist, drop the packet_in
                               return Command.STOP;
                            }

                        } else {    // do not have host wait for multicasting, drop the packet_in from source
                            return Command.STOP;
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

        byte[] igmpPayload = eth.getPayload().serialize();
        byte[] rawMulticastAddress = new byte[4];
        System.arraycopy(igmpPayload, 36, rawMulticastAddress, 0, 4);
        IPv4Address multicastAddress = IPv4Address.of(rawMulticastAddress);
        IPv4Address hostIPAddress = ((IPv4) eth.getPayload()).getSourceAddress();

        // the total length of this packet is 54, the previous 14(0-13) is for header, the rest 40 is for payload, and the 46/32 is for record type
        if (igmpPayload[32] == 4) {
            log.info("Receive an IGMP Join Message from" + sw.getId() + ": "+ hostIPAddress + ", and send to " + multicastAddress);
            processIGMPJoinMsg(multicastAddress, hostIPAddress, sw, pi, sw.getId(), cntx);

        } else if (igmpPayload[32] == 3) {  // leave message
            // only the host exist, process the leave message
            if (!multicastGroupInfoTable.isEmpty() && multicastGroupInfoTable.get(multicastAddress).getMulticastHosts().contains(hostIPAddress)) {
                log.info("Receive an IGMP Leave Message from " + sw.getId() + ": "+ hostIPAddress + ", and send to " + multicastAddress);
                processIGMPLeaveMsg(multicastAddress, hostIPAddress);
            }
        }
        return Command.CONTINUE;
    }


    private void processIGMPJoinMsg(IPv4Address multicastAddress, IPv4Address hostIPAddress, IOFSwitch sw, OFPacketIn pi, DatapathId pinSwitchId, FloodlightContext cntx) {
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
        // And it is not the first host who really begins to receive the packet from source
        if (!ifExist && !multicastGroupInfoTable.get(multicastAddress).getMulticastSources().isEmpty() && multicastGroupInfoTable.get(multicastAddress).getMulticastHosts().size() > 1) {
            //wrf: push Route
            DSCPField dscpField = DSCPField.Default;
            DatapathId dstId = sw.getId();
            OFPort dstPort = OFMessageUtils.getInPort(pi);

            for (MulticastSource multicastSource : multicastGroupInfoTable.get(multicastAddress).getMulticastSources().values()) {
                DatapathId srcId = multicastSource.getSrcId();
                OFPort srcPort = multicastSource.getSrcPort();

                MulticastTree tempMulticastTree = multicastGroupInfoTable.get(multicastAddress).getMulticastTreeInfoTable().get(multicastSource.getSrcAddress());
                Path path = getMulticastRoutingDecision(srcId, srcPort, dstId, dstPort, dscpField, hostIPAddress, tempMulticastTree);
                pushMulticastingRoute(path, multicastSource.getMatch(), pi, pinSwitchId, multicastSource.getCookie(), cntx, tempMulticastTree.getAltBPRegister(), false, false);
            }
        }
    }

    private void processIGMPLeaveMsg(IPv4Address multicastAddress, IPv4Address hostIPAddress) {
        MulticastGroup tempMulticastGroup = multicastGroupInfoTable.get(multicastAddress);

        // host leave, delete the match item
        pinSwitchInfoMap.remove(hostIPAddress);
        tempMulticastGroup.getMulticastHosts().remove(hostIPAddress);
        for (MulticastTree multicastTree : tempMulticastGroup.getMulticastTreeInfoTable().values()) {
            List<NodePortTuple> switchPortList = multicastTree.getPathList().get(hostIPAddress).getPath();

            // iterate over the path, re-compose the action of altBP
            for (int indx = switchPortList.size() - 1; indx > 0; indx -= 2) {
                // indx and indx-1 will always have the same switch DPID.
                DatapathId altBPDPID = switchPortList.get(indx).getNodeId();
                IOFSwitch altBPSwitch = switchService.getSwitch(altBPDPID);

                AltBP tempAltBP = multicastTree.getAltBPRegister().get(altBPDPID);

                OFPort outPort = switchPortList.get(indx).getPortId();
                OFPort inPort = switchPortList.get(indx - 1).getPortId();

                // remove BP's outport from the outportSet
                tempAltBP.getOutPortSet().remove(outPort);

                OFFlowMod.Builder tempFMB;
                // compose the flow table action
                if (tempAltBP.getOutPortSet().size() > 1) { // still have 1+ outports, compose a new bucket
                    ArrayList<OFBucket> bucketList = new ArrayList<OFBucket>();
                    // add all out ports as buckets
                    for (OFPort forwardPort : tempAltBP.getOutPortSet()) {
                        bucketList.add(altBPSwitch.getOFFactory().buildBucket()
                                .setWatchGroup(OFGroup.ANY)
                                .setWatchPort(OFPort.ANY)
                                .setActions(Collections.singletonList((OFAction) altBPSwitch.getOFFactory().actions().buildOutput()
                                        .setMaxLen(0xffFFffFF)
                                        .setPort(forwardPort)
                                        .build()))
                                .build());
                    }

                    OFGroupModify modifyGroup = altBPSwitch.getOFFactory().buildGroupModify()
                            .setGroupType(OFGroupType.ALL)
                            .setGroup(OFGroup.of(tempAltBP.getGroupNumber()))
                            .setBuckets(bucketList)
                            .build();

                    altBPSwitch.write(modifyGroup);
                    log.info("Adjust the group table's bucket quantity");
                    break;
                } else if (tempAltBP.getOutPortSet().size() == 1) {    // compose a normal forwarding action
                    tempFMB = altBPSwitch.getOFFactory().buildFlowModify();
                    tempFMB.setMatch(tempAltBP.getMatch());

                    OFGroupDelete deleteGroup = altBPSwitch.getOFFactory().buildGroupDelete()
                            .setGroupType(OFGroupType.ALL)
                            .setGroup(OFGroup.of(tempAltBP.getGroupNumber()))
                            .build();


                    OFActionOutput.Builder aob = altBPSwitch.getOFFactory().actions().buildOutput();
                    List<OFAction> actions = new ArrayList<>();
                    aob.setPort((OFPort) tempAltBP.getOutPortSet().toArray()[0]);
                    aob.setMaxLen(Integer.MAX_VALUE);
                    actions.add(aob.build());
                    FlowModUtils.setActions(tempFMB, actions, altBPSwitch);

                    /* Configure for particular switch pipeline */
                    if (altBPSwitch.getOFFactory().getVersion().compareTo(OFVersion.OF_10) != 0) {
                        tempFMB.setTableId(FLOWMOD_DEFAULT_TABLE_ID);
                    }
                    messageDamper.write(altBPSwitch, tempFMB.build());
                    altBPSwitch.write(deleteGroup);
                    log.info("Modify the flow table tiem and delete the group table");
                    break;
                } else if (tempAltBP.getOutPortSet().size() == 0) { // delete previous forwarding action
                    tempFMB = altBPSwitch.getOFFactory().buildFlowDelete();
                    tempFMB.setMatch(tempAltBP.getMatch());

                    // set flowmod's flags
                    Set<OFFlowModFlags> flags = new HashSet<>();
                    flags.add(OFFlowModFlags.SEND_FLOW_REM);
                    tempFMB.setFlags(flags);

                    /* Configure for particular switch pipeline */
                    if (altBPSwitch.getOFFactory().getVersion().compareTo(OFVersion.OF_10) != 0) {
                        tempFMB.setTableId(FLOWMOD_DEFAULT_TABLE_ID);
                    }
                    messageDamper.write(altBPSwitch, tempFMB.build());
                    log.info("Delete the last path's flow table item");
                }

            }

            // In the end, remove this path
            multicastTree.getPathList().remove(hostIPAddress);

        }
        if (tempMulticastGroup.getMulticastHosts().isEmpty() && tempMulticastGroup.getMulticastSources().isEmpty()) {    // no hosts, no sources, this multicast group should be removed
            multicastGroupInfoTable.remove(multicastAddress);
        }
    }

    public Command processSourcePacketInMessage(IOFSwitch sw, OFPacketIn pi, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        IPv4Address multicastAddress = ((IPv4) eth.getPayload()).getDestinationAddress();
        IPv4Address sourceAddress = ((IPv4) eth.getPayload()).getSourceAddress();

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
        Match match = createMatchFromPacket(sw, srcPort, pi, cntx);

        for (IPv4Address hostAddress : multicastGroupInfoTable.get(multicastAddress).getMulticastHosts()) {
            dstId = pinSwitchInfoMap.get(hostAddress).getPinSwitchId();
            dstPort = pinSwitchInfoMap.get(hostAddress).getPinSwitchInPort();
            path = getMulticastRoutingDecision(srcId, srcPort, dstId, dstPort, dscpField, hostAddress, multicastGroupInfoTable.get(multicastAddress).getMulticastTreeInfoTable().get(sourceAddress));
            pushMulticastingRoute(path, match, pi, sw.getId(), cookie, cntx, multicastGroupInfoTable.get(multicastAddress).getMulticastTreeInfoTable().get(sourceAddress).getAltBPRegister(), false, false);
        }

        return Command.CONTINUE;
    }

    /**
     * This method is used for writing the flow table item to each switches on the route,
     * and write the table table to RP switch which determined by routing decision
     *
     * @param route                          QoS route calculated by routing decision algorithm
     * @param match
     * @param cookie
     * @param requestFlowRemovedNotification default is false
     * @return
     */
    public boolean pushMulticastingRoute(Path route, Match match, OFPacketIn pi, DatapathId pinSwitch, U64 cookie, FloodlightContext cntx, HashMap<DatapathId, AltBP> currentAltBPSet,
                                         boolean requestFlowRemovedNotification, boolean packetOutSent) {

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

            // register a new out port
            if (!currentAltBPSet.containsKey(switchDPID)) {   // register a new switch
                currentAltBPSet.put(switchDPID, new AltBP(outPort));
            } else {
                currentAltBPSet.get(switchDPID).getOutPortSet().add(outPort);
            }

            // need to build flow mod based on what type it is. Cannot set command later
            OFFlowMod.Builder fmb;

            switch (currentAltBPSet.get(switchDPID).getOutPortSet().size()) {
                case 1: // single point, altBP
                    fmb = sw.getOFFactory().buildFlowAdd();
                    break;
                case 0: // do not have any port
                    fmb = sw.getOFFactory().buildFlowDelete();
                    break;
                default:    // BP, need to modify the flow table item
                    fmb = sw.getOFFactory().buildFlowModify();
                    break;
            }

            Match.Builder mb = MatchUtils.convertToVersion(match, sw.getOFFactory().getVersion());

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

            // save current state of fmb for leaving
            currentAltBPSet.get(switchDPID).setFmb(fmb);

            // compose the flow table action
            if (currentAltBPSet.get(switchDPID).getOutPortSet().size() > 1) { // compose a bucket
                ArrayList<OFBucket> bucketList = new ArrayList<OFBucket>();

                // add all out ports as buckets
                for (OFPort forwardPort : currentAltBPSet.get(switchDPID).getOutPortSet()) {
                    bucketList.add(sw.getOFFactory().buildBucket()
                            .setWatchGroup(OFGroup.ANY)
                            .setWatchPort(OFPort.ANY)
                            .setActions(Collections.singletonList((OFAction) sw.getOFFactory().actions().buildOutput()
                                    .setMaxLen(0xffFFffFF)
                                    .setPort(forwardPort)
                                    .build()))
                            .build());
                }
                if (currentAltBPSet.get(switchDPID).getOutPortSet().size() > 2) {
                    OFGroupModify modifyGroup = sw.getOFFactory().buildGroupModify()
                            .setGroupType(OFGroupType.ALL)
                            .setGroup(OFGroup.of(currentAltBPSet.get(switchDPID).getGroupNumber()))
                            .setBuckets(bucketList)
                            .build();
                    sw.write(modifyGroup);
                } else { // a new BP transformed from altBP who has two out ports, size = 2
                    OFGroupAdd addGroup = sw.getOFFactory().buildGroupAdd()
                            .setGroupType(OFGroupType.ALL)
                            .setGroup(OFGroup.of(groupNumber))
                            .setBuckets(bucketList)
                            .build();
                    currentAltBPSet.get(switchDPID).setGroupNumber(groupNumber);
                    sw.write(addGroup);
                    fmb.setActions(Collections.singletonList((OFAction) sw.getOFFactory().actions().buildGroup()
                            .setGroup(OFGroup.of(groupNumber++))
                            .build()));
                }

            } else {    // compose a normal forwarding action
                OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
                List<OFAction> actions = new ArrayList<>();
                aob.setPort(outPort);
                aob.setMaxLen(Integer.MAX_VALUE);
                actions.add(aob.build());
                FlowModUtils.setActions(fmb, actions, sw);
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
                if (currentAltBPSet.get(switchDPID).getOutPortSet().size() < 3) {
                    messageDamper.write(sw, fmb.build());
                    log.info("A new " + fmb.getCommand() + " flow table item has been written to " + switchDPID);
                }
            }
            /* Push the packet out the first hop switch */
            if (!packetOutSent && sw.getId().equals(pinSwitch) &&
                    !fmb.getCommand().equals(OFFlowModCommand.DELETE) &&
                    !fmb.getCommand().equals(OFFlowModCommand.DELETE_STRICT)) {
                /* Use the buffered packet at the switch, if there's one stored */
                log.info("Push packet out the first hop switch");
                pushPacket(sw, pi, outPort, true, cntx);

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
        l.add(IRestApiService.class);
        return l;
    }

    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        routingService = context.getServiceImpl(IRoutingService.class);
        topologyService = context.getServiceImpl(ITopologyService.class);
        threadPoolService = context.getServiceImpl(IThreadPoolService.class);
        restApi = context.getServiceImpl(IRestApiService.class);
        messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
                EnumSet.of(OFType.FLOW_MOD),
                OFMESSAGE_DAMPER_TIMEOUT);

        flowSetIdRegistry = FlowSetIdRegistry.getInstance();
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {

        //kwmtodo: something happening unexpected, just use the thread for get the returnMap

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    getGroupPathTree();
                    if (returnMap.isEmpty()){
                        //continue get the returnMap;
//                        System.out.println("empty");
                        continue;
                    }else{
                        break;
                    }
                }
            }
        }).start();
        restApi.addRestletRoutable(new GinkgoRouteable());
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
        ScheduledExecutorService ses = threadPoolService.getScheduledExecutor();

        sourceTimeout = new SingletonTask(ses, new Runnable() {
            @Override
            public void run() {
                log.info("Routine source timeout check");
                if (!multicastGroupInfoTable.isEmpty()) {
                    for (MulticastGroup mcGroup : multicastGroupInfoTable.values()) {
                        if (!mcGroup.getMulticastTreeInfoTable().isEmpty()) {
                            for (MulticastTree mcTree : mcGroup.getMulticastTreeInfoTable().values()) {
                                if (mcTree.getSourceValidTime() != null && (mcTree.getSourceValidTime().getTime() + 10000) < System.currentTimeMillis()) {  // provide 10 seconds redundancy for outdated source live time
                                    IPv4Address removedSource = mcTree.getSourceAddress();
                                    mcGroup.getMulticastSources().remove(removedSource);
                                    mcGroup.getMulticastTreeInfoTable().remove(removedSource);
                                    log.info("Remove the invalid multicast source: " + removedSource);
                                }
                            }
                        }
                    }
                }
                sourceTimeout.reschedule(5, TimeUnit.SECONDS);
            }
        });
        sourceTimeout.reschedule(1, TimeUnit.SECONDS);
    }

    @Override
    public boolean ifMulticastAddressExist(IPv4Address dstAddress) {
        return this.multicastGroupInfoTable.containsKey(dstAddress);
    }

    //kwmtodo:
    LinkedHashMap<IPv4Address,MulticastTree> returnMap = new LinkedHashMap<>();
    @Override
    public HashMap<IPv4Address, MulticastTree> getGroupPathTree() {
        IPv4Address groupIP = null;
        MulticastGroup group = null;
        HashMap<IPv4Address, MulticastTree> multicastTreeInfoTable = new HashMap<>();
        MulticastTree value = null;
        Set<Map.Entry<IPv4Address, MulticastGroup>> entrySet = this.multicastGroupInfoTable.entrySet();
        for (Map.Entry<IPv4Address, MulticastGroup> entry: entrySet){
            groupIP = entry.getKey();
            group = entry.getValue();
            multicastTreeInfoTable = group.getMulticastTreeInfoTable();
            Set<Map.Entry<IPv4Address, MulticastTree>> pathTable = multicastTreeInfoTable.entrySet();
            for (Map.Entry<IPv4Address, MulticastTree> path : pathTable){
                value = path.getValue();
                returnMap.put(groupIP,value);
            }
        }
        return returnMap;
    }

    private Path getMulticastRoutingDecision(DatapathId src, OFPort srcPort,
                                             DatapathId dst, OFPort dstPort,
                                             DSCPField dscpField,
                                             IPv4Address hostAddress, MulticastTree multicastTree) {
        DatapathId bp = null;
        Stack<DatapathId> tempBP = new Stack<>();
        Stack<DatapathId> possibleBP = new Stack<>();
        Path newPath = routingService.getPath(src, srcPort, dst, dstPort);

        if (multicastTree.getPathList().isEmpty()) {
            multicastTree.getPathList().put(hostAddress, newPath);
        } else {
            //calculate BP and the rest part of the path
            //Then store into pathList
            for (Path nextPath : multicastTree.getPathList().values()) {
                for (int k = 0; k < newPath.getPath().size(); k += 2) {
                    for (int l = 0; l < nextPath.getPath().size(); l += 2) {
                        if (newPath.getPath().get(k).getNodeId().equals(nextPath.getPath().get(l).getNodeId())) {
                            tempBP.push(newPath.getPath().get(k).getNodeId());
                        }
                    }
                }
                if (!tempBP.isEmpty()) {
                    possibleBP.add(tempBP.peek());
                }
                tempBP.empty();
            }
            List<NodePortTuple> nodePortTuples = newPath.getPath();
            for (int i = nodePortTuples.size() - 1; i >= 0; i--) {
                if (possibleBP.contains(nodePortTuples.get(i).getNodeId())) {
                    bp = nodePortTuples.get(i).getNodeId();
                }
            }
            //get Path from BP
//            List<NodePortTuple> nodePortTuples = newPath.getPath();
            List<NodePortTuple> ansList = new ArrayList<>();
            boolean ready2getPath = false;
            for (int i = 0; i < nodePortTuples.size(); i++) {
                nodePortTuples.get(i);
                if (nodePortTuples.get(i).getNodeId().equals(bp)) {
                    ready2getPath = true;
                }
                ;
                if (ready2getPath) {
                    ansList.add(nodePortTuples.get(i));
                }
            }
            PathId id = new PathId(nodePortTuples.get(0).getNodeId(), nodePortTuples.get(nodePortTuples.size() - 1).getNodeId());
            Path ansPath = new Path(id, ansList);
            multicastTree.getPathList().put(hostAddress, ansPath);
            log.info("A new path contains BP has been calculated " + ansPath);
            return ansPath;
        }
        log.info("An initial path has been calculated " + newPath);
        return newPath;
    }
}
