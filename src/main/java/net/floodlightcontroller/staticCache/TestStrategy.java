package net.floodlightcontroller.staticCache;

import net.floodlightcontroller.staticCache.web.StaticCacheStrategy;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.TransportPort;

/**
 * @author Michael Kang
 * @create 2021-05-03 下午 05:12
 */
public class TestStrategy {
    public static void main(String[] args) {
        StaticCacheStrategy tempStrategy = new StaticCacheStrategy();
        tempStrategy.nw_src_ipv4 = IPv4Address.of("10.0.0.2");
        tempStrategy.nw_dst_ipv4 = IPv4Address.of("10.0.0.1");
        tempStrategy.nw_cache_ipv4 = IPv4Address.of("10.0.0.3");
        tempStrategy.tp_dst = TransportPort.of(8080);
        tempStrategy.nw_cache_dl_dst = MacAddress.of("be:5f:68:79:d3:44");
        System.out.println(tempStrategy);
    }
}
