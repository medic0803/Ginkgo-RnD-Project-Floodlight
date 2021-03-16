package net.floodlightcontroller.multicasting;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.packet.IPv4;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

public class MulticastSource {
    private DatapathId srcId;
    private OFPort srcPort;
    private U64 cookie;
    private Match match;
    private FloodlightContext cntx;
    private OFPacketIn pi;
    private IPv4Address srcAddress;

    public MulticastSource(DatapathId srcId, OFPort srcPort, U64 cookie, Match match, FloodlightContext cntx, OFPacketIn pi, IPv4Address srcAddress) {
        this.srcId = srcId;
        this.srcPort = srcPort;
        this.cookie = cookie;
        this.match = match;
        this.cntx = cntx;
        this.pi = pi;
        this.srcAddress = srcAddress;
    }

    public DatapathId getSrcId() {
        return srcId;
    }

    public OFPort getSrcPort() {
        return srcPort;
    }

    public U64 getCookie() {
        return cookie;
    }

    public Match getMatch() {
        return match;
    }

    public FloodlightContext getCntx() {
        return cntx;
    }

    public OFPacketIn getPi() {
        return pi;
    }

    public IPv4Address getSrcAddress() {
        return srcAddress;
    }
}
