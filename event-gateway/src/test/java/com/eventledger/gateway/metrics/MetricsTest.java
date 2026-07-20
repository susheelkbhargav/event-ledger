package com.eventledger.gateway.metrics;

import com.eventledger.gateway.domain.EventRecordRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = "gateway.replay.interval=1h") // keep replay out of counter assertions
@AutoConfigureMockMvc
class MetricsTest {

    static WireMockServer wireMock = new WireMockServer(0);

    @BeforeAll static void start() { wireMock.start(); }
    @AfterAll static void stop() { wireMock.stop(); }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("account-service.base-url", () -> "http://localhost:" + wireMock.port());
    }

    @Autowired MockMvc mockMvc;
    @Autowired MeterRegistry meterRegistry;
    @Autowired EventRecordRepository repository;
    @Autowired CircuitBreakerRegistry circuitBreakerRegistry;

    static String eventJson(String id) {
        return """
            {"eventId":"%s","accountId":"acc-m","type":"CREDIT","amount":1,
             "currency":"EUR","eventTimestamp":"2026-07-01T10:00:00Z"}""".formatted(id);
    }

    // No meterRegistry.clear(): it would deregister the queue-depth gauge for
    // the rest of the context's life. Counters accumulate across tests, so
    // assertions are relative deltas.
    @BeforeEach
    void reset() {
        wireMock.resetAll();
        repository.deleteAll();
        circuitBreakerRegistry.circuitBreaker("accountService").reset();
    }

    double counterValue(String outcome) {
        Counter counter = meterRegistry.find("gateway.events.received")
            .tag("type", "CREDIT").tag("outcome", outcome).counter();
        return counter == null ? 0.0 : counter.count();
    }

    @Test
    void submit_applied_incrementsAppliedCounter() throws Exception {
        wireMock.stubFor(WireMock.post(urlEqualTo("/api/v1/transactions")).willReturn(okJson(
            "{\"eventId\":\"x\",\"accountId\":\"x\",\"duplicate\":false}")));
        double before = counterValue("applied");

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
            .content(eventJson("evt-m1")));

        assertThat(counterValue("applied") - before).isEqualTo(1.0);
    }

    @Test
    void submit_duplicate_incrementsDuplicateCounter() throws Exception {
        wireMock.stubFor(WireMock.post(urlEqualTo("/api/v1/transactions")).willReturn(okJson(
            "{\"eventId\":\"x\",\"accountId\":\"x\",\"duplicate\":false}")));
        double before = counterValue("duplicate");

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
            .content(eventJson("evt-m2")));
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
            .content(eventJson("evt-m2")));

        assertThat(counterValue("duplicate") - before).isEqualTo(1.0);
    }

    @Test
    void submit_queued_incrementsQueuedCounterAndGauge() throws Exception {
        wireMock.stubFor(WireMock.post(urlEqualTo("/api/v1/transactions"))
            .willReturn(aResponse().withStatus(503)));
        double before = counterValue("queued");

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
            .content(eventJson("evt-m3")));

        assertThat(counterValue("queued") - before).isEqualTo(1.0);
        // Gauge reads live from the repository: exactly one QUEUED row exists
        // after deleteAll + this submit.
        assertThat(meterRegistry.find("gateway.replay.queue.depth").gauge().value()).isEqualTo(1.0);
    }

    @Test
    void submit_validationFailure_incrementsRejectedCounter() throws Exception {
        Counter rejected = meterRegistry.find("gateway.events.received")
            .tag("type", "UNKNOWN").tag("outcome", "rejected").counter();
        double before = rejected == null ? 0.0 : rejected.count();

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
            .content("{\"eventId\":\"evt-bad\"}"));

        Counter after = meterRegistry.find("gateway.events.received")
            .tag("type", "UNKNOWN").tag("outcome", "rejected").counter();
        assertThat(after).isNotNull();
        assertThat(after.count() - before).isEqualTo(1.0);
    }
}
