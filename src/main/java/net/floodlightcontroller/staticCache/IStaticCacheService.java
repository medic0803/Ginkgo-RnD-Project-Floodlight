package net.floodlightcontroller.staticCache;

import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.staticCache.web.StaticCacheStrategy;

import java.util.List;

/**
 * @author Michael Kang
 * @create 2021-04-18 下午 02:40
 */
public interface IStaticCacheService extends IFloodlightService {

    public void addStrategy (StaticCacheStrategy strategy);

    public List<StaticCacheStrategy> getStrategies();
}