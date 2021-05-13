package net.floodlightcontroller.multicasting.web;

import net.floodlightcontroller.restserver.RestletRoutable;
import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

/**
 * @author Michael Kang
 * @create 2021-05-12 下午 02:08
 */
public class GinkgoRouteable implements RestletRoutable {

    @Override
    public Restlet getRestlet(Context context) {
        Router router = new Router(context);
//        router.attach("/bandwidth/{" + DPID_STR + "}/{" + PORT_STR + "}/json", BandwidthResource.class);
        router.attach("/showLinks/json", LinkResource.class);
        return router;
    }

    @Override
    public String basePath() {
        return "/ginkgo";
    }
}
