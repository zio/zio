---
id: rio 
title: "RIO"
---

`RIO[R, A]` is a type alias for `ZIO[R, Throwable, A]`, which represents an effect that requires an `R`, and may fail with a `Throwable` value, or succeed with an `A`.
