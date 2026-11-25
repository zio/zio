# EmbeddedRedis

> `EmbeddedRedis` is the entry point of the `zio-redis-embedded` module. It starts a real Redis server as a child process and exposes its address as a `RedisConfig`. Add the module as shown in [Modules](../index.md#modules). For a worked spec, see [How to Test With Embedded Redis](../how-to/test-with-embedded-redis.md).

## layer

`EmbeddedRedis.layer` has the type:

```scala
lazy val layer: ZLayer[Any, Throwable, RedisConfig]
```

Building the layer finds a free TCP port, starts a Redis server on it and produces `RedisConfig("localhost", port)`. Closing the layer's scope stops the server. Because the output is a `RedisConfig`, it feeds [`Redis.singleNode`](./redis.md#layers) and `RedisSubscription.singleNode` directly. For a worked wiring, see [How to Test With Embedded Redis](../how-to/test-with-embedded-redis.md).

The layer fails with the `Throwable` raised when the server cannot be started, for example when the Redis binary cannot be launched.

## Limits

The server is a single node, started with the default configuration of the bundled `embedded-redis` library. Its Redis version is whatever that library ships.
