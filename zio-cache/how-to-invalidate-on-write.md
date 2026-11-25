# How to Invalidate a Cached Value After an Update

> Call Cache#invalidate after writing to the source of truth so the next ZIO Cache get reads the new value.

A cache serves the stored value until it expires. When our application updates the data behind a key, readers would see the old value for up to the time to live. This guide removes the stale value right after the update, so the next `Cache#get` runs the lookup again.

We use a small in-memory store as the source of truth, and a cache in front of it:

```scala
import zio._
import zio.cache.{Cache, Lookup}

final class Users(store: Ref[Map[String, String]], cache: Cache[String, Nothing, String]) {
  def name(id: String): UIO[String] = cache.get(id)

  def rename(id: String, newName: String): UIO[Unit] =
    store.update(_.updated(id, newName)) *> cache.invalidate(id)
}

object Users {
  val make: UIO[Users] =
    for {
      store <- Ref.make(Map("u-1" -> "Alice"))
      cache <- Cache.make(
                 capacity = 100,
                 timeToLive = 10.minutes,
                 lookup = Lookup((id: String) => store.get.map(_.getOrElse(id, "unknown")))
               )
    } yield new Users(store, cache)
}
```

`rename` writes to the store first and invalidates the key second. If we invalidated first, a concurrent `Cache#get` could reload the old value between the two steps and keep it until the time to live expires.

```scala
val program: UIO[Unit] =
  for {
    users  <- Users.make
    before <- users.name("u-1")
    _      <- users.rename("u-1", "Alicia")
    after  <- users.name("u-1")
    _      <- ZIO.debug(s"$before -> $after")
  } yield ()
```

The program prints `Alice -> Alicia`. To drop every entry at once, for example after a bulk import, use `Cache#invalidateAll`.

:::note
Use `Cache#refresh` instead of `Cache#invalidate` when readers must never wait for the lookup. See [How to Refresh Values in the Background](./how-to-refresh-in-background.md).
:::
