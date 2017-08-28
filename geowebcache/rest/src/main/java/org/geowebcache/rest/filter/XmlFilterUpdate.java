package org.geowebcache.rest.filter;

import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.rest.exception.RestException;

import java.io.IOException;

public abstract class XmlFilterUpdate {
    abstract public void runUpdate(RequestFilter filter, TileLayer tl) throws IOException, RestException;
}
