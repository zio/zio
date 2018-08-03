ZIO Interop module
==================

This module provides the following integrations:

- `IO` conversions to and from stdlib's `Future`
- `IO` instances for Scalaz 7.2.x typeclasses
- `IO` instance for cats' `Effect` typeclass as required by Typelevel libs, such as `fs2`, `doobie` and `http4s`

## Stdlib `Future`

### From `Future`

This is the extension method added to `IO` companion object:

```scala
def fromFuture[A](ftr: () => Future[A])(ec: ExecutionContext): IO[Throwable, A] =
```

There are a few things to clarify here:

- `ftr`, the expression producing the `Future` value, is a *thunk* (or `Function0`). The reason for that is, `Future` is eager, it means as soon as you call `Future.apply` the effect has started performing, that's not a desired behavior in Pure FP (which ZIO encourages). So it's recommended to declare expressions creating `Future`s using `def` instead of `val`.
- Also you have to be explicit on which EC you want to use, having it as implicit in stdlib is a bad practice we don't want to cargo cult.
- Finally, as you can see, the `IO` returned fixes the error type to `Throwable` since that's the only possible cause for a failed `Future`.

#### Example

```scala
// EC is not implicit
val myEC: ExecutionContext = ...

// future defined in thunk using def
def myFuture: Future[ALotOfData] = myLegacyHeavyJobReturningFuture(...)
val myIO: IO[Throwable, ALotOfData] = IO.fromFuture(myFuture _)(myEC)
```

### To `Future`

This extension method is added to values of type `IO[Throwable, A]`:

```scala
def toFuture[E]: IO[E, Future[A]] =
```

Notice that we don't actually return a `Future` but an infallible `IO` producing the `Future` when it's performed, that's again because as soon as we have a `Future` in our hands, whatever it does is already happening.

As an alternative, a more flexible extension method is added to any `IO[E, A]` to convert to `Future` as long as you can provide a function to convert from `E` to `Throwable`.

```scala
def toFutureE[E2](f: E => Throwable): IO[E2, Future[A]] = io.leftMap(f).toFuture
```

#### Example

```scala
val safeFuture: IO[Nothing, Future[MoarData]] = myShinyNewApiBasedOnZio(...).toFuture(MyError.toThrowable)
val itsHappening: Future[MoarData] = unsafeRun(safeFuture)
```

## Scalaz 7.2

### `IO` Instances

If you are a happy Scalaz 7.2 user `interop` module offers `IO` instances for several typeclasses, check out [the source code](shared/src/main/scala/scalaz/zio/interop/scalaz72.scala) for more details.

#### Example

```scala
import scalaz._, Scalaz._
import scalaz.zio.interop.scalaz72._

def findUser(id: UserId): IO[UserError, User] = ...
def findUsers(ids: IList[UserId]): IO[UserError, IList[User]] = ids.traverse(findUser)
```

### `IO` parallel `Applicative` instance

Due to `Applicative` and `Monad` coherence law `IO`'s `Applicative` instance has to be implemented in terms of `bind` hence when composing multiple effects using `Applicative` they will be sequenced. To cope with that limitation `IO` tagged with `Parallel` has an `Applicative` instance which is not `Monad` and operates in parallel.

#### Example

```scala
import scalaz._, Scalaz._, Tags.Parallel
import scalaz.zio.interop.scalaz72._

case class Dashboard(details: UserDetails, history: TransactionHistory)

def getDetails(id: UserId): IO[UserError, UserDetails] = ...
def getHistory(id: UserId): IO[UserError, TransactionHistory] = ...

def buildDashboard(id: UserId): IO[UserError, Dashboard] =
  Tag.unwrap(^(par(getDetails(id)), par(getHistory(id)))(Dashboard.apply))

def par[E, A](io: IO[E, A]): IO[E, A] @@ Parallel = Tag(io)
```

