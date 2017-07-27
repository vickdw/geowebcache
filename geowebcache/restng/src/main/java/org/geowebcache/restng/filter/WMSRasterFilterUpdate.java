/*
 *
 *  *  (c) 2017 - 2016 Open Source Geospatial Foundation - all rights reserved
 *  *  * This code is licensed under the GPL 2.0 license, available at the root
 *  *  * application directory.
 *  *
 *
 */

package org.geowebcache.restng.filter;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.filter.request.WMSRasterFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.restng.exception.RestException;
import org.springframework.http.HttpStatus;

import java.io.IOException;

public class WMSRasterFilterUpdate {
    String gridSetId;
    int zoomStart;
    int zoomStop;


    protected void runUpdate(RequestFilter filter, TileLayer tl) throws IOException, RestException {
        if(! (filter instanceof WMSRasterFilter)) {
            throw new RestException("The filter " + filter.getName() + " is not a WMSRasterFilter.",
                    HttpStatus.BAD_REQUEST);
        }

        WMSRasterFilter wmsFilter = (WMSRasterFilter) filter;

        // Check that the SRS makes sense
        if (tl.getGridSubset(gridSetId) == null) {
            throw new RestException("The filter " + wmsFilter.getName()
                    + " is associated with a layer that does not support "
                    + gridSetId, HttpStatus.BAD_REQUEST);
        }

        // Run the actual update
        try {
            wmsFilter.update(tl, gridSetId, zoomStart, zoomStop);
        } catch (GeoWebCacheException e) {
            throw new RestException("Error updating " + wmsFilter.getName()
                    + ": " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
