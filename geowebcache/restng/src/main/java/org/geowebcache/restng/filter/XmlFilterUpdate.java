package org.geowebcache.restng.filter;

import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.restng.exception.RestException;

import java.io.IOException;

/**
 * Created by vickdw on 7/14/17.
 */
public abstract class XmlFilterUpdate {
    abstract protected void runUpdate(RequestFilter filter, TileLayer tl) throws IOException, RestException;
}
