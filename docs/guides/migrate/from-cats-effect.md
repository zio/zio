---
id: from-cats-effect
title: "How to Migrate from Cats Effect to ZIO?"
sidebar_label: "Migration from Cats Effect"
---

Cats `IO[A]` can be easily replaced with ZIO's `Task[A]` (an alias for `ZIO[Any, Throwable, A]`).
Translation should be relatively straightforward. Below, you'll find tables showing the ZIO equivalents of
various `cats.*`'s methods.

### Methods on cats.FlatMap.Ops

| cats           | ZIO        |
|----------------|------------|
| `flatMap`      | `flatMap`  |
| `flatten`      | `flatten`  |
| `productREval` | `zipRight` |
| `productLEval` | `zipLeft`  |
| `mproduct`     | `zipPar`   |
| `flatTap`      | `tap`      |
