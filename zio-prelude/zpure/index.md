# ZPure

> A `ZPure[W, S1, S2, R, E, A]` is a description of a computation that requires an environment `R` and an initial state `S1` and either fails with an error of type `E` or succeeds with an updated state of type `S2` and a value of type `A`, in either case also producing a log of type `W`.

A `ZPure[W, S1, S2, R, E, A]` is a description of a computation that requires an environment `R` and an initial state `S1` and either fails with an error of type `E` or succeeds with an updated state of type `S2` and a value of type `A`, in either case also producing a log of type `W`.

Conceptually, we can think of `ZPure` as just a function, though `ZPure` is not actually implemented this way for reasons of efficiency and stack safety.

```scala

case class ZPure[+W, -S1, +S2, -R, +E, +A](run: (R, S1) => (Chunk[W], Either[E, (S2, A)]))
```

The `ZPure` data type models four "capabilities" that a computation can have in addition to just producing a value of type `A`:

- **Errors** - A `ZPure` computation can fail with an error of type `E` similar to an `Either`.
- **Context** - A `ZPure` computation can require some environment of type `R` similar to a `Reader` data type.
- **State** - A `ZPure` computation can update a state `S1` to a new state `S2` similar to a `State` data type.
- **Logging** - A `ZPure` computation can maintain a log of type `W` similar to a `Writer` data type.

Previously, each of these capabilities required a separate data type, such as `Either` for modeling errors.

This was fine when we just needed one of these capabilities but it broke down when we wanted to describe computations that used more than one capability. How were we supposed to describe, for example, a computation that updated state and could also fail?

The answer was to use other data types called _monad transformers_ which "stacked" these capabilities on top of existing data types. So to get state and errors we would stack an `EitherT` monad transformer on top of a `StateT` monad transformer.

This approach has a number of problems.

First, it has terrible performance. Each of these monad transformers is its own data type so evaluating a single step in a monad transformer stack requires digging into multiple layers of data structures just to access a single value, doing something with it, and then rebuilding all the layers of that nested data structure all over again.

Second, monad transformers have very poor ergonomics. They are generally invariant so they have bad type inference and working with these nested structures creates many opportunities for errors, such as constructing the "stack" in the wrong order, that reduce developer productivity and make it harder to onboard new developers.

Third, monad transformer stacks are actually not powerful enough to model what they are trying to describe. Each part of the "stack" only knows about itself and not the other parts of the stack so it is impossible to implement operators that require multiple capabilities.

`ZPure` addresses this problem by including all of these capabilities in one data type using the same technique of "effect rotation" pioneered in ZIO. This results in dramatically higher performance, improved ergonomics, and a much more powerful API.

## Basic Operations

The `ZPure` data type exposes a very similar API to the `ZIO` data type, excluding operators that deal directly with IO or concurrency. So if you know how to use `ZIO` you should be very comfortable working with `ZPure` as well.

Just like `ZIO`, `ZPure` is a description of a computation so the computation will not actually be executed until we run it.

If we are not embedding side effecting code in `ZPure` this laziness will not be observable in the result of a `ZPure` computation but it can be important to keep in mind for performance. For example, if we are doing some expensive but not side effecting computation in the context of `ZPure` and using the result multiple times we want to only run the `ZPure` computation once instead of multiple times.

The operators that execute a `ZPure` computation are all variants of `run` to emphasize that when we call them we are actually running the computation described by the `ZPure` value.

Also like `ZIO`, `ZPure` is stack safe, so we can write recursive programs in terms of `ZPure` without worrying about stack overflow errors.

The main difference between `ZIO` and `ZPure` is that we do not want to embed arbitrary side effecting code in `ZPure`. `ZPure` supports a specific set of capabilities but if we have arbitrary effects we are better off using `ZIO`, which has the power to manage these effects and also more clearly signals our intent.

We can create a computation that succeeds with an existing value using the `succeed` operator.

```scala

val one: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] =
  ZPure.succeed(1)
// one: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] = Succeed(value = 1)
```

Note that the log type `W` here is nothing, indicating that we are not logging anything right now. The `S1`and `S2` types are `Unit` because we are not using `State` in this computation.

The environment type is `Any`, indicating that this computation does not require any environment to be run. And the error type is `Nothing` because this computation cannot fail.

If we find ourselves using the same type parameters over and over it can be helpful to define a type alias to avoid repeating ourselves. There are also several useful type aliases for `ZPure` defined directly in ZIO Prelude.

```scala
type State[S, +A] = zio.prelude.fx.ZPure[Nothing, S, S, Any, Nothing, A]
type Reader[-R, +A] = zio.prelude.fx.ZPure[Nothing, Unit, Unit, R, Nothing, A]
type Writer[+W, +A] = zio.prelude.fx.ZPure[W, Unit, Unit, Any, Nothing, A]
```

The `State` type alias specializes the environment type to `Any` and the error and log types to `Nothing` and just has a single state type 'A'. It is analogous to a function `S => (S, A)`.

The `Reader` type alias has an environment type but specializes the log and error types to `Nothing` and the state types to `Unit`. It is analogous to a function `R => A`.

The `Writer` type alias has a log type but specializes the environment type to `Any`, the error type to `Nothing`, and the state types to `Unit`. It is analogous to a function `() => (W, A)`.

There are also versions of each of these type aliases that support failure, `EState`, `EReader`, and `EWriter`. Using these type aliases can simplify your type signatures and lead to better type inference in some cases. So if you are really only working with one or two of the capabilities of `ZPure` it is definitely worth using these type aliases.

However, for now we will continue to use the full `ZPure` type signature.

We can transform the successful value of a computation using the `map` operator.

```scala
val two: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] =
  one.map(_ + 1)
// two: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] = FlatMap(
//   value = Succeed(value = 1),
//   continue = zio.prelude.fx.ZPure$$Lambda$17353/0x0000000804bf5840@2e4ad19b
// )
```

We can combine the values of two computations with the `zipWith` operator.

```scala
val three: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] =
  one.zipWith(two)(_ + _)
// three: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] = FlatMap(
//   value = Succeed(value = 1),
//   continue = zio.prelude.fx.ZPure$$Lambda$17361/0x0000000804c08840@1fae5a1c
// )
```

We can also chain computations using the `flatMap` operator and Scala's for comprehension syntax.

```scala
val six: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] =
  for {
    x <- one
    y <- two
    z <- three
  } yield x + y + z
// six: ZPure[Nothing, Unit, Unit, Any, Nothing, Int] = FlatMap(
//   value = Succeed(value = 1),
//   continue = <function1>
// )
```

The easiest way to run a computation is to use the `run` method. It requires us to have already provided any required environment and initial state and handled our errors and just produces the final value.

```scala
val value: Int =
  three.run
// value: Int = 3
```

## Working With Errors

Since `ZPure` also has an error type the operators for working with errors are quite similar to the ones on `ZIO`.

We can create a computation that fails with an error using the `fail` operator.

```scala
val fail: ZPure[Nothing, Unit, Unit, Any, String, Nothing] =
  ZPure.fail("fail")
// fail: ZPure[Nothing, Unit, Unit, Any, String, Nothing] = Fail(
//   error = "fail"
// )
```

Notice how now the error type is `String`, indicating that this computation can fail with a `String` error. In this case the success type is `Nothing` since we actually know that this computation can never succeed.

If we are working with code that can throw exceptions we can use the `attempt` operator of `ZPure` to safely import that code into our computation, catching any non-fatal exceptions and translating them into failure values.

```scala
def parseIntThrowable(s: String): ZPure[Nothing, Unit, Unit, Any, Throwable, Int] =
  ZPure.attempt(s.toInt)
```

We can use the `mapError` operator to transform the error type of a computation, for example to map it to a common error type we are using in other parts of our computation.

```scala
def parseInt(s: String): ZPure[Nothing, Unit, Unit, Any, String, Int] =
  parseIntThrowable(s).mapError(_.getMessage)
```

We can recover from errors by using the `catchAll` operator, which allows us to recover from the failure using a new computation.

```scala
def parseIntOrDefault(s: String): ZPure[Nothing, Unit, Unit, Any, Nothing, Int] =
  parseInt(s).catchAll(_ => ZPure.succeed(0))
```

Now the error type is `Nothing`, indicating that we have handled all our errors.

There are many other error handling operators defined on `ZPure`. The `catchSome` operator allows us to only recover from certain errors.

The `fold` and `foldM` operators allow us to handle both the failure and success cases at the same time. And the `orElse` operator allows us to specify a fallback computation that will be run if the original computation fails.

## Working With Context

The environment type `R` is also analogous to the environment type of ZIO.

The main difference is that we tend to use `ZPure` to describe a particular computation in our application rather than as the basis for our entire application architecture. As a result, services tend to be less commonly used in the environment and often the environment just consists of some data.

We also do not use layers to construct the environment. Layers are inherently effectual and we do not want to perform arbitrary effects as part of a `ZPure` computation.

As an example of how we might use the environment type in a `ZPure` computation, we can imagine that our computation describes some logic for working with customer accounts. To perform its logic our computation needs access to various values such as the interest rate to use.

```scala
case class AccountEnvironment(interestRate: Double)
```

We can work with the environment using the same operators we use for `ZIO`, with the caveat described above that we use the more generic environment operators instead of the ones specialized for the module pattern.

We access a service in the environment using the `service` operator, so if we wanted to access the `AccountEnvironment` service we could do it like this:

```scala
val accountEnvironment: ZPure[Nothing, Unit, Unit, AccountEnvironment, Nothing, AccountEnvironment] =
  ZPure.service
// accountEnvironment: ZPure[Nothing, Unit, Unit, AccountEnvironment, Nothing, AccountEnvironment] = Environment(
//   access = zio.prelude.fx.ZPure$ServiceWithPartiallyApplied$$$Lambda$17538/0x0000000804cfc840@27dae542
// )
```

This computation does not use logging or state and cannot fail, but it now depends on an `AccountEnvironment` and just returns the `AccountEnvironment`. Since this computation now succeeds with an `AccountEnvironment` we can use all of our normal operators for transforming success values like `map` and `flatMap` to work with it.

If we just want to do one thing with the environment like get the interest rate we can do this slightly more concisely with the `serviceWith` operator.

```scala
val interestRate: ZPure[Nothing, Unit, Unit, AccountEnvironment, Nothing, Double] =
  ZPure.serviceWith(_.interestRate)
// interestRate: ZPure[Nothing, Unit, Unit, AccountEnvironment, Nothing, Double] = Environment(
//   access = zio.prelude.fx.ZPure$ServiceWithPartiallyApplied$$$Lambda$17538/0x0000000804cfc840@5283d5ad
// )
```

There is also a `serviceWithPure` variant for when we want to perform another computation based on the value from the environment.

```scala
def computeSimpleInterest(balance: Double, days: Int, interestRate: Double): ZPure[Nothing, Unit, Unit, Any, Nothing, Double] =
  ZPure.succeed(balance * days / 365 * interestRate)

def accruedInterest(balance: Double, days: Int): ZPure[Nothing, Unit, Unit, AccountEnvironment, Nothing, Double] =
  ZPure.serviceWithPure(r => computeSimpleInterest(balance, days, r.interestRate))
```

To run a computation we need to provide it with its required environment, which we can do with the `provide` operator.

```scala

val interestComputation: ZPure[Nothing, Unit, Unit, Any, Nothing, Double] =
  accruedInterest(100000, 30).provideEnvironment(ZEnvironment(AccountEnvironment(0.05)))
// interestComputation: ZPure[Nothing, Unit, Unit, Any, Nothing, Double] = Provide(
//   r = ZEnvironment(MdocSession::MdocApp0::AccountEnvironment -> AccountEnvironment(0.05)),
//   continue = FlatMap(
//     value = Environment(
//       access = zio.prelude.fx.ZPure$ServiceWithPurePartiallyApplied$$$Lambda$17539/0x0000000804cff840@50dcdff4
//     ),
//     continue = generalized constraint
//   )
// )
```

Once we have provided our application with our required environment we are ready ro run it.

```scala
val interestDue: Double =
  interestComputation.run
// interestDue: Double = 410.958904109589
```

## Working With State

The state type described by `S1` and `S2` is the first capability provided by `ZPure` that is not directly analogous to `ZIO`.

In `ZIO` we manage state using the `Ref` and `FiberRef` data types so we do not need these additional type parameters. In contrast, in `ZPure` we want to avoid arbitrary side effects so we instead model state as a function that takes the old state and returns the new state using the `S1` and `S2` type parameters.

In practice in most cases `S1` and `S2` will be the same and we will be describing a computation that takes some state and just returns an updated value of the same state type. However, in some cases it can be useful to model the initial and updated state separately, for example to describe a transition from a `Closed` to `Open` state in a type safe way.

To motivate our example here let's define an `AccountState` type.

```scala
case class AccountState(balance: Int, open: Boolean)
```

We will also define a domain specific error type which we will use.

```scala
sealed trait AccountError

case object InsufficientFunds extends AccountError
```

The most basic operators for working with state are `get` and `set`. The `get` operator retrieves the initial state and the `set` operator sets the updated state to the specified value.

Let's use these operators to describe a computation that withdraws funds from the account.

```scala
def withdraw(amount: Int): ZPure[Nothing, AccountState, AccountState, Any, AccountError, Unit] =
  for {
    state <- ZPure.get[AccountState]
    _     <- if (amount > state.balance) ZPure.fail(InsufficientFunds)
             else ZPure.set(AccountState(state.balance - amount, state.open))
  } yield ()
```

This example combines a couple of the features we have talked about so far.

First, we are using a for comprehension to chain together multiple computations

Second, we are using both the error type and the state type here. If the customer has insufficient funds we fail immediately, otherwise we update the state.

One important thing to note here is that since `ZPure` computations are never concurrent we don't need to worry about conflicting modifications to the state.

For example, in the context of `ZIO` code analogous to the above would not be safe because the `get` and `set` would not be performed atomically so we would need to do the entire transaction in a single `modify` operation, returning a value indicating whether there were sufficient funds. In `ZPure` we don't have to do any of that so our code can be simpler.

There are also `update` and `modify` operators on `ZPure`. So for example we could factor out the logic of updating the account balance in the `withdraw` method like this:

```scala
def decrementBalance(amount: Int): ZPure[Nothing, AccountState, AccountState, Any, Nothing, Unit] =
  ZPure.update(state => AccountState(state.balance - amount, state.open))
```

If we are working with state then to actually run our computation we need to provide it with an initial state. The easiest way to do this is with the `provideState` operator, analogous to how we provide the environment.

```scala
val withdrawalComputation: ZPure[Nothing, Any, AccountState, Any, AccountError, Unit] =
  withdraw(10).provideState(AccountState(100, true))
// withdrawalComputation: ZPure[Nothing, Any, AccountState, Any, AccountError, Unit] = FlatMap(
//   value = Modify(
//     run0 = zio.prelude.fx.ZPure$$$Lambda$17546/0x0000000804d11840@16b4a018
//   ),
//   continue = zio.prelude.fx.ZPure$$Lambda$17547/0x0000000804d12840@2e693c8b
// )
```

We will use the `runEither` operator, which is a variant of `run` that allows the computation to fail and just converts the failure to the left side of an `Either`. The `run` and `runEither` operators just return the final value, so let's use the `get` operator to get the final state before we run our computation since we only care about the final state.

```scala moc
val updatedAccountState: Either[AccountError, AccountState] =
  (withdrawalComputation *> ZPure.get).runEither
```

## Working With Logging

The final capability provided by `ZPure` is logging.

We can add to the log with the `log` operator on the `ZPure` companion object.

```scala
def withdrawLog(amount: Int): ZPure[String, AccountState, AccountState, Any, AccountError, Unit] =
  ZPure.log("Attempting to withdraw") *> withdraw(amount) <* ZPure.log(s"Withdrew $amount")
```

We can also use the `log` operator on the `ZPure` trait to log something immediately after a computation has successfully completed.

```scala
def withdrawLog(amount: Int): ZPure[String, AccountState, AccountState, Any, AccountError, Unit] =
  withdraw(amount).log(s"Withdrew $amount")
```

The log is maintained until our computation is done.

This time we will use the `runAll` operator to run our computation. `runAll` is the most general way of running a `ZPure` computation and returns the log as well as either the failure or the success.

```scala

val withdrawalComputationLog: ZPure[String, AccountState, AccountState, Any, AccountError, Unit] =
  withdrawLog(10)
// withdrawalComputationLog: ZPure[String, AccountState, AccountState, Any, AccountError, Unit] = FlatMap(
//   value = FlatMap(
//     value = Modify(
//       run0 = zio.prelude.fx.ZPure$$$Lambda$17545/0x0000000804d11040@43038722
//     ),
//     continue = <function1>
//   ),
//   continue = zio.prelude.fx.ZPure$$Lambda$17548/0x0000000804d13040@70044b3a
// )

val log: Chunk[String] =
  withdrawalComputationLog.runAll(AccountState(100, true))._1
// log: Chunk[String] = IndexedSeq("Withdrew 10")
```
