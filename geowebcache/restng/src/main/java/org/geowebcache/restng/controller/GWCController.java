/*
 *
 *  *  (c) 2017 - 2016 Open Source Geospatial Foundation - all rights reserved
 *  *  * This code is licensed under the GPL 2.0 license, available at the root
 *  *  * application directory.
 *  *
 *  
 */

package org.geowebcache.restng.controller;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.restng.exception.RestException;
import org.springframework.http.HttpStatus;

public class GWCController {
    protected static TileLayer findTileLayer(String layerName, TileLayerDispatcher layerDispatcher) {
        if (layerName == null || layerName.length() == 0) {
            throw new RestException("Layer not specified", HttpStatus.BAD_REQUEST );
        }

        if (!layerDispatcher.layerExists(layerName)) {
            throw new RestException("Unknown layer: " + layerName, HttpStatus.NOT_FOUND);
        }

        TileLayer layer;
        try {
            layer = layerDispatcher.getTileLayer(layerName);
        } catch (GeoWebCacheException gwce) {
            throw new RestException("Encountered error: " + gwce.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return layer;
    }
}
