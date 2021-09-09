package com.redis.rl;

import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.HandlerFilterFunction;
import org.springframework.web.reactive.function.server.HandlerFunction;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.redis.lettucemod.api.StatefulRedisModulesConnection;
import com.redis.lettucemod.api.gears.Registration;
import com.redis.lettucemod.api.sync.RedisGearsCommands;
import com.redis.lettucemod.output.ExecutionResults;

import io.lettuce.core.RedisCommandExecutionException;
import reactor.core.publisher.Mono;

@SpringBootApplication
public class FixedWindowRateLimiterApplication {

  Logger logger = LoggerFactory.getLogger(FixedWindowRateLimiterApplication.class);

  @Bean
  RouterFunction<ServerResponse> routes() {
    return route() //
        .GET("/api/ping", r -> ok() //
            .contentType(TEXT_PLAIN) //
            .body(BodyInserters.fromValue("PONG")) //
        ).filter(new RateLimiterHandlerFilterFunction(connection, maxRequestPerMinute)).build();
  }

  @Autowired
  StatefulRedisModulesConnection<String, String> connection;

  @Value("${MAX_REQUESTS_PER_MINUTE}")
  Long maxRequestPerMinute;

  @PostConstruct
  public void loadGearsScript() throws IOException {
    String py = StreamUtils.copyToString(new ClassPathResource("scripts/rateLimiter.py").getInputStream(),
        Charset.defaultCharset());
    RedisGearsCommands<String, String> gears = connection.sync();
    List<Registration> registrations = gears.dumpregistrations();

    Optional<String> maybeRegistrationId = getGearsRegistrationIdForTrigger(registrations, "RateLimiter");
    if (maybeRegistrationId.isEmpty()) {
      try {
        ExecutionResults er = gears.pyexecute(py);
        if (er.isOk()) {
          logger.info("RateLimiter.py has been registered");
        } else if (er.isError()) {
          logger.error(String.format("Could not register RateLimiter.py -> %s", Arrays.toString(er.getErrors().toArray())));
        }
      } catch (RedisCommandExecutionException rcee) {
        logger.error(String.format("Could not register RateLimiter.py -> %s", rcee.getMessage()));
      }
    } else {
      logger.info("RateLimiter.py has already been registered");
    }
  }

  private Optional<String> getGearsRegistrationIdForTrigger(List<Registration> registrations , String trigger) {
    return registrations.stream().filter(r -> r.getData().getArgs().get("trigger").equals("RateLimiter")).findFirst().map(Registration::getId);
  }

  public static void main(String[] args) {
    SpringApplication.run(FixedWindowRateLimiterApplication.class, args);
  }

}

class RateLimiterHandlerFilterFunction implements HandlerFilterFunction<ServerResponse, ServerResponse> {

  private StatefulRedisModulesConnection<String, String> connection;
  private Long maxRequestPerMinute;

  public RateLimiterHandlerFilterFunction(StatefulRedisModulesConnection<String, String> connection,
      Long maxRequestPerMinute) {
    this.connection = connection;
    this.maxRequestPerMinute = maxRequestPerMinute;
  }

  @Override
  public Mono<ServerResponse> filter(ServerRequest request, HandlerFunction<ServerResponse> next) {
    int currentMinute = LocalTime.now().getMinute();
    String key = String.format("rl_%s:%s", requestAddress(request.remoteAddress()), currentMinute);

    RedisGearsCommands<String, String> gears = connection.sync();

    List<Object> results = gears.trigger("RateLimiter", key, Long.toString(maxRequestPerMinute), "59");
    if (!results.isEmpty() && !Boolean.parseBoolean((String) results.get(0))) {
      return next.handle(request);
    } else {
      return ServerResponse.status(TOO_MANY_REQUESTS).build();
    }
  }

  private String requestAddress(Optional<InetSocketAddress> maybeAddress) {
    return maybeAddress.isPresent() ? maybeAddress.get().getHostName() : "";
  }
}
