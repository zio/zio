---
id: zio-2.x-migration-guide
title: "ZIO 2.x Migration Guide"
---

## Upgrading Dependencies

If we are using one of the following dependencies, we need to upgrade them to the `2.0.0-M2` version:

```scala
libraryDependencies += "dev.zio" %% "zio"         % "2.0.0-M2"
libraryDependencies += "dev.zio" %% "zio-streams" % "2.0.0-M2"
libraryDependencies += "dev.zio" %% "zio-test"    % "2.0.0-M2"
```

## Automated Scalafix Rules
TODO

## ZLayer
TODO

## ZIO Streams
TODO

## Removed Methods

- **Arrow Combinators** — (`+++`, `|||`, `onSecond`, `onFirst`, `second`, `first`, `onRight`, `onLeft`, `andThen`, `>>>`, `compose`, `<<<`, `identity`, `swap`, `join`)

As the module pattern in ZIO 2.0 encourages users to use `Has` with the environment `R` (`Has[R]`), it doesn't make sense to have arrow combinators. An arrow makes the `R` parameter as the _input_ of the arrow function, and it doesn't match properly with environments with the `Has` data type. So In ZIO 2.0, all arrow combinators are removed, and we need to use alternatives like doing monadic for-comprehension style or `flatMap`, `provide`, `zip`, and so on.

## Deprecated Methods

In ZIO 2.0, the name of constructors and operators becomes more ergonomic and simple. They reflect more about their purpose rather than just using idiomatic jargon of category theory or functional terms in functional programming with Haskell.

Here are some of the most important changes:

- **Multiple ways of doing the same thing are removed** — For example:
    - Both `ZIO.succeed` and `ZIO.effectTotal` do the same thing. So in ZIO 2.0 we just have one version of these constructors which is `ZIO.succeed`.
    - The bind operator `>>=` is removed. So we just have one way to flatMap which is the `flatMap` method. Therefore, the `>>=` method doesn't surprise the non-Haskellers.
    - The `ZIO#get` method was essentially a more constrained version of `ZIO#some`. So the `get` method is deleted.

- **ZIO.attempt instead of ZIO.effect** — In ZIO 2.0 all ZIO constructors like `ZIO.effect*` that create a ZIO from a side effect are deprecated and renamed to the `ZIO.attempt*` version. For example, when we are reading from a file, it's more meaning full to say we are attempting to read from a file instead of saying we have an effect of reading from a file.

- **`ZIO` instead of the `M` suffix** — In effectful operations, the `M` suffix is renamed to the `ZIO` suffix. In ZIO 1.x, the `M` suffix in an effectful operation means that the operation works with monad in a monadic context. This naming convention is the legacy of Haskell jargon. In ZIO 2.x, all these suffixes are renamed to the `ZIO`. For example, the `ifM` operator is renamed to `ifZIO`.

- **`Discard` instead of the underscore `_` suffix** — The underscore suffix is another legacy naming convention from Haskell's world. In ZIO 1.x, the underscore suffix means we are going to discard the result. The underscore version works exactly like the one without the underscore, but it discards the result and returns `Unit` in the ZIO context. For example, the `collectAll_` operator renamed to `collectAllDiscard`.

- **`as`, `to`, `into` prefixes** — The `ZIO#asService` method is renamed to `ZIO#toLayer` and also the `ZIO#to` is renamed to the `ZIO#intoPromise`. So now we have three categories of conversion:
    1. **as** — The `ZIO#as` method and its variants like `ZIO#asSome`, `ZIO#asSomeError` and `ZIO#asService` are used when transforming the `A` inside of a `ZIO`, generally as shortcuts for `map(aToFoo(_))`.
    2. **to** — The `ZIO#to` method and its variants like `ZIO#toLayer`, `ZIO#toManaged`, and `ZIO#toFuture` are used when the `ZIO` is transformed into something else other than `ZIO` data-type.
    3. **into** — All `into*` methods, accept secondary data-type, modify it with the result of the current effect (e.g. `ZIO#intoPromise`, `ZStream#intoHub`, `ZStream#intoQueue` and `ZStream#intoManaged`)

### ZIO

| ZIO 1.x                      | ZIO 2.x                       |
|------------------------------+-------------------------------|
| ZIO#&&&                      | ZIO#zip                       |
| ZIO#>>=                      | ZIO#flatMap                   |
| ZIO#bimap                    | ZIO#mapBoth                   |
| ZIO#mapEffect                | ZIO#mapAttempt                |
| ZIO#filterOrElse_            | ZIO#filterOrElse              |
| ZIO#foldCauseM               | ZIO#foldCauseZIO              |
| ZIO#foldM                    | ZIO#foldZIO                   |
| ZIO#foldTraceM               | ZIO#foldTraceZIO              |
|------------------------------+-------------------------------|
| ZIO#get                      | ZIO#some                      |
| ZIO#optional                 | ZIO#unoption                  |
| ZIO#someOrElseM              | ZIO#someOrElseZIO             |
|------------------------------+-------------------------------|
| ZIO.forkAll_                 | ZIO.forkAllDiscard            |
| ZIO.fromFiberM               | ZIO.fromFiberZIO              |
| ZIO.require                  | ZIO.someOrFail                |
| ZIO#on                       | ZIO#lockExecutionContext      |
| ZIO#rejectM                  | ZIO#rejectZIO                 |
| ZIO#run                      | ZIO#exit                      |
| ZIO#timeoutHalt              | ZIO#timeoutFailCause          |
|------------------------------+-------------------------------|
| ZIO#to                       | ZIO#intoPromise               |
| ZIO#asService                | ZIO#toLayer                   |
|------------------------------+-------------------------------|
| ZIO.accessM                  | ZIO.accessZIO                 |
| ZIO.fromFunctionM            | ZIO.accessZIO                 |
| ZIO.fromFunction             | ZIO.access                    |
| ZIO.services                 | ZIO.service                   |
|------------------------------+-------------------------------|
| ZIO.bracket                  | ZIO.acquireReleaseWith        |
| ZIO.bracketExit              | ZIO.acquireReleaseExitWith    |
| ZIO.bracketAuto              | ZIO.acquireReleaseWithAuto    |
| ZIO#bracket                  | ZIO#acquireReleaseWith        |
| ZIO#bracket_                 | ZIO#acquireRelease            |
| ZIO#bracketExit              | ZIO#acquireReleaseExitWith    |
| ZIO#bracketExit              | ZIO#acquireReleaseExitWith    |
| ZIO#bracketOnError           | ZIO#acquireReleaseOnErrorWith |
| ZIO#toManaged_               | ZIO#toManaged                 |
|------------------------------+-------------------------------|
| ZIO.collectAll_              | ZIO.collectAllDiscard         |
| ZIO.collectAllPar_           | ZIO.collectAllParDiscard      |
| ZIO.collectAllParN_          | ZIO.collectAllParNDiscard     |
| ZIO#collectM                 | ZIO#collectZIO                |
|------------------------------+-------------------------------|
| ZIO.effect                   | ZIO.attempt                   |
| ZIO.effectAsync              | ZIO.async                     |
| ZIO.effectAsyncInterrupt     | ZIO.asyncInterrupt            |
| ZIO.effectAsyncM             | ZIO.asyncZIO                  |
| ZIO.effectAsyncMaybe         | ZIO.asyncMaybe                |
| ZIO.effectBlocking           | ZIO.attemptBlocking           |
| ZIO.effectBlockingCancelable | ZIO.attemptBlockingCancelable |
| ZIO.effectBlockingIO         | ZIO.attemptBlockingIO         |
| ZIO.effectBlockingInterrupt  | ZIO.attemptBlockingInterrupt  |
| ZIO.effectSuspend            | ZIO.suspend                   |
| ZIO.effectSuspendTotal       | ZIO.suspendSucceed            |
| ZIO.effectSuspendTotalWith   | ZIO.suspendSucceedWith        |
| ZIO.effectSuspendWith        | ZIO.suspendWith               |
| ZIO.effectTotal              | ZIO.succeed                   |
|------------------------------+-------------------------------|
| ZIO.foreach_                 | ZIO.foreachDiscard            |
| ZIO.foreachPar_              | ZIO.foreachParDiscard         |
| ZIO.foreachParN_             | ZIO.foreachParNDiscard        |
| ZIO#replicateM               | ZIO#replicateZIO              |
| ZIO#replicateM_              | ZIO#replicateZIODiscard       |
|------------------------------+-------------------------------|
| ZIO.halt                     | ZIO.failCause                 |
| ZIO.haltWith                 | ZIO.failCauseWith             |
|------------------------------+-------------------------------|
| ZIO.ifM                      | ZIO.ifZIO                     |
| ZIO.loop_                    | ZIO.loopDiscard               |
| ZIO.whenCaseM                | ZIO.whenCaseZIO               |
| ZIO.whenM                    | ZIO.whenZIO                   |
| ZIO.unlessM                  | ZIO.unlessZIO                 |
| ZIO#unlessM                  | ZIO#unlessZIO                 |
| ZIO#whenM                    | ZIO#whenZIO                   |
| ZIO#repeatUntilM             | ZIO#repeatUntilZIO            |
| ZIO#repeatWhileM             | ZIO#repeatWhileZIO            |
| ZIO#retryUntilM              | ZIO#retryUntilZIO             |
| ZIO#retryWhileM              | ZIO#retryWhileZIO             |
| ZIO.replicateM               | ZIO.replicateZIO              |
| ZIO.replicateM_              | ZIO.replicateZIODiscard       |
|------------------------------+-------------------------------|
| ZIO.tupled                   | ZIO.zip                       |
| ZIO.tupledPar                | ZIO.zipPar                    |
| ZIO.mapN                     | ZIO.zip                       |
| ZIO.mapParN                  | ZIO.zipPar                    |
|------------------------------+-------------------------------|
| ZIO.validate_                | ZIO.validateDiscard           |
| ZIO.validatePar_             | ZIO.validateParDiscard        |
|------------------------------+-------------------------------|
