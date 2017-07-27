/*
 *
 *  *  (c) 2017 - 2016 Open Source Geospatial Foundation - all rights reserved
 *  *  * This code is licensed under the GPL 2.0 license, available at the root
 *  *  * application directory.
 *  *
 *
 */

package org.geowebcache.dq.rest;

import org.geowebcache.config.XMLConfiguration;
import org.geowebcache.diskquota.DiskQuotaConfig;
import org.geowebcache.diskquota.DiskQuotaMonitor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.mock;

@Configuration
@ComponentScan({"org.geowebcache.dq.rest"})
@EnableWebMvc
@Profile("test")
public class DiskQuotaRestWebConfig {

    @Bean
    public DiskQuotaMonitor diskQuotaMonitor() {
        DiskQuotaMonitor diskQuotaMonitor = mock(DiskQuotaMonitor.class);
        DiskQuotaConfig diskQuotaConfig = mock(DiskQuotaConfig.class);
        diskQuotaConfig.setDefaults();
        diskQuotaMonitor.saveConfig(diskQuotaConfig);
        return diskQuotaMonitor;
    }

    @Bean
    public XMLConfiguration xmlConfiguration() {
        XMLConfiguration gwcConfiguration = mock(XMLConfiguration.class);

        return gwcConfiguration;
    }

    @Bean
    public DiskQuotaController diskQuotaController(){
        DiskQuotaController diskQuotaController = mock(DiskQuotaController.class);
        return diskQuotaController;
    }
}
