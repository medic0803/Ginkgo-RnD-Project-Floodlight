package net.floodlightcontroller.staticCache.web;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import net.floodlightcontroller.firewall.FirewallRule;
import net.floodlightcontroller.firewall.IFirewallService;
import net.floodlightcontroller.staticCache.IStaticCacheService;
import org.projectfloodlight.openflow.types.*;
import org.restlet.resource.Delete;
import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class StaticCacheStrategiesResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(StaticCacheStrategiesResource.class);

    @Get("json")
    public List<StaticCacheStrategy> retrieve() {
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
        if (checkStrategyExist(strategy,staticCache.getStrategies())){
            status = "A similar Strategy has already exist";
            return ("{\"status\" : \"" + status + "\", \"strategy-id\" : \""+ strategy.strategyid + "\"}");
        }else {
            staticCache.addStrategy(strategy);
            status = "Strategy added";
            //kwmtodo: return statement
            return ("{\"status\" : \"" + status + "\", \"strategy-id\" : \""+ strategy.strategyid + "\"}");
        }
    }
    private static boolean checkStrategyExist(StaticCacheStrategy strategy, List<StaticCacheStrategy> strategies){
        for (StaticCacheStrategy scs:
             strategies) {
            if (scs.isSameAs(strategy)){
                return true;
            }
        }
        return false;
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
                if (currentName.equalsIgnoreCase("strategyid")) {
                    try {
                        strategy.strategyid = Integer.parseInt(jsonParser.getText());
                    } catch (IllegalArgumentException e) {
                        log.error("Unable to parse rule ID: {}", jsonParser.getText());
                    }
                }

                else if (currentName.equalsIgnoreCase("src-ip")) {
                    if (!jsonParser.getText().equalsIgnoreCase("ANY")) {
                        try {
                            strategy.nw_src_ipv4 = IPv4Address.of(jsonParser.getText());
                        } catch (IllegalArgumentException e) {
                            log.error("Unable to parse source IP: {}", jsonParser.getText());
                        }
                    }
                }
                else if (currentName.equalsIgnoreCase("dst-ip")) {
                    if (!jsonParser.getText().equalsIgnoreCase("ANY")) {
                        try {
                            strategy.nw_dst_ipv4 = IPv4Address.of(jsonParser.getText());
                        } catch (IllegalArgumentException e) {
                            log.error("Unable to parse destination IP: {}", jsonParser.getText());
                            //TODO should return some error message via HTTP message
                        }
                    }
                }
                else if (currentName.equalsIgnoreCase("cache-ip")) {
                    if (!jsonParser.getText().equalsIgnoreCase("ANY")) {

                        try {
                            strategy.nw_cache_ipv4 = IPv4Address.of(jsonParser.getText());
                        } catch (IllegalArgumentException e) {
                            log.error("Unable to parse destination IP: {}", jsonParser.getText());
                        }
                    }
                }
                else if (currentName.equalsIgnoreCase("tp-dst")) {
//                    strategy.any_tp_dst = false;
                    try {
                        strategy.tp_dst = TransportPort.of(Integer.parseInt(jsonParser.getText()));
                    } catch (IllegalArgumentException e) {
                        log.error("Unable to parse destination transport port: {}", jsonParser.getText());
                    }
                }

                else if (currentName.equalsIgnoreCase("priority")) {
                    try {
                        strategy.priority = Integer.parseInt(jsonParser.getText());
                    } catch (IllegalArgumentException e) {
                        log.error("Unable to parse priority: {}", jsonParser.getText());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Unable to parse JSON string: {}", e);
        }

        return strategy;
    }

    @Delete
    public String remove(String fmJson) {
        IStaticCacheService cacheService =
                (IStaticCacheService)getContext().getAttributes().
                        get(IStaticCacheService.class.getCanonicalName());

        StaticCacheStrategy deleteStrategy = jsonToStaticCacheStrategy(fmJson);
        if (deleteStrategy == null) {
            //TODO compose the error with a json formatter
            return "{\"status\" : \"Error! Could not parse firewall rule, see log for details.\"}";
        }

        String status = null;
        boolean exists = false;
        Iterator<StaticCacheStrategy> iter = cacheService.getStrategies().iterator();
        while (iter.hasNext()) {
            StaticCacheStrategy staticCacheStrategy = iter.next();
            if (staticCacheStrategy.strategyid == deleteStrategy.strategyid) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            status = "Error! Can't delete, a rule with this ID doesn't exist.";
            log.error(status);
        } else {
            // delete rule from firewall
            cacheService.deleteRule(deleteStrategy.strategyid);
            status = "Strategy deleted";
        }
        return ("{\"status\" : \"" + status + "\"}");
    }

}
