package net.floodlightcontroller.staticCache;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.staticCache.web.StaticCacheStrategy;

import java.util.List;

/**
 * @author Michael Kang
 * @create 2021-04-18  02:40 PM
 */
public interface IStaticCacheService extends IFloodlightService {

    public void addStrategy (StaticCacheStrategy strategy);

    public List<StaticCacheStrategy> getStrategies();

    public void deleteRule(int id);
}
