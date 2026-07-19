package com.eventledger.gateway.client;

import com.eventledger.gateway.client.dto.ApplyTransactionRequest;
import com.eventledger.gateway.domain.TransactionType;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

@SpringBootTest
class ResilienceTest {

    static WireMockServer wireMock = new WireMockServer(0);

    @BeforeAll static void start() { wireMock.start(); }
    @AfterAll static void stop() { wireMock.stop(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired AccountServiceClient client;
    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;

    static ApplyTransactionRequest request(String eventId) {
        return new ApplyTransactionRequest(eventId, "acc-r", TransactionType.CREDIT,
            BigDecimal.ONE, "EUR", Instant.parse("2026-07-01T10:00:00Z"));
    }

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        circuitBreakerRegistry.circuitBreaker("accountService").reset();
    }

    @Test
    void apply_5xxResponses_retriedExactly3Times() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/transactions")).willReturn(aResponse().withStatus(503)));

        assertThatThrownBy(() -> client.apply(request("evt-retry")))
            .isInstanceOf(AccountServiceServerException.class);

        wireMock.verify(3, postRequestedFor(urlEqualTo("/api/v1/transactions")));
    }

    @Test
    void apply_failsTwiceThenSucceeds_recoversWithinRetryBudget() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/transactions")).inScenario("recovery")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(503)).willSetStateTo("second-failure"));
        wireMock.stubFor(post(urlEqualTo("/api/v1/transactions")).inScenario("recovery")
            .whenScenarioStateIs("second-failure")
            .willReturn(aResponse().withStatus(503)).willSetStateTo("recovered"));
        wireMock.stubFor(post(urlEqualTo("/api/v1/transactions")).inScenario("recovery")
            .whenScenarioStateIs("recovered")
            .willReturn(okJson("{\"eventId\":\"evt-rec\",\"accountId\":\"acc-r\",\"duplicate\":false}")));

        var response = client.apply(request("evt-rec"));

        assertThat(response.duplicate()).isFalse();
        wireMock.verify(3, postRequestedFor(urlEqualTo("/api/v1/transactions")));
    }

    @Test
    void apply_4xxResponse_notRetried() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/transactions")).willReturn(aResponse().withStatus(400)));

        catchThrowable(() -> client.apply(request("evt-400")));

        wireMock.verify(1, postRequestedFor(urlEqualTo("/api/v1/transactions")));
    }

    @Test
    void apply_persistentFailures_opensCircuitAndStopsCalling() {
        wireMock.stubFor(post(urlEqualTo("/api/v1/transactions")).willReturn(aResponse().withStatus(503)));

        Throwable lastFailure = null;
        for (int i = 0; i < 10; i++) {
            String eventId = "evt-cb-" + i;
            lastFailure = catchThrowable(() -> client.apply(request(eventId)));
            if (lastFailure instanceof CallNotPermittedException) break;
        }
        assertThat(lastFailure).isInstanceOf(CallNotPermittedException.class);

        int callsBefore = wireMock.findAll(postRequestedFor(urlEqualTo("/api/v1/transactions"))).size();
        assertThatThrownBy(() -> client.apply(request("evt-cb-after")))
            .isInstanceOf(CallNotPermittedException.class);
        int callsAfter = wireMock.findAll(postRequestedFor(urlEqualTo("/api/v1/transactions"))).size();

        assertThat(callsAfter).isEqualTo(callsBefore); // open circuit: no HTTP traffic
    }
}
