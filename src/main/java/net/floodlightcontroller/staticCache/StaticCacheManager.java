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
import net.floodlightcontroller.multicasting.MulticastManager;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticCache.web.StaticCacheStrategy;
import net.floodlightcontroller.staticCache.web.StaticCacheWebRoutable;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.OFMessageDamper;
import net.floodlightcontroller.util.OFMessageUtils;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.OFPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.util.*;

public class StaticCacheManager implements IOFMessageListener, IFloodlightModule, IStaticCacheService {

    // instance fied
    // Floodlight Service
    protected IFloodlightProviderService floodlightProvider;
    protected IOFSwitchService switchService;
    protected ITopologyService topologyService;
    protected OFMessageDamper messageDamper;
    protected IRestApiService restApi;


    protected List<StaticCacheStrategy> strategies;

    protected static Logger log = LoggerFactory.getLogger(StaticCacheManager.class);

    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        Ethernet eth = IFloodlightProviderService.bcStore.get(cntx, IFloodlightProviderService.CONTEXT_PI_PAYLOAD);

        switch (msg.getType()) {
            case PACKET_IN:
                // TODO:
                OFPacketIn pi = (OFPacketIn) msg;
                if (eth.getEtherType() == EthType.IPv4) {
                    if (((IPv4) eth.getPayload()).getProtocol() == IpProtocol.TCP || ((IPv4) eth.getPayload()).getProtocol() == IpProtocol.UDP) {
                        //wrf: 判断80端口，如果是src，那就是src在干什么，如果是
                        byte[] ipv4Packet = eth.getPayload().serialize();
                        byte[] rawSrcPort = new byte[2];
                        byte[] rawDstPort = new byte[2];
                        System.arraycopy(ipv4Packet, 20, rawSrcPort, 0, 2);
                        System.arraycopy(ipv4Packet, 22, rawDstPort, 0, 2);

                        int srcPort= (int) ( ((rawSrcPort[0] & 0xFF)<<8)
                                |(rawSrcPort[1] & 0xFF));

                        int dstPort= (int) ( ((rawDstPort[0] & 0xFF)<<8)
                                |(rawDstPort[1] & 0xFF));

                        log.info("The protocol is " + ((IPv4) eth.getPayload()).getProtocol().toString());
                        log.info("The source port is " + srcPort);
                        log.info("The destionation port is " + dstPort);
                        if (dstPort == 80 || dstPort == 8080 || dstPort  == 8081 || dstPort == 9098){
                            log.info("Receive a HTTP packet");
                            IPv4Address srcAddress = ((IPv4) eth.getPayload()).getSourceAddress();
                            IPv4Address dstAddress = ((IPv4) eth.getPayload()).getDestinationAddress();

                        } else {
                            return Command.STOP;
                        }
                    }

                }
                break;
        }
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
        System.out.println("8*****************************************************");

        //wrftodo: invoke strategy implement method.
    }

    @Override
    public List<StaticCacheStrategy> getStrategies() {
        return this.strategies;
    }
}
