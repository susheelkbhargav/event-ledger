package com.eventledger.gateway.api;

import com.eventledger.gateway.domain.EventRecordRepository;
import com.eventledger.gateway.domain.EventStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
class DegradationTest {

    static WireMockServer wireMock = new WireMockServer(0);

    @BeforeAll static void start() { wireMock.start(); }
    @AfterAll static void stop() { wireMock.stop(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired MockMvc mockMvc;
    @Autowired EventRecordRepository repository;
    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        repository.deleteAll();
        circuitBreakerRegistry.circuitBreaker("accountService").reset();
    }

    @Test
    void submit_accountServiceDown_returns202Queued() throws Exception {
        wireMock.stubFor(WireMock.post(urlEqualTo("/api/v1/transactions"))
            .willReturn(aResponse().withStatus(503)));

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content("""
                {"eventId":"evt-q","accountId":"acc-1","type":"CREDIT","amount":10,
                 "currency":"EUR","eventTimestamp":"2026-07-01T10:00:00Z"}"""))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("QUEUED"));

        assertThat(repository.findById("evt-q").orElseThrow().getStatus()).isEqualTo(EventStatus.QUEUED);
    }

    @Test
    void localReads_accountServiceDown_unaffected() throws Exception {
        wireMock.stubFor(WireMock.post(urlEqualTo("/api/v1/transactions"))
            .willReturn(aResponse().withStatus(503)));
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content("""
            {"eventId":"evt-local","accountId":"acc-2","type":"CREDIT","amount":5,
             "currency":"EUR","eventTimestamp":"2026-07-01T10:00:00Z"}"""));

        mockMvc.perform(get("/api/v1/events/evt-local")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/events").param("account", "acc-2")).andExpect(status().isOk());
    }

    @Test
    void balanceProxy_accountServiceDown_returns503ProblemDetail() throws Exception {
        wireMock.stubFor(WireMock.get(urlEqualTo("/api/v1/accounts/acc-1/balance"))
            .willReturn(aResponse().withStatus(503)));

        mockMvc.perform(get("/api/v1/accounts/acc-1/balance"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.title").value("Account Service Unavailable"))
            .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void submit_validationFailure_problemDetailAggregatesFieldMessages() throws Exception {
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"evt-bad\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").isNotEmpty())
            .andExpect(jsonPath("$.title").value("Validation Failed"));
    }
}
