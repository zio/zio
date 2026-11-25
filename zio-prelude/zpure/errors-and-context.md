# Errors and Context in ZPure

> This guide covers two of the capabilities a `ZPure` computation can have: failing with an error of type `E`, and requiring an environment of type `R`. For the other two, see [State and Logging](./state-and-logging.md).

Each of these capabilities is close to its counterpart in `ZIO`, so the operators are named the same way.

## Failing with Errors

The error type `E` is what makes a computation able to fail. We create a computation that fails with an error using `ZPure.fail`:

```scala
import zio.prelude._
import zio.prelude.fx.ZPure
```

We define a computation that fails with a `String` error:

```scala
val fail: ZPure[Nothing, Unit, Unit, Any, String, Nothing] =
  ZPure.fail("fail")
```

The error type is now `String`, so the computation can fail with a `String`. Its success type is `Nothing`, because this computation can never succeed.

When we wrap code that can throw an exception, `ZPure.attempt` catches any non-fatal exception and turns it into a failure:

```scala
def parseIntThrowable(s: String): ZPure[Nothing, Unit, Unit, Any, Throwable, Int] =
  ZPure.attempt(s.toInt)
```

`ZPure#mapError` transforms the error type. We use it to map a low-level error into the error type that the rest of our code uses:

```scala
def parseInt(s: String): ZPure[Nothing, Unit, Unit, Any, String, Int] =
  parseIntThrowable(s).mapError(_.getMessage)
```

`catchAll` recovers from any error by running another computation. Once every error is handled, the error type becomes `Nothing`:

```scala
def parseIntOrDefault(s: String): ZPure[Nothing, Unit, Unit, Any, Nothing, Int] =
  parseInt(s).catchAll(_ => ZPure.succeed(0))
```

`catchSome` recovers only from some errors, by matching them with a partial function. `fold` handles the failure case and the success case at once, returning a value for each. `orElse` runs a fallback computation only if the original one fails.

## Requiring an Environment

The environment type `R` is the capability a computation uses to read values that are supplied from outside. It works like the environment of `ZIO`, but there are two differences in practice.

First, `ZPure` usually describes one particular piece of logic in an application, not the whole application. So the environment often holds plain data, and services are less common.

Second, `ZPure` has no layers. Layers are effectful, and `ZPure` does not allow arbitrary effects, so the environment is built outside the computation.

Here, a computation for customer accounts needs an interest rate, which we model as part of an environment type:

```scala
case class AccountEnvironment(interestRate: Double)
```

`ZPure.service` accesses the whole value of a given type from the environment. The computation below does not log, use state, or fail, but it requires an `AccountEnvironment` and returns it:

```scala
val accountEnvironment: ZPure[Nothing, Unit, Unit, AccountEnvironment, Nothing, AccountEnvironment] =
  ZPure.service
```

When we only need one field, `ZPure.serviceWith` reads it directly:

```scala
val interestRate: ZPure[Nothing, Unit, Unit, AccountEnvironment, Nothing, Double] =
  ZPure.serviceWith(_.interestRate)
```

When the next step depends on the environment value and returns another computation, `ZPure.serviceWithPure` lets us return that computation:

```scala
def computeSimpleInterest(balance: Double, days: Int, interestRate: Double): ZPure[Nothing, Unit, Unit, Any, Nothing, Double] =
  ZPure.succeed(balance * days / 365 * interestRate)

def accruedInterest(balance: Double, days: Int): ZPure[Nothing, Unit, Unit, AccountEnvironment, Nothing, Double] =
  ZPure.serviceWithPure(r => computeSimpleInterest(balance, days, r.interestRate))
```

## Providing the Environment

A computation that requires an environment cannot be run until we provide one. `provideEnvironment` supplies the whole environment, which removes the environment requirement from the type:

```scala
import zio.ZEnvironment

val interestComputation: ZPure[Nothing, Unit, Unit, Any, Nothing, Double] =
  accruedInterest(100000, 30).provideEnvironment(ZEnvironment(AccountEnvironment(0.05)))
```

When the computation needs only one service, `provideService` supplies that service directly. When the environment is larger than what a particular computation needs, `provideSomeEnvironment` supplies the missing part and leaves the rest of the requirement in the type.

After providing the environment, the computation has no requirements left and can be run, as described in [Running and Converting](./running-and-converting.md):

```scala
val interestDue: Double =
  interestComputation.run
// interestDue: Double = 410.958904109589
```
