package com.redis.rl;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.http.MediaType.TEXT_PLAIN;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.GenericToStringSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.web.reactive.function.server.HandlerFilterFunction;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import reactor.core.publisher.Mono;

import org.springframework.web.reactive.function.BodyInserters;

@SpringBootApplication
public class FixedWindowRateLimiterApplication {

  @Bean
  RouterFunction<ServerResponse> routes() {
    return route() //
        .GET("/api/ping", r -> ok() //
            .contentType(TEXT_PLAIN) //
            .body(BodyInserters.fromValue("PONG")) //
        ).filter(new RateLimiterHandlerFilterFunction()).build();
  }

  @Bean
  ReactiveRedisTemplate<String, Long> reactiveRedisTemplate(ReactiveRedisConnectionFactory factory) {
    JdkSerializationRedisSerializer jdkSerializationRedisSerializer = new JdkSerializationRedisSerializer();
    StringRedisSerializer stringRedisSerializer = StringRedisSerializer.UTF_8;
    GenericToStringSerializer<Long> longToStringSerializer = new GenericToStringSerializer<>(Long.class);
    ReactiveRedisTemplate<String, Long> template = new ReactiveRedisTemplate<>(factory,
        RedisSerializationContext.<String, Long>newSerializationContext(jdkSerializationRedisSerializer)
            .key(stringRedisSerializer).value(longToStringSerializer).build());
    return template;
  }

  @Autowired
  private ReactiveRedisTemplate<String, Long> redisTemplate;

  public static void main(String[] args) {
    SpringApplication.run(FixedWindowRateLimiterApplication.class, args);
  }

}

class RateLimiterHandlerFilterFunction implements HandlerFilterFunction<ServerResponse, ServerResponse> {

  @Override
  public Mono<ServerResponse> filter(ServerRequest request, HandlerFunction<ServerResponse> next) {
    return next.handle(request);
  }
}
