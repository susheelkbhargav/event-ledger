package com.eventledger.gateway.api;

import com.eventledger.gateway.domain.EventRecordRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerTest {

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
    void clean() {
        repository.deleteAll();
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/api/v1/transactions"))
            .willReturn(WireMock.okJson("{\"eventId\":\"x\",\"accountId\":\"x\",\"duplicate\":false}")));
    }

    static String eventJson(String eventId, String accountId, String type, String amount, String ts) {
        return """
            {"eventId":"%s","accountId":"%s","type":"%s","amount":%s,
             "currency":"EUR","eventTimestamp":"%s"}""".formatted(eventId, accountId, type, amount, ts);
    }

    @Test
    void submit_validEvent_returns201WithBody() throws Exception {
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-1", "acc-1", "CREDIT", "10.50", "2026-07-01T10:00:00Z")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eventId").value("evt-1"))
            .andExpect(jsonPath("$.status").value("APPLIED"))
            .andExpect(jsonPath("$.receivedAt").exists());
    }

    @Test
    void submit_missingAccountId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"eventId":"evt-2","type":"CREDIT","amount":10,
                     "currency":"EUR","eventTimestamp":"2026-07-01T10:00:00Z"}"""))
            .andExpect(status().isBadRequest());
    }

    @Test
    void submit_negativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("evt-3", "acc-1", "DEBIT", "-5", "2026-07-01T10:00:00Z")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void byId_unknownEvent_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/events/nope")).andExpect(status().isNotFound());
    }

    @Test
    void byAccount_outOfOrderSubmission_listsChronologically() throws Exception {
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
            .content(eventJson("evt-late", "acc-9", "CREDIT", "1", "2026-07-02T00:00:00Z")));
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON)
            .content(eventJson("evt-early", "acc-9", "CREDIT", "1", "2026-07-01T00:00:00Z")));

        mockMvc.perform(get("/api/v1/events").param("account", "acc-9"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventId").value("evt-early"))
            .andExpect(jsonPath("$[1].eventId").value("evt-late"));
    }

    @Test
    void byAccount_noEvents_returns200EmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/events").param("account", "ghost"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }
}
