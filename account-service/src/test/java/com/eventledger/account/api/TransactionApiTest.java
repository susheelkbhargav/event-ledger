package com.eventledger.account.api;

import com.eventledger.account.domain.AccountRepository;
import com.eventledger.account.domain.TransactionRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired AccountRepository accountRepository;
    @Autowired TransactionRecordRepository transactionRecordRepository;

    @BeforeEach
    void clean() {
        transactionRecordRepository.deleteAll();
        accountRepository.deleteAll();
    }

    static String txJson(String eventId, String accountId, String type, String amount) {
        return """
            {"eventId":"%s","accountId":"%s","type":"%s","amount":%s,
             "currency":"EUR","eventTimestamp":"2026-07-01T10:00:00Z"}""".formatted(eventId, accountId, type, amount);
    }

    @Test
    void apply_newCredit_returns201AndUpdatesBalance() throws Exception {
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                .content(txJson("evt-1", "acc-1", "CREDIT", "100.50")))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/accounts/acc-1/balance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(100.50));
    }

    @Test
    void apply_creditThenDebit_balanceReflectsNetDelta() throws Exception {
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
            .content(txJson("evt-c", "acc-2", "CREDIT", "100")));
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
            .content(txJson("evt-d", "acc-2", "DEBIT", "30")));

        mockMvc.perform(get("/api/v1/accounts/acc-2/balance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(70));
    }

    @Test
    void apply_duplicateEventId_returns200AndDoesNotDoubleApply() throws Exception {
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
            .content(txJson("evt-dup", "acc-3", "CREDIT", "10")));

        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
                .content(txJson("evt-dup", "acc-3", "CREDIT", "10")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.duplicate").value(true));

        assertThat(transactionRecordRepository.count()).isEqualTo(1);
        mockMvc.perform(get("/api/v1/accounts/acc-3/balance"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(10));
    }

    @Test
    void apply_concurrentDistinctEventsSameAccount_balanceIsExactSum() throws Exception {
        int events = 10;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(events);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < events; i++) {
                final String body = txJson("evt-conc-" + i, "acc-conc", "CREDIT", "1");
                futures.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/v1/transactions")
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> f : futures) assertThat(f.get()).isEqualTo(201);

            assertThat(accountRepository.findById("acc-conc").orElseThrow().getBalance())
                .isEqualByComparingTo(new BigDecimal("10"));
        } finally {
            pool.shutdown();
        }
    }

    @Test
    void balance_unknownAccount_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/ghost/balance")).andExpect(status().isNotFound());
    }

    @Test
    void details_knownAccount_returnsBalanceAndCount() throws Exception {
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON)
            .content(txJson("evt-det", "acc-4", "CREDIT", "5")));

        mockMvc.perform(get("/api/v1/accounts/acc-4"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountId").value("acc-4"))
            .andExpect(jsonPath("$.balance").value(5))
            .andExpect(jsonPath("$.transactionCount").value(1));
    }
}
