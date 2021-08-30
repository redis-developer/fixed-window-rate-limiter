# Redis Fixed Window Rate Limiter

Spring Data Reactive Redis implementation of Redis ["Basic Rate Limiting"](https://redis.com/redis-best-practices/basic-rate-limiting/) Recipe which is a "Fixed Window" Rate Limiter (a single counter per unit of time)

## Implementation Details

The "Basic Rate Limiting" recipe calls for the use of a [Redis Transaction](https://redis.io/topics/transactions) in which the commands are sent to the server, accumulated in serial way and executed sequentially without any possible interruption by a request from another client.

Basically, we want the `INCR` and `EXPIRE` calls to update the requests-per-unit-of-time counter to happen atomically or not at all. A "best possible" method with a reactive API is using the `ReactiveRedisTemplate` `execute` method which takes a
`ReactiveRedisCallback` guaranteing that at least the commands will run on the same Redis
connection, but this is by no means a real "transaction".

```java
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
```

# Command Line Testing

A simple way to test an API rate limiter is using [curl](https://curl.se) in a loop,
since we are testing a set number of requests per unit of time the curl loop below will
suffice:

```
for n in {1..22}; do echo $(curl -s -w " :: HTTP %{http_code}, %{size_download} bytes, %{time_total} s" -X GET http://localhost:8080/api/ping); sleep 0.5; done
```

We loop 22 times, the example code is set to 20 so 22 will allow us to see two 429 responses. The
curl flags used are as follows; first is -s that silences curl (makes it hide progress bar and errors), -w is the write out options in which we can pass a string with interpolated variables.
Then we sleep 1/2 second between cycles.