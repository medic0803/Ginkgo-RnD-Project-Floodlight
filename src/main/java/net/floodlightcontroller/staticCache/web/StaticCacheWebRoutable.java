package net.floodlightcontroller.staticCache.web;

import net.floodlightcontroller.restserver.RestletRoutable;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

public class StaticCacheWebRoutable implements RestletRoutable {
    @Override
    public Router getRestlet(Context context) {
        Router router = new Router(context);
        router.attach("/strategies/json", StaticCacheStrategiesResource.class);
        return router;
    }

    @Override
    public String basePath() {
        return "/wm/staticcachepusher";
    }
}
