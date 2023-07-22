---
id: tref
title: "TRef"
---

A `TRef[A]` is a mutable reference to an immutable value, which can participate in transactions in STM. The mutable reference can be retrieved and set from within transactions, with strong guarantees for atomicity, consistency, and isolation from other transactions.

`TRef` provides the low-level machinery to create transactions from modifications of STM memory.

## Create a TRef

Creating a `TRef` inside a transaction:

```scala
import zio._
import zio.stm._

val createTRef: STM[Nothing, TRef[Int]] = TRef.make(10)
```

Or creating a `TRef` inside a transaction, and immediately committing the transaction, which allows you to store and pass along the reference.

```scala
import zio._
import zio.stm._

val commitTRef: UIO[TRef[Int]] = TRef.makeCommit(10)
```

## Retrieve the value out of a TRef

Retrieving the value in a single transaction: 

```scala
import zio._
import zio.stm._

val retrieveSingle: UIO[Int] = (for {
  tRef <- TRef.make(10)
  value <- tRef.get
} yield value).commit
```

Or on multiple transactional statements:

```scala
import zio._
import zio.stm._

val retrieveMultiple: UIO[Int] = for {
  tRef <- TRef.makeCommit(10)
  value <- tRef.get.commit
} yield value
```

## Set a value to a TRef

Setting the value overwrites the existing content of a reference.

Setting the value in a single transaction:

```scala
import zio._
import zio.stm._

val setSingle: UIO[Int] = (for {
  tRef <- TRef.make(10)
  _ <- tRef.set(20)
  nValue <- tRef.get
} yield nValue).commit
```

Or on multiple transactions:

```scala
import zio._
import zio.stm._

val setMultiple: UIO[Int] = for {
  tRef <- TRef.makeCommit(10)
  nValue <- tRef.set(20).flatMap(_ => tRef.get).commit
} yield nValue
```

## Update the value of the TRef

The update function `A => A` allows computing a new value for the `TRef` using the old value.

Updating the value in a single transaction:

```scala
import zio._
import zio.stm._

val updateSingle: UIO[Int] = (for {
  tRef <- TRef.make(10)
  nValue <- tRef.updateAndGet(_ + 20)
} yield nValue).commit
```

Or on multiple transactions:

```scala
import zio._
import zio.stm._

val updateMultiple: UIO[Int] = for {
  tRef <- TRef.makeCommit(10)
  nValue <- tRef.updateAndGet(_ + 20).commit
} yield nValue
```

## Modify the value of the TRef

The modify function `A => (B, A): B` works similar to `update`, but allows extracting some information (the `B`) out of the update operation. 

Modify the value in a single transaction:

```scala
import zio._
import zio.stm._

val modifySingle: UIO[(String, Int)] = (for {
  tRef <- TRef.make(10)
  mValue <- tRef.modify(v => ("Zee-Oh", v + 10))
  nValue <- tRef.get
} yield (mValue, nValue)).commit
```

Or on multiple transactions:

```scala
import zio._
import zio.stm._

val modifyMultiple: UIO[(String, Int)] = for {
  tRef <- TRef.makeCommit(10)
  tuple2 <- tRef.modify(v => ("Zee-Oh", v + 10)).zip(tRef.get).commit
} yield tuple2
```

## Example usage

Here is a scenario where we use a `TRef` to hand-off a value between two `Fiber`s

```scala
import zio._
import zio.stm._

def transfer(tSender: TRef[Int],
             tReceiver: TRef[Int],
             amount: Int): UIO[Int] = {
  STM.atomically {
    for {
      _ <- tSender.get.retryUntil(_ >= amount)
      _ <- tSender.update(_ - amount)
      nAmount <- tReceiver.updateAndGet(_ + amount)
    } yield nAmount
  }
}

val transferredMoney: UIO[String] = for {
  tSender <- TRef.makeCommit(50)
  tReceiver <- TRef.makeCommit(100)
  _ <- transfer(tSender, tReceiver, 50).fork
  _ <- tSender.get.retryUntil(_ == 0).commit
  tuple2 <- tSender.get.zip(tReceiver.get).commit
  (senderBalance, receiverBalance) = tuple2
} yield s"sender: $senderBalance & receiver: $receiverBalance"
```

In this example, we create and commit two transactional references for the sender and receiver to be able to extract their value. 
On the following step, we create an atomic transactional that updates both accounts only when there is sufficient balance available in the sender account. In the end, we fork to run asynchronously.
On the running fiber, we suspend until the sender balance suffers changes, in this case, to reach `zero`. Finally, we extract the new values out of the accounts and combine them in one result. 

## ZTRef

Like `Ref[A]`, `TRef[A]` is actually a type alias for `ZTRef[+EA, +EB, -A, +B]`, a polymorphic, transactional reference and supports all the transformations that `ZRef` does. For more discussion regarding polymorphic references see the documentation on [`ZRef`](../concurrency/ref.md).