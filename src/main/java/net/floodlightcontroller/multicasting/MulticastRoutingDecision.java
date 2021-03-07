package net.floodlightcontroller.multicasting;

import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.routing.Path;
import net.floodlightcontroller.routing.PathId;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.ArrayList;
import java.util.List;

public class MulticastRoutingDecision {

    public enum MulticastRoutingAction{
        JOIN_WITHOUT_RP, JOIN_WITH_RP, LEAVE
    }

    private MulticastRoutingAction routingAction = MulticastRoutingAction.LEAVE;
    private Path uPath;
    private DatapathId rP;

    public MulticastRoutingDecision() { }

    public Path getuPath() {
        return uPath;
    }

    public DatapathId getrP() {
        return rP;
    }
    public MulticastRoutingDecision(MulticastRoutingAction routingAction, Path uPath, DatapathId rP) {
        this.routingAction = routingAction;
        this.rP = rP;
        this.uPath = getUpdatePath(uPath,rP);

    }


    public MulticastRoutingDecision(MulticastRoutingAction routingAction, Path uPath) {
        this.routingAction = routingAction;
        this.uPath = uPath;
    }


    /**
     * JOIN_WITHOUT_RP:
     * JOIN_WITH_RP: cut the path need to be updated, and the path start from RP
     *
     * @param wholePath
     * @return
     */
    private Path getUpdatePath(Path wholePath, DatapathId rp){
        if (MulticastRoutingAction.JOIN_WITHOUT_RP.equals(routingAction)){
            return wholePath;
        }else if (MulticastRoutingAction.JOIN_WITH_RP.equals(routingAction)){
            List<NodePortTuple> nptList = wholePath.getPath();
            //get the path from the Rp
            List<NodePortTuple> ansList = new ArrayList<>();
            boolean ready2getPath = false;
            for (int i = 0; i < nptList.size(); i++) {
                nptList.get(i);
                if (nptList.get(i).getNodeId().equals(rp)){
                    ready2getPath = true;
                };
                if (ready2getPath){
                    ansList.add(nptList.get(i));
                }
            }
            PathId id = new PathId(nptList.get(0).getNodeId(), nptList.get(nptList.size()-1).getNodeId());
            Path ansPath = new Path(id,ansList);
            return ansPath;
        }else{
            //kwmtodo: leave
            return wholePath;
        }
    }

    public MulticastRoutingAction getRoutingAction() {
        return routingAction;
    }
}
