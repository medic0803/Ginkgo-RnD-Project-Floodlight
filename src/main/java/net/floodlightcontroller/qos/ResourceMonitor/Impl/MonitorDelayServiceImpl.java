package net.floodlightcontroller.qos.ResourceMonitor.Impl;

import net.floodlightcontroller.core.*;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.linkdiscovery.ILinkDiscoveryService;
import net.floodlightcontroller.linkdiscovery.Link;
import net.floodlightcontroller.linkdiscovery.internal.LinkInfo;
import net.floodlightcontroller.qos.ResourceMonitor.MonitorDelayService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.types.DatapathId;
import org.python.modules.time.Time;
import org.sdnplatform.sync.internal.SyncTorture;

import java.util.*;
import java.util.Map.Entry;

/**
 * @author Michael Kang
 * @create 2021-01-29 下午 06:03
 */
public class MonitorDelayServiceImpl implements MonitorDelayService, IFloodlightModule, IOFMessageListener, IOFSwitchListener {

    private IThreadPoolService threadPoolServcie;
    private IOFSwitchService switchService;
    private ILinkDiscoveryService linkDiscoveryService;
    private IFloodlightProviderService floodlightProviderService;


    /**
     * This is the method Floodlight uses to call listeners with OpenFlow messages
     *
     * @param sw   the OpenFlow switch that sent this message
     * @param msg  the message
     * @param cntx a Floodlight message context object you can use to pass
     *             information between listeners
     * @return the command to continue or stop the execution
     */
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
        return null;
    }

    /**
     * The name assigned to this listener
     *
     * @return
     */
    @Override
    public String getName() {
        return "LinkDelayMonitor";
    }

    /**
     * Check if the module called name is a callback ordering prerequisite
     * for this module.  In other words, if this function returns true for
     * the given name, then this listener will be called after that
     * message listener.
     *
     * @param type the object type to which this applies
     * @param name the name of the module
     * @return whether name is a prerequisite.
     */
    @Override
    public boolean isCallbackOrderingPrereq(OFType type, String name) {
        return false;
    }

    /**
     * Check if the module called name is a callback ordering post-requisite
     * for this module.  In other words, if this function returns true for
     * the given name, then this listener will be called before that
     * message listener.
     *
     * @param type the object type to which this applies
     * @param name the name of the module
     * @return whether name is a post-requisite.
     */
    @Override
    public boolean isCallbackOrderingPostreq(OFType type, String name) {
        return false;
    }

    /**
     * Fired when switch becomes known to the controller cluster. I.e.,
     * the switch is connected at some controller in the cluster
     *
     * @param switchId the datapath Id of the new switch
     */
    @Override
    public void switchAdded(DatapathId switchId) {

    }

    /**
     * Fired when a switch disconnects from the cluster,
     *
     * @param switchId the datapath Id of the switch
     */
    @Override
    public void switchRemoved(DatapathId switchId) {

    }

    /**
     * Fired when a switch becomes active *on the local controller*, I.e.,
     * the switch is connected to the local controller and is in MASTER mode
     *
     * @param switchId the datapath Id of the switch
     */
    @Override
    public void switchActivated(DatapathId switchId) {

    }

    /**
     * Fired when a port on a known switch changes.
     * <p>
     * A user of this notification needs to take care if the port and type
     * information is used directly and if the collection of ports has been
     * queried as well. This notification will only be dispatched after the
     * the port changes have been committed to the IOFSwitch instance. However,
     * if a user has previously called {@link IOFSwitch#getPorts()} or related
     * method a subsequent update might already be present in the information
     * returned by getPorts.
     *
     * @param switchId
     * @param port
     * @param type
     */
    @Override
    public void switchPortChanged(DatapathId switchId, OFPortDesc port, PortChangeType type) {

    }

    /**
     * Fired when any non-port related information (e.g., attributes,
     * features) change after a switchAdded
     * TODO: currently unused
     *
     * @param switchId
     */
    @Override
    public void switchChanged(DatapathId switchId) {

    }

    /**
     * Fired when receive a ROLE_STATUS message,
     * TODO: At Master to Slave transitions, switch become deactivated.
     *
     * @param switchId
     */
    @Override
    public void switchDeactivated(DatapathId switchId) {

    }

    /**
     * Return the list of interfaces that this module implements.
     * All interfaces must inherit IFloodlightService
     *
     * @return
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(MonitorDelayService.class);
        return l;
    }

    /**
     * Instantiate (as needed) and return objects that implement each
     * of the services exported by this module.  The map returned maps
     * the implemented service to the object.  The object could be the
     * same object or different objects for different exported services.
     *
     * @return The map from service interface class to service implementation
     */
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        Map<Class<? extends IFloodlightService>, IFloodlightService> l = new HashMap<>();
        l.put(MonitorDelayService.class, this);
        return l;
    }

    /**
     * Get a list of Modules that this module depends on.  The module system
     * will ensure that each these dependencies is resolved before the
     * subsequent calls to init().
     *
     * @return The Collection of IFloodlightServices that this module depends
     * on.
     */
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
        Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
        l.add(IFloodlightProviderService.class);
        l.add(IThreadPoolService.class);
        l.add(IOFSwitchService.class);
        l.add(ILinkDiscoveryService.class);
        return l;
    }

    /**
     * This is a hook for each module to do its <em>internal</em> initialization,
     * e.g., call setService(context.getService("Service"))
     * <p>
     * All module dependencies are resolved when this is called, but not every module
     * is initialized.
     *
     * @param context
     * @throws FloodlightModuleException
     */
    @Override
    public void init(FloodlightModuleContext context) throws FloodlightModuleException {
        floodlightProviderService = context.getServiceImpl(IFloodlightProviderService.class);
        switchService = context.getServiceImpl(IOFSwitchService.class);
        linkDiscoveryService = context.getServiceImpl(ILinkDiscoveryService.class);
        threadPoolServcie = context.getServiceImpl(IThreadPoolService.class);
    }

    /**
     * This is a hook for each module to do its <em>external</em> initializations,
     * e.g., register for callbacks or query for state in other modules
     * <p>
     * It is expected that this function will not block and that modules that want
     * non-event driven CPU will spawn their own threads.
     *
     * @param context
     * @throws FloodlightModuleException
     */
    @Override
    public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
        boolean flag = true;
        while(flag){
            System.out.println(">>>>");
            System.out.println(">>>>");
            System.out.println(">>>>");
            Time.sleep(5);
            if (1==testFunc()){
                flag = false;
            }
            System.out.println("<<<<<<<<<<<");
            System.out.println("<<<<<<<<<<<");
            System.out.println("<<<<<<<<<<<");
        }
    }


    //kwmtodo:Understand the Test.
    public int testFunc(){
        Map<Link, LinkInfo> linkInfo = linkDiscoveryService.getLinks();
        Iterator<Entry<Link, LinkInfo>> iter = linkInfo.entrySet().iterator();
        while(iter.hasNext()){
            Entry<Link, LinkInfo> node = iter.next();
            System.out.println("=======================================");
            System.out.println("源交换机:"+node.getKey().getSrc().toString()+",源端口："+node.getKey().getSrcPort());
            System.out.println("目的交换机:"+node.getKey().getDst().toString()+",目的端口："+node.getKey().getDstPort());
            System.out.println("链路时延:"+node.getKey().getLatency());
            System.out.println("当前时延："+node.getValue().getCurrentLatency());
            System.out.println("=======================================");
        }
        if (linkInfo.isEmpty()){
            return 0;
        }else {
            return 1;
        }
    }
}
