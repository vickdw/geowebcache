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
import com.thoughtworks.xstream.io.HierarchicalStreamDriver;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.copy.HierarchicalStreamCopier;
import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.geowebcache.config.ContextualConfigurationProvider;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.restng.exception.RestException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

abstract public class GWCSeedingController extends GWCController {
    private static Log log = LogFactory.getLog(GWCSeedingController.class);

    public JSONObject myrequest;

    @Autowired
    protected XMLConfiguration xmlConfig;
    
    /**
     * Handle a GET request
     */
    abstract public ResponseEntity<?> doGet(HttpServletRequest request) throws RestException;

    /**
     * Handle a POST request
     */
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws RestException, IOException {
        String formatExtension = req.getParameter("extension");

        XStream xs = configXStream(new GeoWebCacheXStream(new DomDriver()));

        Object obj = null;

        if (formatExtension==null || formatExtension.equalsIgnoreCase("xml")) {
            obj = xs.fromXML(req.getInputStream());
        } else if (formatExtension.equalsIgnoreCase("json")) {
            obj = xs.fromXML(convertJson(req.getInputStream().toString()));
        } else {
            throw new RestException("Format extension unknown or not specified: "
                    + formatExtension, HttpStatus.BAD_REQUEST);
        }

        handleRequest(req, resp, obj);

    }

    abstract protected void handleRequest(HttpServletRequest req, HttpServletResponse resp, Object obj);

    /**
     * Deserializing a json string is more complicated.
     *
     * XStream does not natively support it. Rather, it uses a JettisonMappedXmlDriver to convert to
     * intermediate xml and then deserializes that into the desired object. At this time, there is a
     * known issue with the Jettison driver involving elements that come after an array in the json
     * string.
     *
     * http://jira.codehaus.org/browse/JETTISON-48
     *
     * The code below is a hack: it treats the json string as text, then converts it to the
     * intermediate xml and then deserializes that into the SeedRequest object.
     */
    protected String convertJson(String entityText) throws IOException {
        HierarchicalStreamDriver driver = new JettisonMappedXmlDriver();
        StringReader reader = new StringReader(entityText);
        HierarchicalStreamReader hsr = driver.createReader(reader);
        StringWriter writer = new StringWriter();
        new HierarchicalStreamCopier().copy(hsr, new PrettyPrintWriter(writer));
        writer.close();
        return writer.toString();
    }

    public void setXmlConfig(XMLConfiguration xmlConfig) {
        this.xmlConfig = xmlConfig;
    }

    protected XStream configXStream(XStream xs) {
        return xmlConfig.getConfiguredXStreamWithContext(xs, ContextualConfigurationProvider.Context.REST);
    }
}
