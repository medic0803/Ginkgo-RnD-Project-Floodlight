package net.floodlightcontroller.qos.ResourceMonitor.pojo;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

/**
 * @author Michael Kang
 * @create 2021-02-18 下午 04:40
 */
public class SwitchPortCounter {

    private DatapathId id;
    private OFPort pt;

    private U64 rxValue;
    private U64 rx;
    private U64 txValue;
    private U64 tx;

    public SwitchPortCounter() {
    }

    public SwitchPortCounter(DatapathId id, OFPort pt, U64 rxValue, U64 rx_counted, U64 txValue, U64 tx_counted) {
        this.id = id;
        this.pt = pt;
        this.rxValue = rxValue;
        this.rx = rx_counted;
        this.txValue = txValue;
        this.tx = tx_counted;
    }

    public static SwitchPortCounter of(DatapathId id, OFPort pt, U64 rxValue, U64 rx_Counted, U64 txValue, U64 tx_Counted) {
        if (id == null) {
            throw new IllegalArgumentException("Datapath ID cannot be null");
        }
        if (pt == null) {
            throw new IllegalArgumentException("Port cannot be null");
        }
        if (rxValue == null) {
            throw new IllegalArgumentException("rxValue cannot be null");
        }
        if (rx_Counted == null) {
            throw new IllegalArgumentException("rx_counted cannot be null");
        }
        if (txValue == null) {
            throw new IllegalArgumentException("txValue cannot be null");
        }
        if (tx_Counted == null) {
            throw new IllegalArgumentException("tx_counted cannot be null");
        }
        return new SwitchPortCounter(id, pt, rxValue, rx_Counted, txValue, tx_Counted);
    }

    public DatapathId getSwitchId() {
        return id;
    }

    public OFPort getSwitchPort() {
        return pt;
    }

    public U64 getPriorByteValueRx() {
        return rxValue;
    }

    public U64 getPriorByteValueTx() {
        return txValue;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((pt == null) ? 0 : pt.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SwitchPortCounter other = (SwitchPortCounter) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (pt == null) {
            if (other.pt != null)
                return false;
        } else if (!pt.equals(other.pt))
            return false;
        return true;
    }
}
