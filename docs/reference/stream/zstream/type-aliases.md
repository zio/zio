---
id: type-aliases
title: "Type Aliases"
---

The `ZStream` data type, has two type aliases:

```scala
type Stream[+E, +A] = ZStream[Any, E, A]
type UStream[+A]    = ZStream[Any, Nothing, A]
```

1. `Stream[E, A]` is a type alias for `ZStream[Any, E, A]`, which represents a ZIO stream that does not require any services, and may fail with an `E`, or produce elements with an `A`.

2. `UStream[A]` is a type alias for `ZStream[Any, Nothing, A]`, which represents a ZIO stream that does not require any services, it cannot fail, and after evaluation, it may emit zero or more values of type `A`.
