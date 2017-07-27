/*
 *
 *  *  (c) 2017 - 2016 Open Source Geospatial Foundation - all rights reserved
 *  *  * This code is licensed under the GPL 2.0 license, available at the root
 *  *  * application directory.
 *  *
 *
 */

package org.geowebcache.restng.config;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import org.geowebcache.config.GeoWebCacheConfiguration;
import org.geowebcache.io.GeoWebCacheXStream;
import org.geowebcache.restng.filter.WMSRasterFilterUpdate;
import org.geowebcache.restng.filter.XmlFilterUpdate;

import java.io.InputStream;

public class XMLConfiguration {
    private static XStream getConfiguredXStream(XStream xs) {
        // Restrict classes that can be serialized/deserialized
        // Allowing arbitrary classes to be deserialized is a security issue.
        {


            // Allow any implementation of these extension points
            xs.allowTypeHierarchy(org.geowebcache.layer.TileLayer.class);
            xs.allowTypeHierarchy(org.geowebcache.filter.parameters.ParameterFilter.class);
            xs.allowTypeHierarchy(org.geowebcache.filter.request.RequestFilter.class);
            xs.allowTypeHierarchy(org.geowebcache.config.BlobStoreConfig.class);
            xs.allowTypeHierarchy(org.geowebcache.config.Configuration.class);

            // Allow anything that's part of GWC
            // TODO: replace this with a more narrow whitelist
            xs.allowTypesByWildcard(new String[]{"org.geowebcache.**"});
        }

        xs.setMode(XStream.NO_REFERENCES);

        xs.alias("gwcConfiguration", GeoWebCacheConfiguration.class);
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xmlns_xsi");
        xs.aliasField("xmlns:xsi", GeoWebCacheConfiguration.class, "xmlns_xsi");
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xsi_noNamespaceSchemaLocation");
        xs.aliasField("xsi:noNamespaceSchemaLocation", GeoWebCacheConfiguration.class,
                "xsi_noNamespaceSchemaLocation");
        xs.useAttributeFor(GeoWebCacheConfiguration.class, "xmlns");

        xs.alias("wmsRasterFilterUpdate", WMSRasterFilterUpdate.class);

        return xs;
    }

    public static XmlFilterUpdate parseXMLFilterUpdate(final InputStream in) {
        XStream xs = getConfiguredXStream(new GeoWebCacheXStream(new DomDriver()));

        XmlFilterUpdate fu = (XmlFilterUpdate) xs.fromXML(in);

        return fu;
    }
}
