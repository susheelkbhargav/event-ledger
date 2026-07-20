package com.eventledger.gateway.replay;

import com.eventledger.gateway.domain.EventRecordRepository;
import com.eventledger.gateway.domain.EventStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "gateway.replay.interval=200ms")
@AutoConfigureMockMvc
class ReplayTest {

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

    @Test
    void queuedEvent_accountServiceRecovers_replayedToApplied() throws Exception {
        wireMock.resetAll();
        repository.deleteAll();
        circuitBreakerRegistry.circuitBreaker("accountService").reset();

        // Account Service down → event lands QUEUED
        wireMock.stubFor(WireMock.post(urlEqualTo("/api/v1/transactions"))
            .willReturn(aResponse().withStatus(503)));
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content("""
                {"eventId":"evt-replay","accountId":"acc-1","type":"CREDIT","amount":10,
                 "currency":"EUR","eventTimestamp":"2026-07-01T10:00:00Z"}"""))
            .andExpect(status().isAccepted());

        // Account Service recovers
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(urlEqualTo("/api/v1/transactions")).willReturn(okJson(
            "{\"eventId\":\"evt-replay\",\"accountId\":\"acc-1\",\"duplicate\":false}")));
        circuitBreakerRegistry.circuitBreaker("accountService").reset();

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
            assertThat(repository.findById("evt-replay").orElseThrow().getStatus())
                .isEqualTo(EventStatus.APPLIED));
    }
}
