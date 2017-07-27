/*
 *
 *  *  (c) 2017 - 2016 Open Source Geospatial Foundation - all rights reserved
 *  *  * This code is licensed under the GPL 2.0 license, available at the root
 *  *  * application directory.
 *  *
 *
 */

package org.geowebcache.restng.statistics;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.restng.controller.MemoryCacheController;
import org.geowebcache.storage.blobstore.memory.CacheConfiguration;
import org.geowebcache.storage.blobstore.memory.CacheProvider;
import org.geowebcache.storage.blobstore.memory.MemoryBlobStore;
import org.geowebcache.storage.blobstore.memory.NullBlobStore;
import org.geowebcache.storage.blobstore.memory.guava.GuavaCacheProvider;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({
        "file*:/webapp/WEB-INF/web.xml",
        "file*:/webapp/WEB-INF/geowebcache-servlet.xml"
})
public class MemoryCacheControllerTest {
    private MockMvc mockMvc;

    MemoryCacheController mcc;

    @Before
    public void setup() throws GeoWebCacheException {
        GridSetBroker gridSetBroker = new GridSetBroker(false, false);
        XMLConfiguration xmlConfig = loadXMLConfig();
        xmlConfig.initialize(gridSetBroker);

        mcc = new MemoryCacheController();
        mcc.setXMLConfiguration(xmlConfig);
        this.mockMvc = MockMvcBuilders.standaloneSetup(mcc).build();
    }

    @Test
    public void testStatisticsXml() throws Exception {
        //Initialize a new MemoryBlobStore with cache
        CacheProvider cache = new GuavaCacheProvider(new CacheConfiguration());
        NullBlobStore nbs = new NullBlobStore();
        cache.clear();

        MemoryBlobStore mbs = new MemoryBlobStore();
        mbs.setStore(nbs);
        mcc.setBlobStore(mbs);
        mbs.setCacheProvider(cache);

        this.mockMvc.perform(get("/restng/statistics.xml")
                .contextPath("/restng")).andExpect(status().is2xxSuccessful());
    }

    @Test
    public void testStatisticsJson() throws Exception {
        //Initialize a new MemoryBlobStore with cache
        CacheProvider cache = new GuavaCacheProvider(new CacheConfiguration());
        NullBlobStore nbs = new NullBlobStore();
        cache.clear();

        MemoryBlobStore mbs = new MemoryBlobStore();
        mbs.setStore(nbs);
        mcc.setBlobStore(mbs);
        mbs.setCacheProvider(cache);

        this.mockMvc.perform(get("/restng/statistics.json")
                .contextPath("/restng")).andExpect(status().is2xxSuccessful());
    }

    private XMLConfiguration loadXMLConfig() {

        InputStream is = XMLConfiguration.class
                .getResourceAsStream(XMLConfigurationBackwardsCompatibilityTest.GWC_125_CONFIG_FILE);
        XMLConfiguration xmlConfig = null;
        try {
            xmlConfig = new XMLConfiguration(is);
        } catch (Exception e) {
            // Do nothing
        }

        return xmlConfig;
    }
}
