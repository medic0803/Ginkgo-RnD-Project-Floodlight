package net.floodlightcontroller.staticCache.web;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import net.floodlightcontroller.staticCache.IStaticCacheService;
import org.projectfloodlight.openflow.types.*;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class StaticCacheStrategiesResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(StaticCacheStrategiesResource.class);

    @Get("json")
    public List<StaticCacheStrategy> retrieve() {
//    public String retrieve() {
        IStaticCacheService staticCache =
                (IStaticCacheService)getContext().getAttributes().
                        get(IStaticCacheService.class.getCanonicalName());

        return staticCache.getStrategies();
    }

    @Post
    public String store(String scJson) {
        //kwmtodo: module service
        IStaticCacheService staticCache =
                (IStaticCacheService)getContext().getAttributes().
                        get(IStaticCacheService.class.getCanonicalName());

        //kwmtodo: json translation function
        StaticCacheStrategy strategy = jsonToStaticCacheStrategy(scJson);

        String status = null;
        //kwmtodo: status determiner
        staticCache.addStrategy(strategy);
        status = "Strategy added";
        //kwmtodo: return statement
        return ("{\"status\" : \"" + status + "\", \"strategy-id\" : \""+ Integer.toString(strategy.strategyid) + "\"}");
    }


    public static StaticCacheStrategy jsonToStaticCacheStrategy(String fmJson) {
        StaticCacheStrategy strategy = new StaticCacheStrategy();
        MappingJsonFactory mappingJsonFactory = new MappingJsonFactory();
        JsonParser jsonParser;
        try {
            try {
                jsonParser = mappingJsonFactory.createParser(fmJson);
            } catch (JsonParseException e) {
                throw new IOException(e);
            }

            jsonParser.nextToken();
            if (jsonParser.getCurrentToken() != JsonToken.START_OBJECT) {
                throw new IOException("Expected START_OBJECT");
            }

            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                if (jsonParser.getCurrentToken() != JsonToken.FIELD_NAME) {
                    throw new IOException("Expected FIELD_NAME");
                }

                String currentName = jsonParser.getCurrentName();
                jsonParser.nextToken();
                if (jsonParser.getText().equals("")) {
                    continue;
                }

                // This is currently only applicable for remove().  In store(), ruleid takes a random number
                if (currentName.equalsIgnoreCase("ruleid")) {
                    try {
                        strategy.strategyid = Integer.parseInt(jsonParser.getText());
                    } catch (IllegalArgumentException e) {
                        log.error("Unable to parse rule ID: {}", jsonParser.getText());
                    }
                }

                else if (currentName.equalsIgnoreCase("src-ip")) {
                    if (!jsonParser.getText().equalsIgnoreCase("ANY")) {
//                        strategy.any_nw_src = false;
//                        if (strategy.dl_type.equals(EthType.NONE)){
//                            strategy.any_dl_type = false;
//                            strategy.dl_type = EthType.IPv4;
//                        }
                        try {
                            strategy.nw_src_prefix_and_mask = IPv4AddressWithMask.of(jsonParser.getText());
                        } catch (IllegalArgumentException e) {
                            log.error("Unable to parse source IP: {}", jsonParser.getText());
                            //TODO should return some error message via HTTP message
                        }
                    }
                }

                else if (currentName.equalsIgnoreCase("dst-ip")) {
                    if (!jsonParser.getText().equalsIgnoreCase("ANY")) {
//                        strategy.any_nw_dst = false;
//                        if (strategy.dl_type.equals(EthType.NONE)){
//                            strategy.any_dl_type = false;
//                            strategy.dl_type = EthType.IPv4;
//                        }
                        try {
                            strategy.nw_dst_prefix_and_mask = IPv4AddressWithMask.of(jsonParser.getText());
                        } catch (IllegalArgumentException e) {
                            log.error("Unable to parse destination IP: {}", jsonParser.getText());
                            //TODO should return some error message via HTTP message
                        }
                    }
                }
                else if (currentName.equalsIgnoreCase("cache-ip")) {
                    if (!jsonParser.getText().equalsIgnoreCase("ANY")) {
//                        strategy.any_nw_dst = false;
//                        if (strategy.dl_type.equals(EthType.NONE)){
//                            strategy.any_dl_type = false;
//                            strategy.dl_type = EthType.IPv4;
//                        }
                        try {
                            strategy.nw_cache_prefix_and_mask = IPv4AddressWithMask.of(jsonParser.getText());
                        } catch (IllegalArgumentException e) {
                            log.error("Unable to parse destination IP: {}", jsonParser.getText());
                            //TODO should return some error message via HTTP message
                        }
                    }
                }
                else if (currentName.equalsIgnoreCase("tp-dst")) {
//                    strategy.any_tp_dst = false;
                    try {
                        strategy.tp_dst = TransportPort.of(Integer.parseInt(jsonParser.getText()));
                        System.out.println("#####################" + strategy.tp_dst);
                    } catch (IllegalArgumentException e) {
                        log.error("Unable to parse destination transport port: {}", jsonParser.getText());
                        //TODO should return some error message via HTTP message
                    }
                }

                else if (currentName.equalsIgnoreCase("priority")) {
                    try {
                        strategy.priority = Integer.parseInt(jsonParser.getText());
                    } catch (IllegalArgumentException e) {
                        log.error("Unable to parse priority: {}", jsonParser.getText());
                        //TODO should return some error message via HTTP message
                    }
                }
            }
        } catch (IOException e) {
            log.error("Unable to parse JSON string: {}", e);
        }

        return strategy;
    }
}
