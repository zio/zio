---
id: index
title: "Summary"
---

 - **[ZIO](zio.md)** — A `ZIO` is a value that models an effectful program, which might fail or succeed.
   + **[UIO](uio.md)** — An `UIO[A]` is a type alias for `ZIO[Any, Nothing, A]`.
   + **[URIO](urio.md)** — An `URIO[R, A]` is a type alias for `ZIO[R, Nothing, A]`.
   + **[Task](task.md)** — A `Task[A]` is a type alias for `ZIO[Any, Throwable, A]`.
   + **[RIO](rio.md)** — A `RIO[R, A]` is a type alias for `ZIO[R, Throwable, A]`.
   + **[IO](io.md)** — An `IO[E, A]` is a type alias for `ZIO[Any, E, A]`.
- **[Exit](exit.md)** — An `Exit[E, A]` describes the result of executing an `IO` value.
- **[Cause](cause.md)** - `Cause[E]` is a description of a full story of a fiber failure. 
- **[Runtime](runtime.md)** — A `Runtime[R]` is capable of executing tasks within an environment `R`.
