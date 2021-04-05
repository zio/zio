---
id: handle-errors
title: "How to Handle Errors?"
---

## Declared Errors vs Unforeseen Defects
A ZIO value has a type parameter `E` which is the type of declared errors it can fail with. `E` only covers the errors which were specified at the outset. The same ZIO value could still throw exceptions in unforeseen ways. These unforeseen situations are called _defects_ in a ZIO program, and they lie outside E.

Bringing abnormal situations from the domain of defects into that of `E` enables the compiler to help us keep a tab on error conditions throughout the application, at compile time. This helps ensure the handling of domain errors in domain specific ways. Defects, on the other hand, can creep silently to higher levels in our application, and, if they get triggered at all, their handling might eventually be in more general ways.

## Lossless Error Model
ZIO holds onto errors, that would otherwise be lost, using `try finally`. If the `try` block throws an exception, and the `finally` block throws an exception as well, then, if these are caught at a higher level, only the finalizer's exception will be caught normally, not the exception from the try block.

Whereas, ZIO guarantees that no errors are lost. This guarantee is provided via a hierarchy of supervisors and information made available via datatypes such as `Exit` & `Cause`. All errors will be reported. If there's a bug in the code, zio enables us to find about it.

## Transform `Option` and `Either` values

It's typical that you work with `Option` and `Either` values inside your application. You either fetch a record from the database which might be there or not (`Option`) or parse a file which might return decode errors `Either`. ZIO has already functions built-in to transform these values into `ZIO` values.

### Either

|from|to|transform|code|
|--|--|--|--|
|`Either[B, A]`|`ZIO[Any, E, A]`|`ifLeft: B => E`|`ZIO.fromEither(from).mapError(ifLeft)`
|`ZIO[R, E, Either[B, A]]`|`ZIO[R, E, A]`|`ifLeft: B => E`|`from.flatMap(ZIO.fromEither(_).mapError(ifLeft))`
|`ZIO[R, E, Either[E, A]]`|`ZIO[R, E, A]`|-|`from.rightOrFail`
|`ZIO[R, E, Either[B, A]]`|`ZIO[R, E, A]`|`f: B => E`|`from.rightOrFailWith(f)`
|`ZIO[R, E, Either[A, E]]`|`ZIO[R, E, A]`|-|`from.leftOrFail`
|`ZIO[R, E, Either[A, B]]`|`ZIO[R, E, A]`|`f: B => E`|`from.leftOrFailWith(f)`
|`ZIO[R, Throwable, Either[Throwable, A]]`|`ZIO[R, Throwable, A]`|-|`from.absolve`

### Option

|from|to|transform|code|
|--|--|--|--|
|`Option[A]`|`ZIO[Any, E, A]`|`ifEmpty: E`|`ZIO.fromOption(from).orElseFail(ifEmpty)`
|`ZIO[R, E, Option[A]]`|`ZIO[R, E, A]`|`ifEmpty: E`|`from.someOrFail(ifEmpty)`



