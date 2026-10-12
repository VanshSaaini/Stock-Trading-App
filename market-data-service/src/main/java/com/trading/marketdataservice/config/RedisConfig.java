package com.trading.marketdataservice.config;

import com.trading.marketdataservice.dto.StockPrice;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    /**
     * Typed template: values are stored as plain JSON and always read back as StockPrice.
     * (A generic serializer without default-typing returns LinkedHashMap, which
     * breaks the (StockPrice) cast when reading from the cache.)
     */
    @Bean
    public RedisTemplate<String, StockPrice> stockPriceRedisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, StockPrice> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JacksonJsonRedisSerializer<>(StockPrice.class));
        template.afterPropertiesSet();
        return template;
    }
}
