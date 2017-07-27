/*
 *
 *  *  (c) 2017 - 2016 Open Source Geospatial Foundation - all rights reserved
 *  *  * This code is licensed under the GPL 2.0 license, available at the root
 *  *  * application directory.
 *  *
 *
 */

package org.geowebcache.restng.controller;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.layer.TileLayerDispatcher;
import org.geowebcache.restng.exception.RestException;
import org.geowebcache.util.ServletUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@RestController
public class ReloadController {
    private static Log log = LogFactory.getLog(ReloadController.class);

    @Autowired
    TileLayerDispatcher layerDispatcher;

    @RequestMapping(value = "/reload", method = RequestMethod.POST)
    public @ResponseBody ResponseEntity<?> doPost(HttpServletRequest req, HttpServletResponse resp)
            throws GeoWebCacheException, RestException, IOException {

        if (req.getParameterMap() == null || req.getParameter("reload_configuration") == null) {
            throw new RestException(
                    "Unknown or malformed request. Please try again, somtimes the form "
                            +"is not properly received. This frequently happens on the first POST "
                            +"after a restart. The POST was to " + req.getRequestURI(),
                    HttpStatus.BAD_REQUEST);
        }

        StringBuilder doc = new StringBuilder();

        doc.append("<html>\n"+ ServletUtils.gwcHtmlHeader("../","GWC Reload") +"<body>\n" + ServletUtils.gwcHtmlLogoLink("../"));

        try {
            layerDispatcher.reInit();
            String info = "Configuration reloaded. Read "
                    + layerDispatcher.getLayerCount()
                    + " layers from configuration resources.";

            log.info(info);
            doc.append("<p>"+info+"</p>");

            doc.append("<p>Note that this functionality has not been rigorously tested,"
                    + " please reload the servlet if you run into any problems."
                    + " Also note that you must truncate the tiles of any layers that have changed.</p>");

        } catch (Exception e) {
            doc.append("<p>There was a problem reloading the configuration:<br>\n"
                    + e.getMessage()
                    + "\n<br>"
                    + " If you believe this is a bug, please submit a ticket at "
                    + "<a href=\"http://geowebcache.org\">GeoWebCache.org</a>"
                    + "</p>");
        }

        doc.append("<p><a href=\"../demo\">Go back</a></p>\n");
        doc.append("</body></html>");


        return new ResponseEntity<Object>(doc.toString(), HttpStatus.OK);
    }

    public void setTileLayerDispatcher(TileLayerDispatcher tileLayerDispatcher) {
        layerDispatcher = tileLayerDispatcher;
    }
}
