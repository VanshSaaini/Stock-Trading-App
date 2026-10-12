package com.trading.orderservice.config;

import com.trading.orderservice.client.MarketDataClient;
import com.trading.orderservice.client.PortfolioClient;
import com.trading.orderservice.client.UserServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpClientsConfig {

    @Bean
    MarketDataClient marketDataClient(@Value("${market.service.url}") String baseUrl) {
        return createClient(baseUrl, MarketDataClient.class);
    }

    @Bean
    UserServiceClient userServiceClient(@Value("${user.service.url}") String baseUrl) {
        return createClient(baseUrl, UserServiceClient.class);
    }

    @Bean
    PortfolioClient portfolioClient(@Value("${portfolio.service.url}") String baseUrl) {
        return createClient(baseUrl, PortfolioClient.class);
    }

    private static <T> T createClient(String baseUrl, Class<T> clientType) {

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(5));

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientType);
    }
}
