---
id: zthub
title: "ZTHub"
---

A `ZTHub[RA, RB, EA, EB, A, B]` is a transactional message hub. Publishers can publish messages of type `A` to the hub and subscribers can subscribe to take messages of type `B` from the hub. Publishing messages can require an environment of type `RA` and fail with an error of type `EA`. Taking messages can require an environment of type `RB` and fail with an error of
type `EB`.

A `ZTHub` is an asynchronous message hub like `ZHub` but it can participate in STM transactions. APIs are almost identical, but they are in the `STM` world rather than the `ZIO` world.

The fundamental operators on a `ZTHub` are `publish` and `subscribe`:

```scala mdoc
import zio._

trait ZTHub[-RA, -RB, +EA, +EB, -A, +B] {
  def publish(a: A): ZSTM[RA, EA, Boolean]
  def subscribe: USTM[ZTDequeue[RB, EB, B]]
}
```
