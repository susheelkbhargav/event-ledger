package com.eventledger.gateway.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.ResourceAccessException;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "account-service.read-timeout=300ms")
class ReadTimeoutTest {

    static WireMockServer wireMock = new WireMockServer(0);

    @BeforeAll static void start() { wireMock.start(); }
    @AfterAll static void stop() { wireMock.stop(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired AccountServiceClient client;
    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;

    @Test
    void getBalance_slowResponse_timesOutAndRetries() {
        circuitBreakerRegistry.circuitBreaker("accountService").reset();
        wireMock.resetAll();
        wireMock.stubFor(get(urlEqualTo("/api/v1/accounts/acc-slow/balance"))
            .willReturn(okJson("{\"accountId\":\"acc-slow\",\"balance\":1}").withFixedDelay(2000)));

        assertThatThrownBy(() -> client.getBalance("acc-slow"))
            .isInstanceOf(ResourceAccessException.class);

        wireMock.verify(3, getRequestedFor(urlEqualTo("/api/v1/accounts/acc-slow/balance")));
    }
}
