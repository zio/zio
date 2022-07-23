---
id: index
title: "Core Data Types"
---

In this section we are going to talk about the basic data types that are required to build a ZIO application:

- **[ZIO](zio/zio.md)** — `ZIO` is a value that models an effectful program, which might fail or succeed.
    + **[UIO](zio/uio.md)** — `UIO[A]` is a type alias for `ZIO[Any, Nothing, A]`.
    + **[URIO](zio/urio.md)** — `URIO[R, A]` is a type alias for `ZIO[R, Nothing, A]`.
    + **[Task](zio/task.md)** — `Task[A]` is a type alias for `ZIO[Any, Throwable, A]`.
    + **[RIO](zio/rio.md)** — `RIO[R, A]` is a type alias for `ZIO[R, Throwable, A]`.
    + **[IO](zio/io.md)** — `IO[E, A]` is a type alias for `ZIO[Any, E, A]`.
- **[ZIOApp](zioapp.md)** — `ZIOApp` and the `ZIOAppDefault` are entry points for ZIO applications.
- **[Runtime](runtime.md)** — `Runtime[R]` is capable of executing tasks within an environment `R`.
- **[Exit](exit.md)** — `Exit[E, A]` describes the result of executing an `IO` value.
- **[Cause](cause.md)** — `Cause[E]` is a description of a full story of a fiber failure.
