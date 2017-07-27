/*
 *
 *  *  (c) 2017 - 2016 Open Source Geospatial Foundation - all rights reserved
 *  *  * This code is licensed under the GPL 2.0 license, available at the root
 *  *  * application directory.
 *  *
 *
 */

package org.geowebcache.restng.seed;

import org.geowebcache.GeoWebCacheException;
import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.config.XMLConfigurationBackwardsCompatibilityTest;
import org.geowebcache.grid.GridSetBroker;
import org.geowebcache.restng.controller.MassTruncateController;
import org.geowebcache.storage.StorageBroker;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;

import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.classextension.EasyMock.createMock;
import static org.easymock.classextension.EasyMock.replay;
import static org.easymock.classextension.EasyMock.verify;
import static org.junit.Assert.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({
        "file*:/webapp/WEB-INF/web.xml",
        "file*:/webapp/WEB-INF/geowebcache-servlet.xml"
})
public class MassTruncateControllerTest {
    private MockMvc mockMvc;

    MassTruncateController mtc;

    @Before
    public void setup() throws GeoWebCacheException {
        GridSetBroker gridSetBroker = new GridSetBroker(false, false);
        XMLConfiguration xmlConfig = loadXMLConfig();
        xmlConfig.initialize(gridSetBroker);

        mtc = new MassTruncateController();
        mtc.setXmlConfig(xmlConfig);

        this.mockMvc = MockMvcBuilders.standaloneSetup(mtc).build();
    }

    @Test
    public void testTruncateLayer() throws Exception {
        String layerName = "test";
        String requestBody = "<truncateLayer><layerName>"+layerName+"</layerName></truncateLayer>";

        StorageBroker sb = createMock(StorageBroker.class);
        expect(sb.delete(eq(layerName))).andReturn(true);
        replay(sb);

        mtc.setStorageBroker(sb);

        this.mockMvc.perform(post("/restng/masstruncate")
                .contentType(MediaType.TEXT_XML)
                .content(requestBody)
                .contextPath("/restng")).andExpect(status().is2xxSuccessful());
        verify(sb);
    }

    @Test
    public void testGetMassTruncate() throws Exception {
        MvcResult result = this.mockMvc.perform(get("/restng/masstruncate")
                .contentType(MediaType.APPLICATION_ATOM_XML)
                .contextPath("/restng")).andReturn();
        
        assertEquals(200, result.getResponse().getStatus());

        System.out.print(result);
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
