---
id: tarray
title: "TArray"
---

`TArray` is an array of mutable references that can participate in transactions in STM.

## Create a TArray

Creating an empty `TArray`:

```scala
import zio._
import zio.stm._

val emptyTArray: STM[Nothing, TArray[Int]] = TArray.empty[Int]
```

Or creating a `TArray` with specified values:

```scala
import zio._
import zio.stm._

val specifiedValuesTArray: STM[Nothing, TArray[Int]] = TArray.make(1, 2, 3)
```

Alternatively, you can create a `TArray` by providing a collection of values:

```scala
import zio._
import zio.stm._

val iterableTArray: STM[Nothing, TArray[Int]] = TArray.fromIterable(List(1, 2, 3))
```

## Retrieve the value from a TArray

The n-th element of the array can be obtained as follows:

```scala
import zio._
import zio.stm._

val tArrayGetElem: UIO[Int] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  elem   <- tArray(2)
} yield elem).commit
```

Accessing the non-existing indexes aborts the transaction with `ArrayIndexOutOfBoundsException`.

## Update the value of a TArray

Updating the n-th element of an array can be done as follows:

```scala
import zio._
import zio.stm._

val tArrayUpdateElem: UIO[TArray[Int]] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  _      <- tArray.update(2, el => el + 10)
} yield tArray).commit
```

Updating the n-th element of an array can be done effectfully via `updateM`:

```scala
import zio._
import zio.stm._

val tArrayUpdateMElem: UIO[TArray[Int]] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  _      <- tArray.updateM(2, el => STM.succeed(el + 10))
} yield tArray).commit
```

Updating the non-existing indexes aborts the transaction with `ArrayIndexOutOfBoundsException`.

## Transform elements of a TArray

The transform function `A => A` allows computing a new value for every element in the array: 

```scala
import zio._
import zio.stm._

val transformTArray: UIO[TArray[Int]] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  _      <- tArray.transform(a => a * a)
} yield tArray).commit
```

The elements can be mapped effectfully via `transformM`:

```scala
import zio._
import zio.stm._

val transformMTArray: UIO[TArray[Int]] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  _      <- tArray.transformM(a => STM.succeed(a * a))
} yield tArray).commit
```

Folds the elements of a `TArray` using the specified associative binary operator:

```scala
import zio._
import zio.stm._

val foldTArray: UIO[Int] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  sum    <- tArray.fold(0)(_ + _)
} yield sum).commit
```

The elements can be folded effectfully via `foldM`:

```scala
import zio._
import zio.stm._

val foldMTArray: UIO[Int] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  sum    <- tArray.foldM(0)((acc, el) => STM.succeed(acc + el))
} yield sum).commit
```

## Perform side-effect for TArray elements

`foreach` is used for performing side-effect for each element in the array:

```scala
import zio._
import zio.stm._

val foreachTArray = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  _      <- tArray.foreach(a => STM.succeed(println(a)))
} yield tArray).commit
```
