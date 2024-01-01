---
id: stm
title: "STM"
---

An `STM[E, A]` represents an effect that can be performed transactionally resulting in a failure `E` or a success `A`. There is a more powerful variant `ZSTM[R, E, A]` which supports an environment type `R` like `ZIO[R, E, A]`.

The `STM` (and `ZSTM` variant) data-type is _not_ as powerful as the `ZIO[R, E, A]` datatype as it does not allow you to perform arbitrary effects. These are because actions inside STM actions can be executed an arbitrary amount of times (and rolled-back as well). Only STM actions and pure computation may be performed inside a memory transaction. 

No STM actions can be performed outside a transaction, so you cannot accidentally read or write a transactional data structure outside the protection of `STM.atomically` (or without explicitly `commit`ting the transaction). For example:

```scala
import zio._
import zio.stm._

def transferMoney(from: TRef[Long], to: TRef[Long], amount: Long): STM[String, Long] =
  for {
    senderBal <- from.get
    _         <- if (senderBal < amount) STM.fail("Not enough money")
                 else STM.unit
    _         <- from.update(existing => existing - amount)
    _         <- to.update(existing => existing + amount)
    recvBal   <- to.get
  } yield recvBal

val program: IO[String, Long] = for {
  sndAcc  <- STM.atomically(TRef.make(1000L))
  rcvAcc  <- STM.atomically(TRef.make(0L))
  recvAmt <- STM.atomically(transferMoney(sndAcc, rcvAcc, 500L))
} yield recvAmt
```

`transferMoney` describes an atomic transfer process between a sender and a receiver. The transaction will fail if the sender does not have enough of money in their account. This means that individual accounts will be debited and credited atomically. If the transaction fails in the middle, the entire process will be rolled back, and it will appear that  nothing has happened.

Here, we see that `STM` effects compose using a for-comprehension and that wrapping an `STM` effect with `STM.atomically` (or calling `commit` on any STM effect) turns the `STM` effect into a `ZIO` effect which can be executed. 

STM transactions compose sequentially. By using `STM.atomically` (or `commit`), the programmer identifies atomic transaction in the sense that the entire set of operations within `STM.atomically` appears to take place indivisibly.

## Errors

`STM` supports errors just like `ZIO` via the error channel. In `transferMoney`, we saw an example of an error (`STM.fail`). 

Errors in `STM` have abort semantics: if an atomic transaction encounters an error, the transaction is rolled back with no effect.

## `retry`

`STM.retry` is central to making transactions composable when they may block. For example, if we wanted to ensure that the money transfer took place when the sender had enough of money (instead of failing right away), we can use `STM.retry` instead:

```scala
def transferMoneyNoMatterWhat(from: TRef[Long], to: TRef[Long], amount: Long): STM[String, Long] =
  for {
    senderBal <- from.get
    _         <- if (senderBal < amount) STM.retry else STM.unit
    _         <- from.update(existing => existing - amount)
    _         <- to.update(existing => existing + amount)
    recvBal   <- to.get
  } yield recvBal
```

`STM.retry` will abort and retry the entire transaction until it succeeds (instead of failing like the previous example).

Note that the transaction will only be retried when one of the underlying transactional data structures have been changed.

There are many other variants of the `STM.retry` combinator like `STM.check` so rather than writing `if (senderBal < amount) STM.retry else STM.unit`, you can replace it with `STM.check(senderBal < amount)`.

## Composing alternatives

STM transactions compose sequentially so that both STM effects are executed. However, STM transactions can also compose transactions as alternatives so that only one STM effect is executed by making use of `orTry` on STM effects. 

Provided we have two STM effects `sA` and `sB`, you can express that you would like to compose the two using `sA orTry sB`. The transaction would first attempt to run `sA` and if it retries then `sA` is abandoned with no effect and then `sB` runs. Now if `sB` also retries then the entire call retries. However, it waits for the transactional data structures to change that are involved in either `sA` or `sB`. 

Using `orTry` is an elegant technique that can be used to determine whether or not an STM transaction needs to block. For example, we can take `transferMoneyNoMatterWhat` and turn it into an STM transaction that will fail immediately if the sender does not have enough of money instead of retrying by doing:

```scala
def transferMoneyFailFast(from: TRef[Long], to: TRef[Long], amount: Long): STM[String, Long] =
    transferMoneyNoMatterWhat(from, to, amount) orTry STM.fail("Sender does not have enough of money")
```

This will cause the transfer to fail immediately if the sender does not have money because of the semantics of `orTry`.
