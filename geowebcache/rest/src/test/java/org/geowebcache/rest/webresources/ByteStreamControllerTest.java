package org.geowebcache.rest.webresources;

import org.geowebcache.rest.controller.ByteStreamController;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({
        "file*:/webapp/WEB-INF/web.xml",
        "file*:/webapp/WEB-INF/geowebcache-servlet.xml"
})
public class ByteStreamControllerTest {
    private MockMvc mockMvc;


    ByteStreamController bsc;

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        bsc = new ByteStreamController();
        this.mockMvc = MockMvcBuilders.standaloneSetup(bsc).build();
    }

    @Test
    public void setByteStreamController()  throws Exception {
        mockMvc.perform(get("/web/doesnt%20exist")).andExpect(status().is4xxClientError());
    }

    @Test
    public void testResourceFoundPNG() throws Exception {
        mockMvc.perform(get("/web/test.png")).andExpect(status().is4xxClientError());
    }

    @Test
    public void testResourceFoundCSS() throws Exception {
        mockMvc.perform(get("/web/test.css")).andExpect(status().is4xxClientError());
    }

    @Test
    public void testClass() throws Exception  {
        mockMvc.perform(get("/web/ByteStreamerRestlet.class")).andExpect(status().is4xxClientError());
    }

    @Test
    public void testAbsolute() throws Exception  {
        mockMvc.perform(get("/web/org/geowebcache/shouldnt/access/test.png")).andExpect(status().is4xxClientError());
    }

    @Test
    public void testBackreference() throws Exception  {
        mockMvc.perform(get("/web/../../shouldnt/access/test.png")).andExpect(status().is4xxClientError());
    }

    @Test
    public void testBackreference2() throws Exception  {
        mockMvc.perform(get("/web/foo/../../../shouldnt/access/test.png")).andExpect(status().is4xxClientError());
    }
}
