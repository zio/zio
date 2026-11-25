# Scala Versions and Platforms

> All modules are published for the JVM, Scala.js and Scala Native, except `zio-prelude-scala-parallel-collections`, which is published for the JVM and Scala Native only. Use `%%%` instead of `%%` on Scala.js and Scala Native.

## Platforms

## Scala Versions

ZIO Prelude is published for Scala 2.12, 2.13 and 3. The abstractions and data types behave the same in all of them. The differences are in the macro-based features and in a few APIs that rely on language features.

| Area | Scala 2 | Scala 3 |
|------|---------|---------|
| Newtypes | Defined in `Newtype` and `Subtype`. `wrap` and `derive` are `protected`, so only code inside the newtype object can call them. | The same API with `wrap` and `derive` public. See [Constructing Newtypes](../newtypes/constructing.md). |
| Smart newtype validation | Checked by macros from `zio-prelude-macros`. | Checked by inline macros, and you can add a custom [Validator](../newtypes/validator.md). |
| Deriving `Equal` | No `derived` method. Define the instance with `Equal.default` or `Equal.make`. | `Equal.derived` builds an instance for case classes and sealed traits from the standard `Mirror`. |
| `Debug` for arrays | Not applicable. | A `Debug[IArray[A]]` instance is provided. |

Scala 2.12 has parallel collections in its standard library, while Scala 2.13 and 3 need the separate `scala-parallel-collections` library. See [Scala Parallel Collections](../scala-parallel-collections/index.md) for how that module handles it.
