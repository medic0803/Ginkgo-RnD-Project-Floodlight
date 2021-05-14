package net.floodlightcontroller.staticCache.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.action.*;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;

import java.util.ArrayList;
import java.util.List;

@JsonSerialize(using = StaticCacheStrategySerilalizer.class)

public class StaticCacheStrategy {
    //kwm: local vareities
    // src_ip, dst_ip, cache_ip, dst_port, priority,cache_ip
    public int strategyid = 0;
    public IPv4Address nw_src_ipv4;
    public IPv4Address nw_dst_ipv4;
    public IPv4Address nw_cache_ipv4;
    public MacAddress nw_cache_dl_dst;
    public int priority = 0;

    // Complements
    public TransportPort tp_src;
    public TransportPort tp_dst;
    public OFPort src_inPort;
    public OFPort dst_inPort;
    public OFPort src_outPort;
    public OFPort dst_outPort;
    public DatapathId src_dpid;
    public DatapathId dst_dpid;
    public OFFlowAdd flowAdd_host;
    public OFFlowAdd flowAdd_cache;
    public Match match_host;
    public Match match_cache;

    //Constructor
    public StaticCacheStrategy() {
        this.strategyid = this.genID();
        this.nw_src_ipv4 = IPv4Address.NONE;
        this.nw_dst_ipv4 = IPv4Address.NONE;
        this.nw_cache_ipv4 = IPv4Address.NONE;
        this.nw_cache_dl_dst = MacAddress.NONE;
        this.tp_src = TransportPort.NONE;
        this.tp_dst = TransportPort.NONE;
        this.priority = 0;
    }
    //kwm: method to check if is same
    public boolean isSameAs(StaticCacheStrategy scs){
        if (!this.nw_cache_dl_dst.equals(scs.nw_cache_dl_dst)
        ||!this.nw_cache_ipv4.equals(scs.nw_cache_ipv4)
        ||!this.nw_src_ipv4.equals(scs.nw_src_ipv4)
        ||!this.nw_dst_ipv4.equals(scs.nw_dst_ipv4)
        ||!this.tp_dst.equals(scs.tp_dst)
        ||this.priority != scs.priority){
            return false;
        }
        return true;
    }

    public int genID() {
        int uid = this.hashCode();
        if (uid < 0) {
            uid = Math.abs(uid);
            uid = uid * 15551;
        }
        return uid;
    }

    /**
     * This method is used to determine whether a pakcet_in information
     * would match an existing static cache strategy,
     * and it has two situation, forward flow: the packet_in from host
     * inverse flow: the packet_in from cache
     * @param srcAddress                Source Address in a packet_in, which stands for target server or cache server's IPv4Address
     * @param dstAddress                Destination Address in a pakcet_in, which stands for host or cache server's IPv4Address
     * @param tp_dst                    Transport port for destination
     * @param hostOrCache               Indicate the packet_in is from host or cache to determine which statement would be processed
     * @return
     */
    public StaticCacheStrategy ifMatch(IPv4Address srcAddress, IPv4Address dstAddress, TransportPort tp_dst, String hostOrCache) {
        switch (hostOrCache) {
            case "HOST":
                if (srcAddress.equals(this.nw_src_ipv4) && dstAddress.equals(this.nw_dst_ipv4) && tp_dst.equals(this.tp_dst)) {
                    return this;
                }
                break;
            case "CACHE":
                if (srcAddress.equals(this.nw_cache_ipv4) && dstAddress.equals(this.nw_src_ipv4) && tp_dst.equals(this.tp_src)) {
                    return this;
                }
                break;
        }
        return null;
    }

    /**
     * This method should be processed in pushRoute method in StaticCacheManager,
     * which used to compose the instructions and FlowAdd for the pinSwitch of the cache server.
     * There are three actions needed to put in the instructions,
     * modify the source IPv4Address, modify the source MACAddress which are used to implement redirection of packet to cache server
     * set outPort to guide the flow
     * @param src_outPort                   Source(Host)'s out OFPort on it's attachment point switch
     */
    public void completeStrategy_host(IOFSwitch sw, OFPacketIn pi, OFPort src_outPort, OFActionSetQueue setQueue) {
        this.src_outPort = src_outPort;

        OFActionSetField host_setEthDst = sw.getOFFactory().actions().buildSetField()
                .setField(
                        sw.getOFFactory().oxms().buildEthDst()
                                .setValue(this.nw_cache_dl_dst)
                                .build()
                )
                .build();
        OFActionSetField host_setIpv4Dst = sw.getOFFactory().actions().buildSetField()
                .setField(
                        sw.getOFFactory().oxms().buildIpv4Dst()
                                .setValue(this.nw_cache_ipv4)
                                .build()
                )
                .build();
        List<OFAction> actions_host = new ArrayList<>();
        actions_host.add(host_setEthDst);
        actions_host.add(host_setIpv4Dst);
        actions_host.add(sw.getOFFactory().actions().buildOutput().setPort(src_outPort).build());
        actions_host.add(setQueue);

        OFInstructionApplyActions host_instruction = sw.getOFFactory().instructions().buildApplyActions()
                .setActions(actions_host)
                .build();

        List<OFInstruction> instructions = new ArrayList<>();
        instructions.add(host_instruction);
        System.out.println("---------------------------Apply the strategy Successfully-------------------------------------------");
        match_host = sw.getOFFactory().buildMatch()
                .setExact(MatchField.IN_PORT, this.src_inPort)
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IPV4_SRC, this.nw_src_ipv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.TCP)
                .setExact(MatchField.IPV4_DST, this.nw_dst_ipv4)
                .setExact(MatchField.TCP_SRC, this.tp_src)
                .setExact(MatchField.TCP_DST, this.tp_dst)
                .build();

        //wrf: change the hardtimeout
        flowAdd_host = sw.getOFFactory().buildFlowAdd()
                .setMatch(match_host)
                .setIdleTimeout(3600)
                .setHardTimeout(3600)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setCookie(pi.getCookie())
                .setPriority(32768)
                .setInstructions(instructions)
                .setTableId(TableId.ZERO)
                .build();
    }

    /**
     * This method should be processed in pushRoute method in StaticCacheManager,
     * which used to compose the instructions and FlowAdd for the pinSwitch of the cache server.
     * There are three actions needed to put in the instructions,
     * modify the destination IPv4Address, modify the destination MACAddress which are used to implement Reverse Proxy
     * set outPort to guide the flow
     * @param dst_outPort                   Destination(Cache server)'s out OFPort on it's attachment point switch
     */
    public void completeStrategy_cache(IOFSwitch sw, OFPacketIn pi, OFPort dst_outPort, OFActionSetQueue setQueue) {
        this.dst_outPort = dst_outPort;

        OFActionSetField cache_setEthSrc = sw.getOFFactory().actions().buildSetField()
                .setField(
                        sw.getOFFactory().oxms().buildEthSrc()
                                .setValue(nw_cache_dl_dst)
                                .build()
                )
                .build();
        OFActionSetField cache_setIpv4Src = sw.getOFFactory().actions().buildSetField()
                .setField(
                        sw.getOFFactory().oxms().buildIpv4Src()
                                .setValue(this.nw_dst_ipv4)
                                .build()
                )
                .build();
        List<OFAction> actions_Cache = new ArrayList<>();
        actions_Cache.add(cache_setEthSrc);
        actions_Cache.add(cache_setIpv4Src);
        actions_Cache.add(sw.getOFFactory().actions().buildOutput().setPort(dst_outPort).build());
        actions_Cache.add(setQueue);

        OFInstructionApplyActions instruction_cache = sw.getOFFactory().instructions().buildApplyActions()
                .setActions(actions_Cache)
                .build();

        List<OFInstruction> instructions_cache = new ArrayList<>();
        instructions_cache.add(instruction_cache);

        match_cache = sw.getOFFactory().buildMatch()
                .setExact(MatchField.IN_PORT, dst_inPort)
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IPV4_SRC, this.nw_cache_ipv4)
                .setExact(MatchField.IP_PROTO, IpProtocol.TCP)
                .setExact(MatchField.IPV4_DST, this.nw_src_ipv4)
                .setExact(MatchField.TCP_SRC, this.tp_dst)
                .setExact(MatchField.TCP_DST, this.tp_src)
                .build();

        //wrf: change the hardtimeout
        flowAdd_cache = sw.getOFFactory().buildFlowAdd()
                .setMatch(match_cache)
                .setIdleTimeout(3600)
                .setHardTimeout(3600)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setCookie(pi.getCookie())
                .setPriority(32768)
                .setInstructions(instructions_cache)
                .setTableId(TableId.ZERO)
                .build();


    }

    @Override
    public String toString() {
        return "StaticCacheStrategy{" +
                "strategyid=" + strategyid +
                ", nw_src_prefix_and_mask=" + nw_src_ipv4 +
                ", nw_dst_prefix_and_mask=" + nw_dst_ipv4 +
                ", nw_cache_prefix_and_mask=" + nw_cache_ipv4 +
                ", nw_cache_dl_dst=" + nw_cache_dl_dst +
                ", tp_dst=" + tp_dst +
                ", priority=" + priority +
                '}';
    }

}
