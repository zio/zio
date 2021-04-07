---
id: with-scalaz-7x
title: "How to Interop with Scalaz 7.x?"
---

## `ZIO` Instances

If you are a happy Scalaz 7.2 user [`interop-scala7x`](https://github.com/zio/interop-scalaz) module offers `ZIO` instances for several typeclasses.

### Example

```scala
import scalaz._, Scalaz._
import zio.interop.scalaz72._

type Database = IList[User]

def findUser(id: UserId): ZIO[Database, UserError, User] = ...
def findUsers(ids: IList[UserId]): ZIO[Database, UserError, IList[User]] = ids.traverse(findUser(_))
```

## `ZIO` parallel `Applicative` instance

Due to `Applicative` and `Monad` coherence law `ZIO`'s `Applicative` instance has to be implemented in terms of `bind` hence when composing multiple effects using `Applicative` they will be sequenced. To cope with that limitation `ZIO` tagged with `Parallel` has an `Applicative` instance which is not `Monad` and operates in parallel.

### Example

```scala
import scalaz._, Scalaz._
import zio.interop.scalaz72._

case class Dashboard(details: UserDetails, history: TransactionHistory)

def getDetails(id: UserId): ZIO[Database, UserError, UserDetails] = ...
def getHistory(id: UserId): ZIO[Database, UserError, TransactionHistory] = ...

def buildDashboard(id: UserId): ZIO[Database, UserError, Dashboard] =
  Tag.unwrap(^(par(getDetails(id)), par(getHistory(id)))(Dashboard.apply))

def par[R, E, A](io: ZIO[R, E, A]): scalaz72.ParIO[R, E, A] = Tag(io)
```
