---
id: io
title: "IO"
---

`IO[E, A]` is a type alias for `ZIO[Any, E, A]`, which represents an effect that has no requirements, and may fail with an `E`, or succeed with an `A`.
