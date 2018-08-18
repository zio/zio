---
layout: docs
position: 2
section: usage
title:  "Usage"
---

# Usage

A value of type `IO[E, A]` describes an effect that may fail with an `E`, run forever, or produce a single `A`.

`IO` values are immutable, and all `IO` functions produce new `IO` values, enabling `IO` to be reasoned about and used like any ordinary Scala immutable data structure.

`IO` values do not actually _do_ anything. However, they may be interpreted by the `IO` runtime system into effectful interactions with the external world. Ideally, this occurs at a single time, in your application's `main` function (`App` provides this functionality automatically).
