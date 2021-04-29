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
        System.out.println(this.nw_cache_prefix_and_mask.getValue());
        List<OFAction> actions = new ArrayList<>();
        actions.add(host_setEthDst);
        actions.add(host_setIpv4Dst);
        actions.add(sw.getOFFactory().actions().buildOutput().setPort(OFPort.of(3)).build());

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
                .setIdleTimeout(3600)
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
        OFActionSetField cache_setEthSrc = sw.getOFFactory().actions().buildSetField()
                .setField(
                        sw.getOFFactory().oxms().buildEthSrc()
                                .setValue(MacAddress.of("2e:d9:c4:94:dc:5e"))
                                .build()
                )
                .build();
        OFActionSetField cache_setIpv4Src = sw.getOFFactory().actions().buildSetField()
                .setField(
                        sw.getOFFactory().oxms().buildIpv4Src()
                                .setValue(this.nw_dst_prefix_and_mask.getValue())
                                .build()
                )
                .build();
        List<OFAction> actions_Cache = new ArrayList<>();
        actions_Cache.add(cache_setEthSrc);
        actions_Cache.add(cache_setIpv4Src);
        actions_Cache.add(sw.getOFFactory().actions().buildOutput().setPort(OFPort.of(2)).build());

        OFInstructionApplyActions instruction_cache = sw.getOFFactory().instructions().buildApplyActions()
                .setActions(actions_Cache)
                .build();

        List<OFInstruction> instructions_cache = new ArrayList<>();
        instructions_cache.add(instruction_cache);
        System.out.println("---------------------------Apply the Cache strategy Successfully-------------------------------------------");
        Match match_cache = sw.getOFFactory().buildMatch()
                .setExact(MatchField.IN_PORT, OFPort.of(3))
                .setExact(MatchField.ETH_TYPE, EthType.IPv4)
                .setExact(MatchField.IPV4_SRC, this.nw_cache_prefix_and_mask.getValue())
                .setExact(MatchField.IP_PROTO, IpProtocol.TCP)
                .setExact(MatchField.IPV4_DST, this.nw_src_prefix_and_mask.getValue())
                .setExact(MatchField.TCP_SRC, TransportPort.of(8080))
                .build();

        //wrf: change the hardtimeout
        OFFlowAdd flowAdd_cache = sw.getOFFactory().buildFlowAdd()
                .setMatch(match_cache)
                .setIdleTimeout(3600)
                .setHardTimeout(3600)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setCookie(pi.getCookie())
                .setPriority(32768)
                .setInstructions(instructions_cache)
                .setTableId(TableId.ZERO)
                .build();

        // OFBadMatchErrorMsgVer13, code=BAD_PREREQ
        sw.write(flowAdd_cache);

    }
}
