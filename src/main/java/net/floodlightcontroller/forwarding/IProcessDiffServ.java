package net.floodlightcontroller.forwarding;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.packet.Ethernet;

public interface IProcessDiffServ extends IFloodlightService {
    public void setPriorityOfStream(Ethernet eth);
}
