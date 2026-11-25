# State and Logging in ZPure

> This guide covers the two capabilities of `ZPure` that have no direct counterpart in `ZIO`: state, which is described by the `S1` and `S2` type parameters, and logging, which is described by the `W` type parameter. For errors and the environment, see [Errors and Context](./errors-and-context.md).

## Modeling State

In `ZIO`, state is held in a `Ref` or a `FiberRef`, so the state is not part of the computation's type. `ZPure` avoids side effects, so it does not hold state in a mutable cell. Instead, a computation receives a state of type `S1` and returns an updated state of type `S2`. The state is part of the type.

In most cases `S1` and `S2` are the same type, and the computation updates a state of that type. Sometimes it is useful to give the input and output states different types. For example, a transition from a `Closed` account to an `Open` one can be modeled with `S1` set to `Closed` and `S2` set to `Open`.

To motivate the examples, we define a state for an account and an error type for failed withdrawals:

```scala
import zio.prelude._
import zio.prelude.fx.ZPure

case class AccountState(balance: Int, open: Boolean)

sealed trait AccountError

case object InsufficientFunds extends AccountError
```

The most basic operators for state are `ZPure.get` and `ZPure.set`. `ZPure.get` returns the current state unchanged, and `ZPure.set` replaces the state with a new value. This computation withdraws money from an account:

```scala
def withdraw(amount: Int): ZPure[Nothing, AccountState, AccountState, Any, AccountError, Unit] =
  for {
    state <- ZPure.get[AccountState]
    _     <- if (amount > state.balance) ZPure.fail(InsufficientFunds)
             else ZPure.set(AccountState(state.balance - amount, state.open))
  } yield ()
```

This example uses both the state and the error type. If the account has insufficient funds, the computation fails right away. Otherwise it writes the new state.

`ZPure` computations are never concurrent, so we do not need to protect the state against conflicting updates. In `ZIO`, the same logic would have to read and write the state in one atomic `Ref#modify` step, because another fiber could change the state between the `ZIO` read and write. In `ZPure`, the `ZPure.get` and the `ZPure.set` cannot interleave, so the code is simpler.

To change the state based on its current value, `ZPure.update` takes a function from the old state to the new state. We can use it to move the balance update out of `withdraw`:

```scala
def decrementBalance(amount: Int): ZPure[Nothing, AccountState, AccountState, Any, Nothing, Unit] =
  ZPure.update(state => AccountState(state.balance - amount, state.open))
```

`ZPure.modify` is similar, but its function also returns a value to the caller. We use it when a computation needs to both update the state and produce a result from it.

To run a computation that uses state, we must provide the initial state. `provideState` supplies it, in the same way that `provideEnvironment` supplies an environment:

```scala
val withdrawalComputation: ZPure[Nothing, Any, AccountState, Any, AccountError, Unit] =
  withdraw(10).provideState(AccountState(100, true))
```

Running the computation returns the final value and state, as described in [Running and Converting](./running-and-converting.md). Here we use `ZPure.get` to read the final state and `ZPure#runEither` to run the computation, which returns either the first error or the result:

```scala
val updatedAccountState: Either[AccountError, AccountState] =
  (withdrawalComputation *> ZPure.get).runEither
// updatedAccountState: Either[AccountError, AccountState] = Right(
//   value = AccountState(balance = 90, open = true)
// )
```

## Logging

The log type `W` describes the entries a computation can add to a log while it runs. A log entry is any value of type `W`, so a `String` log is the simplest choice.

To add an entry to the log, we use `ZPure.log`, which produces a computation whose only effect is the log entry:

```scala
def withdrawLog(amount: Int): ZPure[String, AccountState, AccountState, Any, AccountError, Unit] =
  ZPure.log("Attempting to withdraw") *> withdraw(amount) <* ZPure.log(s"Withdrew $amount")
```

We can also add an entry immediately after a computation succeeds, using the `ZPure#log` operator on a computation:

```scala
def withdrawLog(amount: Int): ZPure[String, AccountState, AccountState, Any, AccountError, Unit] =
  withdraw(amount).log(s"Withdrew $amount")
```

The log is kept until the computation finishes. To read it, we run the computation with `ZPure#runAll`, which returns the log and the result. The sections on running explain the variants in more detail:

```scala
import zio.Chunk

val withdrawalComputationLog: ZPure[String, AccountState, AccountState, Any, AccountError, Unit] =
  withdrawLog(10)

val log: Chunk[String] =
  withdrawalComputationLog.runAll(AccountState(100, true))._1
```

By default, the log entries written before a failure are kept when the computation fails. `clearLogOnError` changes this: it discards the entries written by the computation when it fails. `keepLogOnError` restores the default. We can use `clearLogOnError` when a failed attempt should leave no trace in the log, such as a withdrawal that is rejected for lack of funds.
