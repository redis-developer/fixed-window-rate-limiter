package com.redis.rl;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;
import static org.springframework.http.MediaType.TEXT_PLAIN;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.http.MediaType.TEXT_PLAIN;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisCallback;
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
        ).filter(new RateLimiterHandlerFilterFunction(redisTemplate)).build();
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

  private ReactiveRedisTemplate<String, Long> redisTemplate;

  public RateLimiterHandlerFilterFunction(ReactiveRedisTemplate<String, Long> redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  @Override
  public Mono<ServerResponse> filter(ServerRequest request, HandlerFunction<ServerResponse> next) {
    int currentMinute = LocalTime.now().getMinute();
    String key = String.format("rl_%s:%s", requestAddress(request.remoteAddress()), currentMinute);
    System.out.println(">>>> key " + key);

  private Mono<ServerResponse> incrAndExpireKey(String key, ServerRequest request,
    HandlerFunction<ServerResponse> next) {
    return redisTemplate.execute(new ReactiveRedisCallback<List<Object>>() {
      @Override
      public Publisher<List<Object>> doInRedis(ReactiveRedisConnection connection) throws DataAccessException {
        ByteBuffer bbKey = ByteBuffer.wrap(key.getBytes());
        return Mono.zip( //
            connection.numberCommands().incr(bbKey), //
            connection.keyCommands().expire(bbKey, Duration.ofSeconds(59L)) //
        ).then(Mono.empty());
      }
    }).then(next.handle(request));
  }

  private String requestAddress(Optional<InetSocketAddress> maybeAddress) {
    return maybeAddress.isPresent() ? maybeAddress.get().getHostName() : "";
  }
}
