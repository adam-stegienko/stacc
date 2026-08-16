package com.adam_stegienko.stacc_google_api_rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration," +
                "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration",
        "api.googleads.enabled=false"
})
@AutoConfigureMockMvc
class StaccGoogleApiRestTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void homeEndpointIsAccessible() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string("ST Automated Campaign Controller Google Api Rest is running!"));
    }

    @Test
    void googleAdsEndpointsRequireGoogleAdsEnabled() throws Exception {
        // With api.googleads.enabled=false the controller is not registered,
        // so all its routes return 404.
        mockMvc.perform(get("/v1/api/google-ads/campaigns/status/SomeCampaign")
                        .param("customerId", "123456789"))
                .andExpect(status().isNotFound());
    }
}

