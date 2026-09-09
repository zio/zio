---
id: algebraic-data-types
title: "Model Domain Errors Using Algebraic Data Types"
sidebar_label: Algebraic Data Types
description: "Model domain errors using sealed traits and case classes (ADTs) for exhaustive compiler-checked error handling in ZIO applications."
keywords:
  - "algebraic data types"
  - "sealed trait"
  - "domain errors"
  - "ADT"
  - "error modeling"
  - "exhaustive matching"
---

It is best to use _algebraic data types (ADTs)_ when modeling errors within the same domain or subdomain.

Sealed traits allow us to introduce an error type as a common supertype and all errors within a domain are part of that error type by extending that:

```scala
sealed trait UserServiceError extends Exception

case class InvalidUserId(id: ID) extends UserServiceError
case class ExpiredAuth(id: ID)   extends UserServiceError
```

In this case, the super error type is `UserServiceError`. We sealed that trait, and we extend it by two cases, `InvalidUserId` and `ExpiredAuth`. Because it is sealed, if we have a reference to a `UserServiceError` we can match against it and the Scala compiler knows there are two possibilities for a `UserServiceError`:

```scala
userServiceError match {
  case InvalidUserId(id) => ???
  case ExpiredAuth(id)   => ???
}
```

This is a sum type, and also an enumeration. The Scala compiler knows only two of these `UserServiceError` exist. If we don't match on all of them, it is going to warn us. We can add the `-Xfatal-warnings` compiler option which treats warnings as errors. By turning on the fatal warning, we will have type-safety control on expected errors. So sealing these traits gives us great power.

Also extending all of our errors from a common supertype helps the ZIO's combinators like flatMap to auto widen to the most specific error type.

Let's say we have this for-comprehension here that calls the `userAuth` function, and it can fail with `ExpiredAuth`, and then we call `userProfile` that fails with `InvalidUserID`, and then we call `generateEmail` that can't fail at all, and finally we call `sendEmail` which can fail with `EmailDeliveryError`. We have got a lot of different errors here:

```scala
val myApp: IO[Exception, Receipt] =
  for {
    service <- userAuth(token)                // IO[ExpiredAuth, UserService]
    profile <- service.userProfile(userId)    // IO[InvalidUserId, Profile]
    body    <- generateEmail(orderDetails)    // IO[Nothing, String]
    receipt <- sendEmail("Your order detail",
       body, profile.email)                   // IO[EmailDeliveryError, Unit]
  } yield receipt
```

In this example, the flatMap operations auto widens the error type to the most specific error type possible. As a result, the inferred error type of this for-comprehension will be `Exception` which gives us the best information we could hope to get out of this. We have lost information about the particulars of this. We no longer know which of these error types it is. We know it is some type of `Exception` which is more information than nothing.

### Keeping Error Specificity

When every error type in a workflow extends the same sealed supertype, the for-comprehension widens to that sealed type rather than to `Exception` — and the exhaustive match on the sealed type is enforced by the compiler:

```scala mdoc:compile-only
import zio._

sealed trait AppError extends Exception
case class InvalidUserId(id: String)       extends AppError
case class ExpiredAuth(id: String)         extends AppError
case class EmailDeliveryError(msg: String) extends AppError

def userAuth(token: String): IO[ExpiredAuth, String]                  = ZIO.succeed("user-id")
def userProfile(id: String): IO[InvalidUserId, String]                = ZIO.succeed("profile")
def sendEmail(body: String, to: String): IO[EmailDeliveryError, Unit] = ZIO.unit

val myApp: IO[AppError, Unit] =
  for {
    id      <- userAuth("token")          // IO[ExpiredAuth, String]
    profile <- userProfile(id)            // IO[InvalidUserId, String]
    _       <- sendEmail("body", profile) // IO[EmailDeliveryError, Unit]
  } yield ()
```

Now `myApp` has type `IO[AppError, Unit]` instead of `IO[Exception, Unit]`. The sealed hierarchy preserves specificity: any caller that handles `AppError` must exhaustively match `InvalidUserId`, `ExpiredAuth`, and `EmailDeliveryError` — the compiler warns on any missing case (or treats it as an error with `-Xfatal-warnings`).

For Scala 3 workflows where the error types do not share a meaningful domain supertype, see [Model Errors with Union Types](union-types.md) as a lightweight alternative.
