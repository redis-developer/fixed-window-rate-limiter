package com.redis.rl;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;
import static org.springframework.http.MediaType.TEXT_PLAIN;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
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
