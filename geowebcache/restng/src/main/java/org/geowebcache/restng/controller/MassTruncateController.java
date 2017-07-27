/*
 *
 *  *  (c) 2017 - 2016 Open Source Geospatial Foundation - all rights reserved
 *  *  * This code is licensed under the GPL 2.0 license, available at the root
 *  *  * application directory.
 *  *
 *
 */

package org.geowebcache.restng.controller;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.GeoWebCacheException;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.restng.exception.RestException;
import org.geowebcache.seed.*;
import org.geowebcache.storage.StorageBroker;
import org.geowebcache.storage.StorageException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;

@Component
@RestController
public class MassTruncateController extends GWCSeedingController{
    private static Log log = LogFactory.getLog(MassTruncateController.class);
    
    @Autowired
    private StorageBroker storageBroker;

    @Autowired
    private StorageBroker broker;

    @Autowired
    private TileBreeder breeder;

    public void setStorageBroker(StorageBroker broker) {
        this.broker = broker;
    }

    public void setTileBreeder(TileBreeder breeder) {
        this.breeder = breeder;
    }

    static final Class<?>[] DEFAULT_REQUEST_TYPES = {
            TruncateLayerRequest.class,
            TruncateParametersRequest.class,
            TruncateOrphansRequest.class,
            TruncateBboxRequest.class
    };

    Class<?>[] requestTypes;

    /**
     * Responds with a simple XML document indicating the available MassRequest types.
     */
    @RequestMapping(value = "/masstruncate", method = RequestMethod.GET)
    public ResponseEntity<?> doGet(HttpServletRequest req) {
        // Just use this for figuring out what the correct element names are
        XStream xs = configXStream(new GeoWebCacheXStream());

        // Not worth the trouble of messing with XStream for the output so just assemble some XML.

        StringBuilder sb = new StringBuilder();
        Set<String> result = new HashSet<String>();
        sb.append("<massTruncateRequests href=\"")
                .append(req.getRequestURL()).append("\">");

        for(Class<?> requestType: getRequestTypes()) {
            String alias = xs.getMapper().serializedClass(requestType);
            sb.append(" <requestType>");
            sb.append(alias);
            sb.append("</requestType>");
            if(!result.add(alias) && log.isWarnEnabled()) {
                log.warn("Duplicate MassTruncate RestException type: "+alias);
            }
        }

        sb.append("</massTruncateRequests>");
        return new ResponseEntity<Object>(sb, HttpStatus.OK);
    }
    
    /**
     * Issue a mass truncate request.
     */
    @RequestMapping(value = "/masstruncate", method = RequestMethod.POST)
    public ResponseEntity<?> doPost(HttpServletRequest req) throws IOException {
        String contentType = req.getContentType();

        XStream xs = configXStream(new GeoWebCacheXStream(new DomDriver()));
        StringWriter writer = new StringWriter();
        IOUtils.copy(req.getInputStream(), writer, null);
        String reqData = writer.toString();

        Object obj = null;

        if (contentType==null || contentType.equalsIgnoreCase("text/xml")) {

            obj = xs.fromXML(reqData);
        } else if (contentType.equalsIgnoreCase("json")) {
            obj = xs.fromXML(convertJson(reqData));
        } else {
            throw new RestException("Format extension unknown or not specified: "
                    + contentType, HttpStatus.BAD_REQUEST);
        }

        MassTruncateRequest mtr = (MassTruncateRequest) obj;
        try {
            if(!mtr.doTruncate(broker, breeder)) {
                throw new RestException("Truncation failed", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (IllegalArgumentException e) {
            throw new RestException(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (StorageException e) {
            throw new RestException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (GeoWebCacheException e) {
            throw new RestException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return new ResponseEntity<String>(HttpStatus.OK);
    }

    protected void handleRequest(HttpServletRequest req, HttpServletResponse resp, Object obj) {
        MassTruncateRequest mtr = (MassTruncateRequest) obj;
        try {
            if(!mtr.doTruncate(broker, breeder)) {
                throw new RestException("Truncation failed", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } catch (IllegalArgumentException e) {
            throw new RestException(e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (StorageException e) {
            throw new RestException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (GeoWebCacheException e) {
            throw new RestException(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    protected Class<?>[] getRequestTypes() {
        if(requestTypes==null) requestTypes=DEFAULT_REQUEST_TYPES;
        return requestTypes;
    }

    @Override
    protected XStream configXStream(XStream xs) {
        xs = super.configXStream(xs);
        xs.processAnnotations(getRequestTypes());
        return xs;
    }
}
