package com.eventledger.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(AccountServiceProps.class)
public class ClientConfig {

    @Bean
    RestClient accountServiceRestClient(RestClient.Builder builder, AccountServiceProps props) {
        HttpClientSettings settings = HttpClientSettings.defaults()
            .withConnectTimeout(props.connectTimeout())
            .withReadTimeout(props.readTimeout());
        // HTTP/1.1: Account Service runs plain-HTTP Tomcat; the JDK client's
        // default h2c upgrade also breaks WireMock POSTs (RST_STREAM).
        return builder
            .baseUrl(props.baseUrl())
            .requestFactory(ClientHttpRequestFactoryBuilder.jdk()
                .withHttpClientCustomizer(c -> c.version(HttpClient.Version.HTTP_1_1))
                .build(settings))
            .build();
    }
}
