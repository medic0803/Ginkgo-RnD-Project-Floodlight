package net.floodlightcontroller.multicasting;

import net.floodlightcontroller.core.module.IFloodlightService;
import org.projectfloodlight.openflow.types.IPv4Address;

public interface IFetchMulticastGroupService extends IFloodlightService {
    public boolean ifMulticastAddressExist(IPv4Address dstAddress);
}
