# How to Test Cache Expiry Without Waiting

> Use TestClock from ZIO Test to move time forward and check that a ZIO Cache entry expires and is computed again.

A cache entry expires after its time to live, and waiting that long in a test is slow and flaky. A cache reads the time from the ZIO `Clock` service, so `TestClock` can move time forward instantly. This guide checks that an entry is computed again after it expires.

The test counts how many times the lookup runs. We use a one-minute time to live and move the `TestClock` past it:

```scala
import zio._
import zio.cache.{Cache, Lookup}
import zio.test._

object CacheExpirySpec extends ZIOSpecDefault {
  def spec =
    test("an expired entry is computed again") {
      for {
        calls <- Ref.make(0)
        cache <- Cache.make(
                   capacity = 10,
                   timeToLive = 1.minute,
                   lookup = Lookup((key: String) => calls.updateAndGet(_ + 1).as(key.length))
                 )
        _     <- cache.get("key")
        _     <- cache.get("key")
        _     <- TestClock.adjust(2.minutes)
        _     <- cache.get("key")
        count <- calls.get
      } yield assertTrue(count == 2)
    }
}
```

The first two `Cache#get` calls run the lookup once, because the second finds the stored value. After `TestClock.adjust(2.minutes)` the entry has expired, so the third call runs the lookup again and the count is `2`.

`Cache#contains` follows the same clock. After the adjustment, `Cache#contains` returns `false` for the expired key even before another `Cache#get` replaces it.

:::note
Create the cache inside the test, as above, so it reads the `TestClock` that the test provides.
:::
