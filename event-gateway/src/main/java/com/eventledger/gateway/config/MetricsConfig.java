package com.eventledger.gateway.config;

import com.eventledger.gateway.domain.EventRecordRepository;
import com.eventledger.gateway.domain.EventStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    MeterBinder replayQueueDepth(EventRecordRepository repository) {
        return registry -> Gauge.builder("gateway.replay.queue.depth",
                repository, r -> r.countByStatus(EventStatus.QUEUED))
            .register(registry);
    }
}
