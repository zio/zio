---
id: logging-errors
title: "Don't Reflexively Log Errors"
---

In modern async concurrent applications with a lot of subsystems, if we do not type errors, we are not able to see what section of our code fails with what error. Therefore, this can be very tempting to log errors when they happen. So when we lose type-safety in the whole application it makes us be more sensitive and program defensively. Therefore, whenever we are calling an API we tend to catch its errors, log them as below:

```scala
import zio._

sealed trait UploadError extends Exception
case class FileExist(name: String)          extends UploadError
case class FileNotExist(name: String)       extends UploadError
case class StorageLimitExceeded(limit: Int) extends UploadError

/**
 * This API fail with `FileExist` failure when the provided file name exist.
 */
def upload(name: String): Task[Unit] = {
    if (...)
      ZIO.fail(FileExist(name))
    else if (...)
      ZIO.fail(StorageLimitExceeded(limit)) // This error is undocumented unintentionally
    else
      ZIO.attempt(...)
}

upload("contacts.csv").catchAll {
  case FileExist(name) => delete("contacts.csv") *> upload("contacts.csv")
  case _ =>
    for {
      _ <- ZIO.log(error.toString) // logging the error
      _ <- ZIO.fail(error) // failing again (just like rethrowing exceptions in OOP)
    } yield ()
}
```

In the above code when we see the `upload`'s return type we can't find out what types of error it may fail with. So as a programmer we need to read the API documentation, and see in what cases it may fail. Due to the fact that the documents may be outdated, and they may not provide all error cases, we tend to add another case to cover all the other errors. Expert developers may prefer to read the implementation to find out all expected errors, but it is a tedious task to do.

We don't want to lose any errors. So if we do not use typed errors, it makes us defensive to log every error, regardless of whether they will occur or not.

When we are programming with typed errors, that allows us to never lose any errors. Even if we don't handle all, the error channel of our effect type demonstrate the type of remaining errors:

```scala
val myApp: ZIO[Any, UploadError, Unit] =
  upload("contacts.csv")
    .catchSome {
      case FileExist(name) => delete(name) *> upload(name)
    }
```

It is still going to be sent an unhandled error type as a result. Therefore, there is no way to lose any errors, and they propagate automatically through all the different subsystems in our application, which means we don't have to be fearful anymore. It will be handled by higher-level code, or if it doesn't it will be passed off to something that can.

If we handle all errors using `ZIO#catchAll` the type of error channel become `Nothing` which means there is no expected error remaining to handle:

```scala
val myApp: ZIO[Any, Nothing, Unit] =
  upload("contacts.csv")
    .catchAll {
      case FileExist(name) =>
        ZIO.unit // handling FileExist error case
      case StorageLimitExceeded(limit) =>
        ZIO.unit // handling StorageLimitExceeded error case
    }
```

When we type errors, we know that they can't be lost. So typed errors give us the ability to log less.
