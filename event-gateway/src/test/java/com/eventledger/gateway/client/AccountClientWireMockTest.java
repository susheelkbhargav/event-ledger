package com.eventledger.gateway.client;

import com.eventledger.gateway.domain.EventRecordRepository;
import com.eventledger.gateway.domain.EventStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AccountClientWireMockTest {

    static WireMockServer wireMock = new WireMockServer(0);

    @BeforeAll
    static void start() { wireMock.start(); }

    @AfterAll
    static void stop() { wireMock.stop(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired MockMvc mockMvc;
    @Autowired EventRecordRepository repository;

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        repository.deleteAll();
    }

    static final String EVENT = """
        {"eventId":"evt-apply","accountId":"acc-1","type":"CREDIT","amount":10,
         "currency":"EUR","eventTimestamp":"2026-07-01T10:00:00Z"}""";

    @Test
    void submit_validEvent_appliesRemotelyAndReturnsApplied() throws Exception {
        wireMock.stubFor(WireMock.post(urlEqualTo("/api/v1/transactions")).willReturn(okJson(
            "{\"eventId\":\"evt-apply\",\"accountId\":\"acc-1\",\"duplicate\":false}")));

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(EVENT))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("APPLIED"));

        assertThat(repository.findById("evt-apply").orElseThrow().getStatus()).isEqualTo(EventStatus.APPLIED);
        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v1/transactions")));
    }

    @Test
    void balance_knownAccount_proxiesToAccountService() throws Exception {
        wireMock.stubFor(WireMock.get(urlEqualTo("/api/v1/accounts/acc-1/balance")).willReturn(okJson(
            "{\"accountId\":\"acc-1\",\"balance\":42.00}")));

        mockMvc.perform(get("/api/v1/accounts/acc-1/balance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(42.00));
    }

    @Test
    void balance_unknownAccount_returns404() throws Exception {
        wireMock.stubFor(WireMock.get(urlEqualTo("/api/v1/accounts/ghost/balance"))
            .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(404)));

        mockMvc.perform(get("/api/v1/accounts/ghost/balance")).andExpect(status().isNotFound());
    }
}
