package net.floodlightcontroller.staticCache.web;

import org.projectfloodlight.openflow.types.*;

public class StaticCacheStrategy {
    //kwm: local vareities
    // src_ip, dst_ip, cache_ip, dst_port, priority,cache_ip
    public int ruleid = 0;
    public IPv4AddressWithMask nw_src_prefix_and_mask;
    public IPv4AddressWithMask nw_dst_prefix_and_mask;
    public TransportPort tp_dst;
    public int priority = 0;

    //kwm: 空参的构造器
    public StaticCacheStrategy( ) {
        this.ruleid = 0;
        this.nw_src_prefix_and_mask = IPv4AddressWithMask.NONE;
        this.nw_dst_prefix_and_mask = IPv4AddressWithMask.NONE;
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
}
