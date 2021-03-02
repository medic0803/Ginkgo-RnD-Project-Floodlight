package net.floodlightcontroller.multicasting;

import net.floodlightcontroller.core.module.IFloodlightService;

public interface IFetchMulticastGroupService extends IFloodlightService {
    public MulticastInfoTable getmulticastInforTable();
}
