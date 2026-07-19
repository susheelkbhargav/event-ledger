package com.eventledger.gateway.tracing;

import com.eventledger.gateway.domain.EventRecordRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTracing
@ExtendWith(OutputCaptureExtension.class)
class TracePropagationTest {

    static WireMockServer wireMock = new WireMockServer(0);

    @BeforeAll static void start() { wireMock.start(); }
    @AfterAll static void stop() { wireMock.stop(); }

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
        wireMock.stubFor(WireMock.post(urlEqualTo("/api/v1/transactions"))
            .willReturn(okJson("{\"eventId\":\"x\",\"accountId\":\"x\",\"duplicate\":false}")));
    }

    @Test
    void submit_outboundCall_carriesW3CTraceparent(CapturedOutput output) throws Exception {
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content("""
            {"eventId":"evt-trace","accountId":"acc-1","type":"CREDIT","amount":1,
             "currency":"EUR","eventTimestamp":"2026-07-01T10:00:00Z"}"""));

        wireMock.verify(postRequestedFor(urlEqualTo("/api/v1/transactions"))
            .withHeader("traceparent", matching("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")));

        // Correlation proof: the trace id sent on the wire must appear in the
        // structured log output, whatever field name the format uses.
        String traceparent = wireMock.findAll(postRequestedFor(urlEqualTo("/api/v1/transactions")))
            .get(0).getHeader("traceparent");
        String traceId = traceparent.split("-")[1];
        assertThat(output.getOut()).contains(traceId);
    }
}
