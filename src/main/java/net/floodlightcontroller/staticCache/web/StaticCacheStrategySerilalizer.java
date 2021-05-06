package net.floodlightcontroller.staticCache.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class StaticCacheStrategySerilalizer extends JsonSerializer<StaticCacheStrategy> {

    @Override
    public void serialize(StaticCacheStrategy strategy, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException, JsonProcessingException {
        jsonGenerator.writeStartObject();

        jsonGenerator.writeNumberField("strategyid", strategy.strategyid);
        jsonGenerator.writeStringField("src_ip", strategy.nw_src_ipv4.toString());
        jsonGenerator.writeStringField("dst_ip", strategy.nw_dst_ipv4.toString());
        jsonGenerator.writeNumberField("tp_dst", strategy.tp_dst.getPort());
        jsonGenerator.writeNumberField("priority", strategy.priority);
        jsonGenerator.writeStringField("cache_ip", strategy.nw_cache_ipv4.toString());
        jsonGenerator.writeStringField("cache_mac", strategy.nw_cache_dl_dst.toString());

        jsonGenerator.writeEndObject();
    }
}
