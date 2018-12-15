---
layout: docs
section: interop
title:  "Scalaz 7.x"
---

# Scalaz 7.x

### `IO` Instances

If you are a happy Scalaz 7.2 user `interop-scala7x` module offers `IO` instances for several typeclasses, check out [the source code](shared/src/main/scala/scalaz/zio/interop/scalaz72.scala) for more details.

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
