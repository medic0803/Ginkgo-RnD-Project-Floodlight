package net.floodlightcontroller.multicasting;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.routing.Path;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFGroup;
import org.projectfloodlight.openflow.types.U64;

public class MulticastHost {

    // instance fields
    private Path route;
    private Match match;
    private OFPacketIn pi;
    private DatapathId pinSwitch;
    private U64 cookie;
    private FloodlightContext cntx;
    private boolean requestFlowRemoveNotification;
    private OFFlowModCommand flowModCommand;
    private boolean packetOutSent;
}
