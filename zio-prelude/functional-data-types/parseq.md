# ParSeq

> `ParSeq` describes a collection of events that happen either in parallel or in sequence. It records the shape of the events, so we can tell which events must wait for others and which can happen at the same time.

For example, an application that fails in several ways at once can describe those failures with a `ParSeq` of errors. A batch of requests to external services can be described with a `ParSeq` where some requests run in parallel and others must run one after another.

We import `ParSeq` from the `zio.prelude` package:

```scala
import zio.prelude._
```

## Constructing Events

`ParSeq.apply` and `ParSeq.single` each create a collection that contains one event:

```scala
val fetchUser: ParSeq[Nothing, String] =
  ParSeq("fetch user")
// fetchUser: ParSeq[Nothing, String] = Single(value = "fetch user")
```

The empty collection is `ParSeq.empty`, which contains no events.

## Sequential and Parallel Events

Two operators combine collections of events. `++` makes the second collection happen after the first, and `&&` makes the two happen in parallel. We define a second event and combine it with the first using both operators:

```scala
val fetchOrders: ParSeq[Nothing, String] =
  ParSeq("fetch orders")

val sequential: ParSeq[Nothing, String] =
  fetchUser ++ fetchOrders

val parallel: ParSeq[Nothing, String] =
  fetchUser && fetchOrders
```

To see the structure of a `ParSeq`, we fold it into a string. The first function handles a single event, the second handles a sequence and the third handles events that happen in parallel:

```scala
def describe(events: ParSeq[Nothing, String]): String =
  events.fold("nothing", event => event)(
    (before, after) => s"($before then $after)",
    (left, right) => s"($left and $right)"
  )
```

The sequential and parallel versions have different shapes, and `describe` shows the difference:

```scala
describe(sequential)
// res1: String = "(fetch user then fetch orders)"

describe(parallel)
// res2: String = "(fetch user and fetch orders)"
```

## Transforming Events

`map` transforms each event and keeps the shape of the collection:

```scala
describe(sequential.map(_.toUpperCase))
// res3: String = "(FETCH USER then FETCH ORDERS)"
```

`flatMap` replaces each event with a new collection of events. The sequential and parallel structure of the original collection is kept, and each replaced event is expanded in place.

`ParSeq#zipWith` pairs each event of the first collection with each event of the second and combines every pair with a function. Here both collections contain a single event, so the result is a single combined event:

```scala
describe(fetchUser.zipWith(fetchOrders)(_ + " and " + _))
// res4: String = "fetch user and fetch orders"
```

`ParSeq#zip` is `ParSeq#zipWith` that builds a tuple from each pair.

## The Z Type Parameter

The first type parameter of `ParSeq` is `Z`. It tells the compiler whether the collection can be empty. A collection with `Z` set to `Nothing` always has at least one event, and a collection with `Z` set to `Unit` may be empty.

Some operators need the guarantee that at least one event is present. `first` returns the first event, and it is only available when `Z` is `Nothing`:

```scala
(fetchUser && fetchOrders).first
// res5: String = "fetch user"
```

`toNonEmptyMultiSet` also needs that guarantee. It converts the events into a [ZNonEmptySet](./znonemptyset.md), which counts how many times each event occurs:

```scala
(fetchUser ++ fetchUser).toNonEmptyMultiSet.toMap
// res6: Map[String, newtypes.package.Natural] = Map("fetch user" -> 2)
```
