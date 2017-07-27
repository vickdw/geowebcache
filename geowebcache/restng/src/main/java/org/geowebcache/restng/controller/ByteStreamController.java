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
import org.geowebcache.GeoWebCacheExtensions;
import org.geowebcache.mime.MimeException;
import org.geowebcache.mime.MimeType;
import org.geowebcache.restng.config.WebResourceBundle;
import org.geowebcache.restng.exception.RestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URL;
import java.util.List;
import java.util.regex.Pattern;

@Component
@RestController
public class ByteStreamController {
    private static Log log = LogFactory.getLog(ByteStreamController.class);

    WebResourceBundle bundle;

    private static final WebResourceBundle DEFAULT_BUNDLE = ByteStreamController.class::getResource;

    protected URL getResource(String path) {
        if(bundle==null) {
            synchronized(this) {
                if(bundle==null) {
                    List<WebResourceBundle> result= GeoWebCacheExtensions.extensions(WebResourceBundle.class);
                    if(result.isEmpty()) {
                        bundle = DEFAULT_BUNDLE;
                    } else {
                        bundle = result.get(0);
                        if(result.size()>1) {
                            log.warn("Multiple web resource bundles present, using "+bundle.getClass().getName());
                        }
                    }
                }
            }
        }
        URL resource = bundle.apply(path);
        if(resource==null && bundle != DEFAULT_BUNDLE) {
            resource = DEFAULT_BUNDLE.apply(path);
        }
        return resource;
    }

    static final Pattern UNSAFE_RESOURCE = Pattern.compile("^/|/\\.\\./|^\\.\\./|\\.class$");

    @RequestMapping(value = "/web/{filename}", method = RequestMethod.GET)
    public @ResponseBody
    ResponseEntity<?> doGet(HttpServletRequest req, HttpServletResponse resp) {
        String filename = (String) req.getParameter("filename"); //request.getAttributes().get("filename");

        // Just to make sure we don't allow access to arbitrary resources
        if(UNSAFE_RESOURCE.matcher(filename).find()) {
            throw new RestException("Illegal web resource", HttpStatus.FORBIDDEN);
        }

        URL resource = getResource(filename);
        if(resource == null) {
            return new ResponseEntity<Object>(HttpStatus.NOT_FOUND);
        }

//        response.setStatus(Status.SUCCESS_OK);

        String[] filenameParts = filename.split("\\.");
        String extension = filenameParts[filenameParts.length - 1];

        MimeType mime = null;
        try {
            mime = MimeType.createFromExtension(extension);
        } catch (MimeException e) {
            return new ResponseEntity<Object>("Unable to create MimeType for " + extension, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // TODO write ByteArrayOutputStream ResponseEntity

//        ByteRepresentation imgRep = new ByteRepresentation(new MediaType(mime.getMimeType()), resource);

//        response.setEntity(imgRep);
        
        return null;
    }
}
