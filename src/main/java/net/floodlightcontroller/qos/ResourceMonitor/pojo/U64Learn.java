package net.floodlightcontroller.qos.ResourceMonitor.pojo;

import org.projectfloodlight.openflow.types.U64;

/**
 * @author Michael Kang
 * kwmtodo: 这里的补码转换怎么回事,Floodlight 过载overflow U64处理
 * @create 2021-02-07 下午 06:40
 */
public class U64Learn {
    public static void main(String[] args) {
//        if (spb.getPriorByteValueRx().compareTo(pse.getRxBytes()) > 0) { /* overflow */
//            U64 upper = U64.NO_MASK.subtract(spb.getPriorByteValueRx());
//            U64 lower = pse.getRxBytes();
//            rxBytesCounted = upper.add(lower);
        U64 prior = U64.ofRaw(9223372036854775807L-5);
        U64 now = U64.ofRaw(5+9223372036854775807L);
        U64 upper = U64.NO_MASK.subtract(prior);
        U64 lower = now;
        U64 connted = upper.add(lower);
        System.out.println(connted.getValue());
        System.out.println(now.getValue());
    }
}
