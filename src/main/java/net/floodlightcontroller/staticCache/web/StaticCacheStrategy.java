package net.floodlightcontroller.staticCache.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.util.MatchUtils;
import net.floodlightcontroller.util.OFMessageUtils;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.action.*;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonSerialize(using = StaticCacheStrategySerilalizer.class)

public class StaticCacheStrategy {
    //kwm: local vareities
    // src_ip, dst_ip, cache_ip, dst_port, priority,cache_ip
    public int strategyid = 0;
    public IPv4AddressWithMask nw_src_prefix_and_mask;
    public IPv4AddressWithMask nw_dst_prefix_and_mask;
    public IPv4AddressWithMask nw_cache_prefix_and_mask;
    public MacAddress nw_cache_dl_dst;
    public TransportPort tp_dst;
    public int priority = 0;

    //Constructor
    public StaticCacheStrategy() {
        this.strategyid = this.genID();
        this.nw_src_prefix_and_mask = IPv4AddressWithMask.NONE;
        this.nw_dst_prefix_and_mask = IPv4AddressWithMask.NONE;
        this.nw_cache_prefix_and_mask = IPv4AddressWithMask.NONE;
        this.nw_cache_dl_dst = MacAddress.NONE;
        this.tp_dst = TransportPort.NONE;
        this.priority = 0;
    }

    public int genID() {
        int uid = this.hashCode();
        if (uid < 0) {
            uid = Math.abs(uid);
            uid = uid * 15551;
        }
        return uid;
    }

    //wrf: 匹配策略表
    public StaticCacheStrategy ifMatch(IPv4Address srcAddress, IPv4Address dstAddress, TransportPort tp_dst) {
        if (srcAddress.equals(this.nw_src_prefix_and_mask.getValue()) && dstAddress.equals(this.nw_dst_prefix_and_mask.getValue()) && tp_dst.equals(this.tp_dst)) {
            return this;
        }
        return null;
    }

    //wrf: 下发策略
    public void applyStrategy(IOFSwitch sw, OFPacketIn pi) {

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
                                .setValue(this.nw_cache_prefix_and_mask.getValue())
                                .build()
                )
                .build();
        List<OFAction> actions = new ArrayList<>();
        actions.add(host_setEthDst);
        actions.add(host_setIpv4Dst);
        OFInstructionApplyActions host_instruction = sw.getOFFactory().instructions().buildApplyActions()
                .setActions(actions)
                .build();

        List<OFInstruction> instructions = new ArrayList<>();
        instructions.add(host_instruction);
        System.out.println("---------------------------Apply the strategy Successfully-------------------------------------------");
        Match match = sw.getOFFactory().buildMatch()
                .setExact(MatchField.IN_PORT, OFMessageUtils.getInPort(pi))
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IPV4_SRC, this.nw_src_prefix_and_mask.getValue())
                .setExact(MatchField.IP_PROTO, IpProtocol.TCP)
                .setExact(MatchField.IPV4_DST, this.nw_dst_prefix_and_mask.getValue())
                .setExact(MatchField.TCP_DST, this.tp_dst)
                .build();

        //wrf: change the hardtimeout
        OFFlowAdd flowAdd = sw.getOFFactory().buildFlowAdd()
                .setMatch(match)
                .setIdleTimeout(5)
                .setHardTimeout(3600)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setCookie(pi.getCookie())
                .setPriority(32768)
                .setInstructions(instructions)
                .setTableId(TableId.ZERO)
                .build();

        // OFBadMatchErrorMsgVer13, code=BAD_PREREQ
        sw.write(flowAdd);

        //wrf:逆着回来的流表


    }
}
