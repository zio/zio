# How to Cache by a Part of a Large Input

> Use Cache.makeWithKey so a ZIO Cache stores only an identifier of a large request instead of the whole request.

Sometimes the input of the lookup function is large, but only a small part of it identifies the value. Storing the whole input as the cache key wastes memory and makes two requests with the same identifier look different. `Cache.makeWithKey` converts each input into the key that the cache stores. This guide caches the result of processing a request by the request's `id`.

We start with a request type that carries a large payload, and a lookup function that processes it:

```scala
import zio._
import zio.cache.{Cache, Lookup}

final case class Request(id: String, payload: Array[Byte])

def process(request: Request): UIO[Int] =
  ZIO.succeed(request.payload.length)
```

Then we create the cache with `Cache.makeWithKey`. Its `keyBy` function maps a `Request` to the `String` that the cache stores:

```scala
val requests: UIO[Cache[Request, Nothing, Int]] =
  Cache.makeWithKey(capacity = 100, lookup = Lookup(process))(
    timeToLive = _ => 1.minute,
    keyBy = (request: Request) => request.id
  )
```

The cache is still used with the full `Request`. Two requests with the same `id` share one cached value, even when their payloads differ:

```scala
val program: UIO[Unit] =
  for {
    cache <- requests
    first <- cache.get(Request("r-1", Array[Byte](1, 2, 3)))
    other <- cache.get(Request("r-1", Array[Byte](9)))
    _     <- ZIO.debug(s"first=$first other=$other")
  } yield ()
```

Both results are `3`, because the second call finds the value stored under the key `"r-1"`. The lookup function runs only for the first call and still receives the full `Request`.

:::note
Choose a key that identifies the value completely. If two inputs with the same key should produce different values, they will share one entry and the second caller gets the first caller's value.
:::

`ScopedCache` has no `makeWithKey`. To cache resources by part of an input, wrap the input in a small key type and look up by that key instead.
