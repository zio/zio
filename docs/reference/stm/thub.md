---
id: thub
title: "THub"
---

A `THub` is a transactional message hub. Publishers can publish messages to the hub and subscribers can subscribe to take messages from the hub.

A `THub` is an asynchronous message hub like `Hub` but it can participate in STM transactions. APIs are almost identical, but they are in the `STM` world rather than the `ZIO` world.

The fundamental operators on a `THub` are `publish` and `subscribe`:

```scala
trait THub[A] {
  def publish(a: A): USTM[Boolean]
  def subscribe: USTM[TDequeue[B]]
}
```
