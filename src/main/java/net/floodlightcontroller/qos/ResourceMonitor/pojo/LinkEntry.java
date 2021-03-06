package net.floodlightcontroller.qos.ResourceMonitor.pojo;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * @author Michael Kang
 * @create 2021-02-14 下午 02:29
 */
public class LinkEntry<K,V> implements Entry<K,V>{
    K source;
    V destination;
    public LinkEntry(K key, V value){
        this.source = key;
        this.destination = value;
    }
    @Override
    public K getKey() {
        return this.source;
    }

    @Override
    public V getValue() {
        return this.destination;
    }

    @Override
    public V setValue(V value) {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LinkEntry)) return false;
        LinkEntry<?, ?> linkEntry = (LinkEntry<?, ?>) o;
        return source.equals(linkEntry.source) &&
                destination.equals(linkEntry.destination);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, destination);
    }

    @Override
    public String toString() {
        return "LinkEntry{" +
                "source=" + source +
                ", destination=" + destination +
                '}';
    }
}
