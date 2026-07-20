# Introduction to izumi-reflect

> > @quote: Looks a bit similar to TypeTag

# izumi-reflect

> @quote: Looks a bit similar to TypeTag

`izumi-reflect` is a fast, lightweight, portable and efficient alternative for `TypeTag` from `scala-reflect`.

`izumi-reflect` is a lightweight model of Scala type system and provides a simulator of the important parts of the Scala typechecker.

## Why `izumi-reflect`

1. `izumi-reflect` compiles faster, runs a lot faster than `scala-reflect` and is fully immutable and [thread-safe](https://github.com/scala/bug/issues/10766),
2. `izumi-reflect` supports Scala 2.11, 2.12, 2.13 and **Scala 3**,
3. `izumi-reflect` supports Scala.js and Scala Native,
4. `izumi-reflect` works well with [GraalVM Native Image](https://www.graalvm.org/reference-manual/native-image/),
5. `izumi-reflect` allows you to obtain tags for unapplied type constructors (`F[_]`) and combine them at runtime.

## Credits

`izumi-reflect` has been created by [Septimal Mind](https://7mind.io) to power [Izumi Project](https://github.com/7mind/izumi),
as a replacement for `TypeTag` in reaction to a lack of confirmed information about the future of `scala-reflect`/`TypeTag` in Scala 3 ([Motivation](https://blog.7mind.io/lightweight-reflection.html)), and donated to ZIO.

  
  
  

## Limitations

`izumi-reflect` model of the Scala type system is not 100% precise, but "good enough" for the vast majority of the usecases.

Known limitations are:

1. Recursive type bounds (F-bounded types) are not preserved and may produce false positives,
2. Existential types, both written with wildcards and `forSome` may produce unexpected results, the support is limited,
3. Path-Dependent Types are based on variable names and may cause unexpected results when variables with different names have the same type or vice-versa (vs. Scala compiler)
4. This-Types such as `X.this.type` are ignored and identical to `X`
5. `izumi-reflect` is less powerful than `scala-reflect`: it does not preserve fields and methods when it's not necessary for equality and subtype checks, it does not preserve code trees, internal compiler data structures, etc.
6. There are some optimizations in place which reduce correctness, namely: subtype check for `scala.Matchable` will always return true, no distinction is made between `scala.Any` and `scala.AnyRef`.
7. Lower bounds are not preserved in abstract higher-kinded type members which may produce false comparisons.
8. Type and value members are not preserved in concrete types which may produce false comparisons with refined/structural types. (https://github.com/zio/izumi-reflect/issues/481)

## Debugging

Set [`-Dizumi.reflect.debug.macro.rtti=true`](https://javadoc.io/doc/dev.zio/izumi-reflect_2.13/latest/izumi/reflect/DebugProperties$.html#izumi.reflect.debug.macro.rtti:String(%22izumi.reflect.debug.macro.rtti%22)) to enable debug output during compilation when tags are constructed and at runtime when they are compared.

```shell
sbt -Dizumi.reflect.debug.macro.rtti=true
```

To see debug output when compiling in Intellij, add the above flag to `VM options` in [Preferences -> Build, Execution, Deployment -> Compiler -> Scala Compiler -> Scala Compile Server](jetbrains://idea/settings?name=Build%2C+Execution%2C+Deployment--Compiler--Scala+Compiler--Scala+Compile+Server)

You may also set it in `.jvmopts` file during development. (`.jvmopts` properties will not apply to Intellij compile server, only to sbt)

Set `-Dizumi.reflect.debug.macro.rtti.assertions=true` to enable additional assertions.

Other useful system properties are:

- [`izumi.reflect.rtti.optimized.equals`](https://javadoc.io/doc/dev.zio/izumi-reflect_2.13/latest/izumi/reflect/DebugProperties$.html#izumi.reflect.rtti.optimized.equals:String(%22izumi.reflect.rtti.optimized.equals%22))
- [`izumi.reflect.rtti.cache.compile`](https://javadoc.io/doc/dev.zio/izumi-reflect_2.13/latest/izumi/reflect/DebugProperties$.html#izumi.reflect.rtti.cache.compile:String(%22izumi.reflect.rtti.cache.compile%22))

# Talks

* [Kit Langton — Scala 3 Macro Fun (Open Source Hackery)](https://www.youtube.com/watch?v=wsLhjqCKZuU)
* [Pavel Shirshov — Izumi Reflect: Scala Type System Model](https://www.youtube.com/watch?v=ShRfzVz58OY)

# See also

## [`gzoller/scala-reflection`](https://github.com/gzoller/scala-reflection)

* Scala 3 only
* No support for subtype checks
* Type lambdas are not supported
* _Preserves field information_

## [`airframe-surface`](https://wvlet.org/airframe/docs/airframe-surface)

* Scala 2 and Scala 3
* No support for subtype checks
* _Preserves field information_

## And even more

1. https://github.com/gaeljw/typetrees - a very basic type tag substitute for Scala 3
2. https://stackoverflow.com/questions/75752812/is-there-a-simple-scala-3-example-of-how-to-use-quoted-type-as-replacement-for - discussion on StackOverflow
3. https://contributors.scala-lang.org/t/scala-3-and-reflection/3627 - original discussion on Scala Contributors forum

[Stage]: https://img.shields.io/badge/Project%20Stage-Production%20Ready-brightgreen.svg
[Stage-Page]: https://github.com/zio/zio/wiki/Project-Stages
