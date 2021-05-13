package net.floodlightcontroller.qos.ResourceMonitor.pojo;

import org.projectfloodlight.openflow.types.DatapathId;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Michael Kang
 * @create 2021-05-07 上午 08:51
 */
public class LinkEntryTest {
    public static void main(String[] args) {
        Map<LinkEntry<DatapathId,DatapathId>,Integer> testMap = new HashMap<>();
        DatapathId switch1 =  DatapathId.of("1");
        DatapathId switch2 =  DatapathId.of("2");
        DatapathId switch3 =  DatapathId.of("3");

        DatapathId switch1_copy =  DatapathId.of("1");
        DatapathId switch2_copy =  DatapathId.of("2");

        LinkEntry<DatapathId, DatapathId> link1 = new LinkEntry<>(switch1, switch2);
        testMap.put(link1,100);

        LinkEntry<DatapathId, DatapathId> linkTry = new LinkEntry<>(switch1_copy, switch2_copy);
        System.out.println(testMap.get(linkTry));
    }
}
