/*
 *  (c) 2017 - 2016 Open Source Geospatial Foundation - all rights reserved
 *  * This code is licensed under the GPL 2.0 license, available at the root
 *  * application directory.
 *
 */
package org.geowebcache.restng.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@Component
@RestController
public class IndexController {

    @RequestMapping(value = "/about", method = RequestMethod.GET, produces = "text/html")
    public ResponseEntity<?> handleRequestInternal() {
        String idx = "<html><body>\n"
                + "<a id=\"logo\" href=\""
                + "\">"
                + "<img src=\""
                + "/web/geowebcache_logo.png\" alt=\"\" height=\"100\" width=\"353\" border=\"0\"/></a>\n"
                + "<h3>Resources available from here:</h3>"
                + "<ul>"
                + "<li><h4><a href=\""
                + "/layers/\">layers</a></h4>"
                + "Lets you see the configured layers. You can also view a specific layer "
                + " by appending the name of the layer to the URL, DELETE an existing layer "
                + " or POST a new one. Note that the latter operations only make sense when GeoWebCache"
                + " has been configured through geowebcache.xml. You can POST either XML or JSON."
                + "</li>\n" + "<li><h4>seed</h4>" + "" + "</li>\n" + "</ul>"
                + "</body></html>";
        return new ResponseEntity<String>(idx, HttpStatus.OK);
    }
}
