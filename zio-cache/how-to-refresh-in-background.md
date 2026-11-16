# How to Refresh Values in the Background

> Recompute ZIO Cache values on a schedule with Cache#refresh so readers keep getting the old value during each reload.

Sometimes a value must stay current, but a caller should never wait for the lookup to finish. `Cache#refresh` runs the lookup and replaces the stored value when the new one is ready. While that happens, `Cache#get` keeps returning the current value. This guide runs `Cache#refresh` on a fixed schedule in a background fiber.

We start by refreshing one key every thirty seconds. The fiber is forked into a `Scope`, so it stops when the scope closes:

```scala
import zio._
import zio.cache.{Cache, Lookup}

def loadProfile(userId: String): IO[String, String] =
  ZIO.succeed(s"profile of $userId at ${java.time.Instant.now}")

val refreshed: ZIO[Scope, String, Unit] =
  for {
    cache <- Cache.make(capacity = 100, timeToLive = 5.minutes, lookup = Lookup(loadProfile))
    _     <- cache.get("alice")
    _     <- cache.refresh("alice").repeat(Schedule.spaced(30.seconds)).forkScoped
    _     <- cache.get("alice")
  } yield ()
```

Each `Cache#refresh` call runs the lookup again, even when the stored value has not expired. The old value stays in the cache, so the `Cache#get` calls in the meantime return it without waiting.

:::note
If the lookup fails during a refresh, the failure replaces the stored value and is cached like any other failure. The loop above stops at its first failure, because `Cache#refresh` returns the error. To keep the loop running, handle the error inside it, for example with `ZIO#ignore` or `ZIO#catchAll` plus a log message.
:::

Use `Cache#refresh` for values where a stale read is acceptable for a short time. For values that must never be stale, use `Cache#invalidate` followed by `Cache#get` instead.
