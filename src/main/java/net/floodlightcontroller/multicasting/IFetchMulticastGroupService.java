package net.floodlightcontroller.multicasting;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.types.IPv4Address;

import java.util.HashMap;

public interface IFetchMulticastGroupService extends IFloodlightService {
    public boolean ifMulticastAddressExist(IPv4Address dstAddress);

    /**
     * key group ip
     * @return
     */
    public HashMap<IPv4Address,MulticastTree> getGroupPathTree();
}
