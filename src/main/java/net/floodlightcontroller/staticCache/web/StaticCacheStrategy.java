package net.floodlightcontroller.staticCache.web;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.projectfloodlight.openflow.types.*;

@JsonSerialize(using=StaticCacheStrategySerilalizer.class)

public class StaticCacheStrategy {
    //kwm: local vareities
    // src_ip, dst_ip, cache_ip, dst_port, priority,cache_ip
    public int strategyid = 0;
    public IPv4AddressWithMask nw_src_prefix_and_mask;
    public IPv4AddressWithMask nw_dst_prefix_and_mask;
    public IPv4AddressWithMask nw_cache_prefix_and_mask;
    public TransportPort tp_dst;
    public int priority = 0;

    //Constructor
    public StaticCacheStrategy( ) {
        this.strategyid = 0;
        this.nw_src_prefix_and_mask = IPv4AddressWithMask.NONE;
        this.nw_dst_prefix_and_mask = IPv4AddressWithMask.NONE;
        this.nw_cache_prefix_and_mask = IPv4AddressWithMask.NONE;
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
        if (srcAddress.equals(this.nw_src_prefix_and_mask.getValue()) && dstAddress.equals(this.nw_dst_prefix_and_mask.getValue()) && tp_dst.equals(this.tp_dst)){
            return this;
        }
        return null;
    }

    //wrf: 下发策略

}
