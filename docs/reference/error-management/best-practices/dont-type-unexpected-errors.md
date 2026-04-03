---
id: unexpected-errors
title: "Don't Type Unexpected Errors"
---

When we first discover typed errors, it may be tempting to put every error into the error type parameter. That is a mistake because we can't recover from all types of errors. When we encounter unexpected errors we can't do anything in those cases. We should let the application die. Let it crash is the erlang philosophy. It is a good philosophy for all unexpected errors. At best, we can sandbox it, but we should let it crash.

The context of a domain determines whether an error is expected or unexpected. When using typed errors, sometimes it is necessary to make a typed-error un-typed because in that case, we can't handle the error, and we should let the application crash.

For example, in the following example, we don't want to handle the `IOException` so we can call `ZIO#orDie` to make the effect's failure unchecked. This will translate effect's failure to the death of the fiber running it:

```scala mdoc:compile-only
import zio._

Console.printLine("Hello, World") // ZIO[Any, IOException, Unit]
  .orDie                          // ZIO[Any, Nothing, Unit]
```

If we have an effect that fails for some `Throwable` we can pick certain recoverable errors out of that, and then we can just let the rest of them kill the fiber that is running that effect. The ZIO effect has a method called `ZIO#refineOrDie` that allows us to do that.

In the following example, calling `ZIO#refineOrDie` on an effect that has an error type `Throwable` allows us to refine it to have an error type of `TemporaryUnavailable`:

```scala mdoc:invisible
import java.net.URL
trait TemporaryUnavailable extends Throwable

trait Response

object httpClient {
  def fetchUrl(url: URL): Response = ???
}

val url = new URL("https://zio.dev")
```

```scala mdoc:compile-only
import zio._

val response: ZIO[Any, Nothing, Response] =
  ZIO
    .attemptBlocking(
      httpClient.fetchUrl(url)
    ) // ZIO[Any, Throwable, Response]
    .refineOrDie[TemporaryUnavailable] {
      case e: TemporaryUnavailable => e
    } // ZIO[Any, TemporaryUnavailable, Response]
    .retry(
      Schedule.fibonacci(1.second)
    ) // ZIO[Any, TemporaryUnavailable, Response]
    .orDie // ZIO[Any, Nothing, Response]
```

In this example, we are importing the `fetchUrl` which is a blocking operation into a `ZIO` value. We know that in case of a service outage it will throw the `TemporaryUnavailable` exception. This is an expected error, so we want that to be typed. We are going to reflect that in the error type. We only expect it, so we know how to recover from it.

Also, this operation may throw unexpected errors like `OutOfMemoryError`, `StackOverflowError`, and so forth. Therefore, we don't include these errors since we won't be handling them at runtime. They are defects, and in case of unexpected errors, we should let the application crash.

Therefore, it is quite common to import a code that may throw exceptions, whether that uses expected errors for error handling or can fail for a wide variety of unexpected errors like disk unavailable, service unavailable, and so on. Generally, importing these operations end up represented as a `Task` (`ZIO[Any, Throwable, A]`). So in order to make recoverable errors typed, we use the `ZIO#refineOrDie` method.
