/*
 *
 *  *  (c) 2017 - 2016 Open Source Geospatial Foundation - all rights reserved
 *  *  * This code is licensed under the GPL 2.0 license, available at the root
 *  *  * application directory.
 *  *
 *
 */

package org.geowebcache.restng.filter;

import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.restng.exception.RestException;

import java.io.IOException;

public abstract class XmlFilterUpdate {
    abstract protected void runUpdate(RequestFilter filter, TileLayer tl) throws IOException, RestException;
}
