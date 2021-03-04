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
import net.floodlightcontroller.forwarding.Forwarding;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.routing.ForwardingBase;
import net.floodlightcontroller.routing.Path;
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


public class MulticastManager implements IOFMessageListener, IFloodlightModule, IFetchMulticastGroupService {

    // Instance field
    protected IFloodlightProviderService floodlightProvider;
    protected IOFSwitchService switchService;
    protected static Logger log = LoggerFactory.getLogger(MulticastManager.class);
    private MulticastInfoTable multicastInfoTable = new MulticastInfoTable();
    private HashMap<IPv4Address, DatapathId> pinSwitchIPv4AddressMatchMap = new HashMap<>();

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
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        Ethernet eth =
                IFloodlightProviderService.bcStore.get(cntx,
                        IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
        if (eth.getEtherType() == EthType.IPv4){
            if (((IPv4)eth.getPayload()).getProtocol() == IpProtocol.IGMP){

                // TODO: Delete
                System.out.println("-----------receive igmp packet ----------------");
                System.out.println("Source Address is: " + ((IPv4)eth.getPayload()).getSourceAddress());
                System.out.println("Switch ID is " + sw.getId());

                byte[] igmpPayload = eth.getPayload().serialize();
                byte[] multicastAddress = new byte[4];
                System.arraycopy(igmpPayload, 36, multicastAddress, 0, 4);
                IPv4Address multicastGroupIPAddress = IPv4Address.of(multicastAddress);
                IPv4Address hostIPAddress = ((IPv4)eth.getPayload()).getSourceAddress();

                System.out.println("Destination Address is: " + multicastGroupIPAddress);
                System.out.println("Payload length = " + igmpPayload.length);
                // the total lengeth of this packet is 54, the previous 14(0-13) is for header, the rest 40 is for paylod, and the 46/32 is for record type
                if (igmpPayload[32] == 4){
                    System.out.println(igmpPayload + "IGMP join message");

                    if (multicastInfoTable.isEmpty()){  // empty multicast information table
                        HashSet<IPv4Address> newMulticastGroup = new HashSet();
                        newMulticastGroup.add(hostIPAddress);
                        multicastInfoTable.put(multicastGroupIPAddress, newMulticastGroup);


                        // A new host join, add it's match item
                        pinSwitchIPv4AddressMatchMap.put(hostIPAddress, sw.getId());
                    } else{ // non-empty table
                        if (multicastInfoTable.containsValue(multicastGroupIPAddress)){
                            if (multicastInfoTable.get(multicastGroupIPAddress).contains(hostIPAddress)){   // host already join the multicast group
                                // nothing happen
                            } else {    // host has not joined the multicast group yes
                               multicastInfoTable.get(multicastGroupIPAddress).add(hostIPAddress);
                                // A new host join, add it's match item
                                pinSwitchIPv4AddressMatchMap.put(hostIPAddress, sw.getId());
                            }
                        } else {    // multicast group IP address do not exist
                            HashSet<IPv4Address> newMulticastGroup = new HashSet();
                            newMulticastGroup.add(hostIPAddress);
                            multicastInfoTable.put(multicastGroupIPAddress, newMulticastGroup);
                            // A new host join, add it's match item
                            pinSwitchIPv4AddressMatchMap.put(hostIPAddress, sw.getId());
                        }
                    }
                } else if (igmpPayload[32] == 3){
                    System.out.println(igmpPayload + "IGMP leave message");

                    // host leave, delete the match item
                    pinSwitchIPv4AddressMatchMap.remove(hostIPAddress);
                }

            } else if (multicastInfoTable.containsKey(((IPv4)eth.getPayload()).getDestinationAddress())){
                // TODO: use algorithm to analyse
                IPv4Address streamingSourceIPAddress = ((IPv4)eth.getPayload()).getSourceAddress();

                // getMulticastRoutingDecision(streamingSourceIPAddress ,multicastInfoTable.keySet(multicastGroupIPAddress));
            }
        }
        return Command.CONTINUE;
    }


    public boolean pushMulticastingRoute(Path route, Match match, OFPacketIn pi,
                             DatapathId pinSwitch, U64 cookie, FloodlightContext cntx,
                             boolean requestFlowRemovedNotification, OFFlowModCommand flowModCommand, boolean packetOutSent) {

        List<NodePortTuple> switchPortList = route.getPath();

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

            OFActionOutput.Builder aob = sw.getOFFactory().actions().buildOutput();
            List<OFAction> actions = new ArrayList<>();
            Match.Builder mb = MatchUtils.convertToVersion(match, sw.getOFFactory().getVersion());

            // set input and output ports on the switch
            OFPort outPort = switchPortList.get(indx).getPortId();
            OFPort inPort = switchPortList.get(indx - 1).getPortId();
            if (FLOWMOD_DEFAULT_MATCH_IN_PORT) {
                mb.setExact(MatchField.IN_PORT, inPort);
            }
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
                        new Object[] {indx,
                                sw,
                                fmb.getMatch().get(MatchField.IN_PORT),
                                outPort });
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

            /* Push the packet out the first hop switch */
            if (!packetOutSent && sw.getId().equals(pinSwitch) &&
                    !fmb.getCommand().equals(OFFlowModCommand.DELETE) &&
                    !fmb.getCommand().equals(OFFlowModCommand.DELETE_STRICT)) {
                /* Use the buffered packet at the switch, if there's one stored */
                log.debug("Push packet out the first hop switch");
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
     * @param sw switch that generated the packet-in, and from which packet-out is sent
     * @param pi packet-in
     * @param outport output port
     * @param useBufferedPacket use the packet buffered at the switch, if possible
     * @param cntx context of the packet
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
                    new Object[] {sw, pi});
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
        messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
                EnumSet.of(OFType.FLOW_MOD),
                OFMESSAGE_DAMPER_TIMEOUT);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }

    @Override
    public MulticastInfoTable getmulticastInforTable() {
        return this.multicastInfoTable;
    }
}
