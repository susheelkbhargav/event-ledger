package com.eventledger.gateway.service;

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
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IdempotencyTest {

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

    static final String BODY = """
        {"eventId":"evt-dup","accountId":"acc-1","type":"CREDIT","amount":10,
         "currency":"EUR","eventTimestamp":"2026-07-01T10:00:00Z"}""";

    @BeforeEach
    void clean() {
        repository.deleteAll();
        wireMock.resetAll();
        wireMock.stubFor(WireMock.post(WireMock.urlEqualTo("/api/v1/transactions"))
            .willReturn(WireMock.okJson("{\"eventId\":\"x\",\"accountId\":\"x\",\"duplicate\":false}")));
    }

    @Test
    void submit_duplicateEventId_returns200WithOriginalAndOneRow() throws Exception {
        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value("evt-dup"));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void submit_concurrentDuplicates_exactlyOneRowOne201One200() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = List.of(submitAsync(pool, start), submitAsync(pool, start));
            start.countDown();
            List<Integer> statuses = List.of(results.get(0).get(), results.get(1).get());

            assertThat(repository.count()).isEqualTo(1);
            assertThat(statuses).containsExactlyInAnyOrder(201, 200);
        } finally {
            pool.shutdown();
        }
    }

    private Future<Integer> submitAsync(ExecutorService pool, CountDownLatch start) {
        return pool.submit(() -> {
            start.await();
            MvcResult result = mockMvc.perform(
                post("/api/v1/events").contentType(MediaType.APPLICATION_JSON).content(BODY)).andReturn();
            return result.getResponse().getStatus();
        });
    }
}
