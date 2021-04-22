---
id: index
title: "Summary"
---

The following datatypes can be found in ZIO streams library:
- **[Stream](stream.md)** — A `Stream` is a lazy, concurrent, asynchronous source of values.
- **[Sink](sink.md)** — A `Sink` is a consumer of values from a `Stream`, which may produces a value when it has consumed enough.
- **[SubscriptionRef](subscriptionref.md)** - A `SubscriptionRef` contains a current value and a stream that can be consumed to observe all changes to that value.