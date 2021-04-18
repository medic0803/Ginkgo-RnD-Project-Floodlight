package net.floodlightcontroller.staticCache.web;

import org.restlet.resource.Get;
import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class StaticCacheStrategiesResource extends ServerResource {
    protected static Logger log = LoggerFactory.getLogger(StaticCacheStrategiesResource.class);

//    @Get("json")
//    public List<StaticCacheStrategies> retrieve() {
//
//    }

    @Post
    public String store(String scJson) {
        //kwmtodo: module service

        //kwmtodo: json translation function
        StaticCacheStrategy strategy = jsonToStaticCacheStrategy(scJson);

        String status = null;
        //kwmtodo: status determiner
        staticCache.addStrategy(strategy);
        status = "Strategy added";
        //kwmtodo: return statement

    }
}
