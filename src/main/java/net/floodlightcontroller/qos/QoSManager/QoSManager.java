package net.floodlightcontroller.qos.QoSManager;

import com.google.common.util.concurrent.ListenableFuture;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.staticCache.web.StaticCacheWebRoutable;
import net.floodlightcontroller.topology.ITopologyService;
import net.floodlightcontroller.util.OFMessageDamper;
import org.projectfloodlight.openflow.protocol.OFPacketQueue;
import org.projectfloodlight.openflow.protocol.OFQueueGetConfigReply;
import org.projectfloodlight.openflow.protocol.OFQueueGetConfigRequest;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class QoSManager implements IFloodlightModule, IQoSManagerService {

    // instance field
    // Floodlight Service
    protected IFloodlightProviderService floodlightProvider;
    protected IOFSwitchService switchService;
    protected ITopologyService topologyService;
    protected OFMessageDamper messageDamper;
    protected IRestApiService restApi;
    protected IDeviceService deviceService;
    protected IRoutingService routingEngineService;


    private ConcurrentHashMap<DatapathId, Vector<Long>> queueStatics;

    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IQoSManagerService.class);
        return l;
    }

    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> m = new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
        m.put(IQoSManagerService.class, this);
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
        restApi = context.getServiceImpl(IRestApiService.class);
        routingEngineService = context.getServiceImpl(IRoutingService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);

//        flowSetIdRegistry = StaticCacheManager.FlowSetIdRegistry.getInstance();
//        messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
//                EnumSet.of(OFType.FLOW_MOD),
//                OFMESSAGE_DAMPER_TIMEOUT);
    }

    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        restApi.addRestletRoutable(new StaticCacheWebRoutable());

    }



    private void getQueue(IOFSwitch sw){
        OFQueueGetConfigRequest cr = sw.getOFFactory().buildQueueGetConfigRequest().setPort(OFPort.of(1)).build(); /* Request queues on any port (i.e. don't care) */
        ListenableFuture<OFQueueGetConfigReply> future = sw.writeRequest(cr); /* Send request to switch 1 */
        try {
            /* Wait up to 10s for a reply; return when received; else exception thrown */
            OFQueueGetConfigReply reply = future.get(10, TimeUnit.SECONDS);
            System.out.println(reply);
            /* Iterate over all queues */
            for (OFPacketQueue q : reply.getQueues()) {
                OFPort p = q.getPort(); /* The switch port the queue is on */
                long id = q.getQueueId(); /* The ID of the queue */


            }
        } catch (InterruptedException | ExecutionException | TimeoutException e) { /* catch e.g. timeout */
            e.printStackTrace();
        }
    }

    @Override
    public Vector<Long> getQueues(IOFSwitch sw) {
        return this.queueStatics.get(sw);
    }
}
