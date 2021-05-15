package net.floodlightcontroller.multicasting.web;

import net.floodlightcontroller.core.types.NodePortTuple;
import net.floodlightcontroller.multicasting.IFetchMulticastGroupService;
import net.floodlightcontroller.multicasting.MulticastTree;
import net.floodlightcontroller.routing.Path;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import java.util.*;

/**
 * @author Michael Kang
 * @create 2021-05-12  02:16 PM
 */
public class LinkResource extends ServerResource {
    @Get("json")
    public HashMap<IPv4Address,  ArrayList<List<NodePortTuple>>> retrieve(){

        IFetchMulticastGroupService groupService =
                (IFetchMulticastGroupService) getContext().getAttributes().
                        get(IFetchMulticastGroupService.class.getCanonicalName());

        HashMap<IPv4Address, ArrayList<List<NodePortTuple>>> returnMap = new HashMap<>();
        HashMap<IPv4Address, MulticastTree> groupPathTree = new HashMap<>();

        groupPathTree = groupService.getGroupPathTree();

        for (Map.Entry<IPv4Address, MulticastTree> ipGroup: groupPathTree.entrySet())
        {
            HashMap<IPv4Address, Path> pathTable = ipGroup.getValue().getPathList();
            ArrayList<List<NodePortTuple>> paths = new ArrayList<>();
            Collection<Path> values = pathTable.values();
            for (Path x:values) {
                List<NodePortTuple> path = x.getPath();
                paths.add(path);
            }
            returnMap.put(ipGroup.getKey(),paths);
        }
        return returnMap;
    }
}
