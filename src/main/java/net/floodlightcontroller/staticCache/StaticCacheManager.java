package net.floodlightcontroller.staticCache;

import com.sun.deploy.security.MacOSXDeployNTLMAuthCallback;
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.Device;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.staticCache.web.StaticCacheStrategy;
import net.floodlightcontroller.staticCache.web.StaticCacheWebRoutable;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.OFMessageDamper;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.*;

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
    protected IDeviceService deviceService;

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
                        byte[] ipv4Packet = eth.getPayload().serialize();
                        byte[] raw_tp_src = new byte[2];
                        byte[] raw_tp_dst = new byte[2];
                        System.arraycopy(ipv4Packet, 20, raw_tp_src, 0, 2);
                        System.arraycopy(ipv4Packet, 22, raw_tp_dst, 0, 2);

                        int tp_src= (int) ( ((raw_tp_src[0] & 0xFF)<<8)
                                |(raw_tp_src[1] & 0xFF));

                        int tp_dst= (int) ( ((raw_tp_dst[0] & 0xFF)<<8)
                                |(raw_tp_dst[1] & 0xFF));

                        log.info("The protocol is " + ((IPv4) eth.getPayload()).getProtocol().toString());
                        log.info("The source port is " + tp_src);
                        log.info("The destination port is " + tp_dst);
                        IPv4Address srcAddress = ((IPv4) eth.getPayload()).getSourceAddress();
                        IPv4Address dstAddress = ((IPv4) eth.getPayload()).getDestinationAddress();
//                            System.out.println(srcAddress.asCidrMaskLength());
//                            srcAddress.getLength()
//                            srcAddress.asCidrMaskLength();
                        log.info("src ip is " + srcAddress);
                        log.info("dst ip is " + dstAddress);
                        if (tp_dst == 80 || tp_dst == 8080 || tp_dst  == 8081 || tp_dst == 9098){
                            log.info("Receive a HTTP packet");
                            //wrf: 匹配策略表，一旦匹配，下发修改流表选项
//                            IPv4Address srcAddress = ((IPv4) eth.getPayload()).getSourceAddress();
//                            IPv4Address dstAddress = ((IPv4) eth.getPayload()).getDestinationAddress();
//                            System.out.println(srcAddress.asCidrMaskLength());
//                            srcAddress.getLength()
//                            srcAddress.asCidrMaskLength();
                            log.info("src ip is " + srcAddress);
                            log.info("dst ip is " + dstAddress);
                            StaticCacheStrategy matched_strategy;
                            for (StaticCacheStrategy strategy : strategies){
                                matched_strategy = strategy.ifMatch(srcAddress, dstAddress, TransportPort.of(tp_dst));
                                if (matched_strategy != null) {
                                   //wrf: apply the strategy
//                                    matched_strategy.applyStrategy();
                                    System.out.println("match the strategy");
                                    log.info("match the strategy");
                                    matched_strategy.applyStrategy(sw, pi);
                                    break;
                                }
                            }
                            //wrf: 下发流表，action设置为修改dst address和MAC地址

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
        deviceService = context.getServiceImpl(IDeviceService.class);
        //wrf: detele pre-defined strategy
        StaticCacheStrategy tempStrategy = new StaticCacheStrategy();
        tempStrategy.nw_src_prefix_and_mask = IPv4AddressWithMask.of("10.0.0.2/32");
        tempStrategy.nw_dst_prefix_and_mask = IPv4AddressWithMask.of("10.0.0.1/32");
        tempStrategy.nw_cache_prefix_and_mask = IPv4AddressWithMask.of("10.0.0.3/32");
        tempStrategy.tp_dst = TransportPort.of(80);
        tempStrategy.nw_cache_dl_dst = MacAddress.of("ff:ff:ff:ff:ff:ff");
        strategies.add(tempStrategy);
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
        for (IDevice device : devices){
            for (IPv4Address srcAddress : device.getIPv4Addresses()){
                if (srcAddress.equals(strategy.nw_cache_prefix_and_mask.getValue())){
                    strategy.nw_cache_dl_dst = device.getMACAddress();
                }
            }
        }
        //wrf:思考如何匹配上策略表
        System.out.println("8*****************************************************");

        //wrftodo: invoke strategy implement method.
    }

    @Override
    public List<StaticCacheStrategy> getStrategies() {
        return this.strategies;
    }
}
