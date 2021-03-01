package net.floodlightcontroller.qos.ResourceMonitor.pojo;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.U64;

/**
 * @author Michael Kang
 * @create 2021-02-18 下午 04:40
 */
public class SwitchPortPkLoss {

    private DatapathId id;
    private OFPort pt;

    private Integer pkLossRatio; //the core for the PkLossRatio
    private U64 rxValue;
    private U64 rx_DropValue;
    private U64 txValue;
    private U64 tx_DropValue;

    private SwitchPortPkLoss() {
    }

    public SwitchPortPkLoss(DatapathId id, OFPort pt, int pkLossRatio, U64 rxValue, U64 rx_DropValue, U64 txValue, U64 tx_DropValue) {
        this.id = id;
        this.pt = pt;
        this.pkLossRatio = pkLossRatio;
        this.rxValue = rxValue;
        this.rx_DropValue = rx_DropValue;
        this.txValue = txValue;
        this.tx_DropValue = tx_DropValue;
    }

    //kwmtodo: 更改这个if变量
    public static SwitchPortPkLoss of(DatapathId id, OFPort pt, Integer pkLossRatio,
                                      U64 rxValue, U64 rx_DropValue, U64 txValue, U64 tx_DropValue) {
        if (id == null) {
            throw new IllegalArgumentException("Datapath ID cannot be null");
        }
        if (pt == null) {
            throw new IllegalArgumentException("Port cannot be null");
        }
        if (pkLossRatio == null) {
            throw new IllegalArgumentException("pkLossRatio cannot be null");
        }
        if (rxValue == null) {
            throw new IllegalArgumentException("rxValue cannot be null");
        }
        if (rx_DropValue == null) {
            throw new IllegalArgumentException("rx_DropValue cannot be null");
        }
        if (txValue == null) {
            throw new IllegalArgumentException("txValue cannot be null");
        }
        if (tx_DropValue == null) {
            throw new IllegalArgumentException("tx_DropValue cannot be null");
        }
        return new SwitchPortPkLoss(id, pt, pkLossRatio, rxValue, rx_DropValue, txValue, tx_DropValue);
    }

    public U64 getPriorRx_DropValue() {
        return rx_DropValue;
    }

    public U64 getPriorTx_DropValue() {
        return tx_DropValue;
    }

    public DatapathId getSwitchId() {
        return id;
    }

    public OFPort getSwitchPort() {
        return pt;
    }

    public int getPkLossPerSec() {
        return this.pkLossRatio;
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
        SwitchPortPkLoss other = (SwitchPortPkLoss) obj;
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
