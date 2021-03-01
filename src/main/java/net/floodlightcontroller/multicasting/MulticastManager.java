package net.floodlightcontroller.multicasting;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPAddress;
import org.projectfloodlight.openflow.types.IpProtocol;

import java.util.*;


public class MulticastManager implements IOFMessageListener, IFloodlightModule {

    // Instance field
    protected IFloodlightProviderService floodlightProvider;
    private MulticastInfoTable multicastInfoTable = new MulticastInfoTable();

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
                System.out.println("Destination Address is: " + ((IPv4)eth.getPayload()).getDestinationAddress());

                IPAddress multicastGroupIPAddress = ((IPv4)eth.getPayload()).getDestinationAddress();
                IPAddress hostIPAddress = ((IPv4)eth.getPayload()).getSourceAddress();

                byte[] igmpPayload = eth.getPayload().serialize();
                System.out.println("Payload length = " + igmpPayload.length);
                // the total lengeth of this packet is 54, the previous 14(0-13) is for header, the rest 40 is for paylod, and the 46/32 is for record type
                if (igmpPayload[32] == 4){
                    System.out.println(igmpPayload + "IGMP join message");
                    if (multicastInfoTable.isEmpty()){  // empty multicast information table
                        HashSet<IPAddress> newMulticastGroup = new HashSet();
                        newMulticastGroup.add(hostIPAddress);
                        multicastInfoTable.put(multicastGroupIPAddress, newMulticastGroup);
                    } else{ // non-empty table
                        if (multicastInfoTable.containsValue(multicastGroupIPAddress)){
                            if (multicastInfoTable.get(multicastGroupIPAddress).contains(hostIPAddress)){   // host already join the multicast group
                                // nothing happen
                            } else {    // host has not joined the multicast group yes
                               multicastInfoTable.get(multicastGroupIPAddress).add(hostIPAddress);
                                // TODO: use algorithm to analyse
                                // getMulticastRoutingDecision(multicastInfoTable.keySet(multicastGroupIPAddress));
                            }
                        } else {    // multicast group IP address do not exist
                            HashSet<IPAddress> newMulticastGroup = new HashSet();
                            newMulticastGroup.add(hostIPAddress);
                            multicastInfoTable.put(multicastGroupIPAddress, newMulticastGroup);
                            // TODO: use algorithm to analyse
                            // getMulticastRoutingDecision(multicastInfoTable.keySet(multicastGroupIPAddress));
                        }
                    }
                } else if (igmpPayload[32] == 3){
                    System.out.println(igmpPayload + "IGMP leave message");
                }


            }
        }
        return Command.CONTINUE;
    }


    private void pushMulticastGroupRoute(){
        // TODO::
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
        return null;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        return null;
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
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
    }
}
