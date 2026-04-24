# TArray

> `TArray` is an array of mutable references that can participate in transactions in STM.

`TArray` is an array of mutable references that can participate in transactions in STM.

## Create a TArray

Creating an empty `TArray`:

```scala

val emptyTArray: STM[Nothing, TArray[Int]] = TArray.empty[Int]
```

Or creating a `TArray` with specified values:

```scala

val specifiedValuesTArray: STM[Nothing, TArray[Int]] = TArray.make(1, 2, 3)
```

Alternatively, you can create a `TArray` by providing a collection of values:

```scala

val iterableTArray: STM[Nothing, TArray[Int]] = TArray.fromIterable(List(1, 2, 3))
```

## Retrieve the value from a TArray

The n-th element of the array can be obtained as follows:

```scala

val tArrayGetElem: UIO[Int] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  elem   <- tArray(2)
} yield elem).commit
```

Accessing the non-existing indexes aborts the transaction with `ArrayIndexOutOfBoundsException`.

## Update the value of a TArray

Updating the n-th element of an array can be done as follows:

```scala

val tArrayUpdateElem: UIO[TArray[Int]] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  _      <- tArray.update(2, el => el + 10)
} yield tArray).commit
```

Updating the n-th element of an array can be done effectfully via `updateSTM`:

```scala

val tArrayUpdateMElem: UIO[TArray[Int]] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  _      <- tArray.updateSTM(2, el => STM.succeed(el + 10))
} yield tArray).commit
```

Updating the non-existing indexes aborts the transaction with `ArrayIndexOutOfBoundsException`.

## Transform elements of a TArray

The transform function `A => A` allows computing a new value for every element in the array: 

```scala

val transformTArray: UIO[TArray[Int]] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  _      <- tArray.transform(a => a * a)
} yield tArray).commit
```

The elements can be mapped effectfully via `transformSTM`:

```scala

val transformSTMTArray: UIO[TArray[Int]] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  _      <- tArray.transformSTM(a => STM.succeed(a * a))
} yield tArray).commit
```

Folds the elements of a `TArray` using the specified associative binary operator:

```scala

val foldTArray: UIO[Int] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  sum    <- tArray.fold(0)(_ + _)
} yield sum).commit
```

The elements can be folded effectfully via `foldSTM`:

```scala

val foldSTMTArray: UIO[Int] = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  sum    <- tArray.foldSTM(0)((acc, el) => STM.succeed(acc + el))
} yield sum).commit
```

## Perform effects for TArray elements

`foreach` is used for performing an STM effect for each element in the array:

```scala

val foreachTArray = (for {
  tArray <- TArray.make(1, 2, 3, 4)
  tQueue <- TQueue.unbounded[Int]
  _      <- tArray.foreach(a => tQueue.offer(a).unit)
} yield tArray).commit
```
