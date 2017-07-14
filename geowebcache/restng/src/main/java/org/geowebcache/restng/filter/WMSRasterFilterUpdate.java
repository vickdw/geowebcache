package org.geowebcache.restng.filter;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.filter.request.RequestFilter;
import org.geowebcache.filter.request.WMSRasterFilter;
import org.geowebcache.layer.TileLayer;
import org.geowebcache.restng.exception.RestException;
import org.springframework.http.HttpStatus;

import java.io.IOException;

/**
 * Created by vickdw on 7/14/17.
 */
public class WMSRasterFilterUpdate extends XmlFilterUpdate {
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
