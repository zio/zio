---
id: zio-saga
title: "ZIO Saga"
---

[ZIO Saga](https://github.com/VladKopanev/zio-saga) is a distributed transaction manager using Saga Pattern.

## Introduction

Sometimes when we are architecting the business logic using microservice architecture we need distributed transactions that are across services.

The _Saga Pattern_ lets us manage distributed transactions by sequencing local transactions with their corresponding compensating actions. A _Saga Pattern_ runs all operations. In the case of failure, it guarantees us to undo all previous works by running the compensating actions.

ZIO Saga allows us to compose our requests and compensating actions from the Saga pattern in one transaction with no boilerplate.

ZIO Saga adds a simple abstraction called `Saga` that takes the responsibility of proper composition of effects and associated compensating actions.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "com.vladkopanev" %% "zio-saga-core" % "0.4.0"
```

## Example

In the following example, all API requests have a compensating action. We compose all them together and then run the whole as one transaction:

```scala
import zio.{IO, UIO, URIO, ZIO}
def bookHotel: UIO[Unit] = IO.unit
def cancelHotel: UIO[Unit] = IO.unit

def bookTaxi: IO[String, Unit] = IO.unit
def cancelTaxi: IO[String, Unit] = IO.unit

def bookFlight: IO[String, Unit] = IO.unit
def cancelFlight: IO[String, Unit] = IO.unit
```

```scala
import com.vladkopanev.zio.saga.Saga
import zio.{IO, UIO, URIO, ZIO}

import com.vladkopanev.zio.saga.Saga._

val transaction: Saga[Any, String, Unit] =
  for {
    _ <- bookHotel compensate cancelHotel
    _ <- bookTaxi compensate cancelTaxi
    _ <- bookFlight compensate cancelFlight
  } yield ()

val myApp: ZIO[Any, String, Unit] = transaction.transact
```
