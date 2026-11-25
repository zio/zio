# Integrations

> The ZIO JSON interop modules for http4s, refined, Scalaz 7 and enumeratum: what each one provides, how to install it, and which platforms and Scala versions it supports.

An interop module connects ZIO JSON to another library. It provides `JsonEncoder`, `JsonDecoder` and related instances for that library's types, or adapts ZIO JSON codecs to that library's own abstractions. Each module is a separate artifact, so you only add the ones you use.

## Modules

Each interop module has a short page with installation steps and examples:

| Module                      | What it gives you                                                                                                                   |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- |
| [http4s](http4s.md)         | `EntityEncoder` and `EntityDecoder` instances for http4s 0.23 built from ZIO JSON codecs, so routes can read and write JSON bodies. |
| [refined](refined.md)       | Encoders, decoders and map-key codecs for refined types (`A Refined P`); decoding checks the predicate.                             |
| [Scalaz 7.x](scalaz-7x.md)  | An encoder and a decoder for Scalaz's `IList`.                                                                                      |
| [enumeratum](enumeratum.md) | Traits and builders that give enumeratum enums (string enums and value enums) their codecs and map-key codecs.                      |

## Platforms and Scala Versions

The modules are not all published for the same platforms. The table below follows the project build (`build.sbt` and `project/BuildHelper.scala`):

| Artifact                      | JVM | Scala.js | Scala Native | Scala 2.12 | Scala 2.13 | Scala 3 |
| ----------------------------- | --- | -------- | ------------ | ---------- | ---------- | ------- |
| `zio-json-interop-http4s`     | Yes | No       | No           | Yes        | Yes        | Yes     |
| `zio-json-interop-refined`    | Yes | Yes      | Yes          | Yes        | Yes        | Yes     |
| `zio-json-interop-scalaz7x`   | Yes | Yes      | Yes          | Yes        | Yes        | No      |
| `zio-json-interop-enumeratum` | Yes | Yes      | Yes          | Yes        | Yes        | Yes     |

The http4s module is a JVM-only project. The other three are cross-built, so in a Scala.js or Scala Native project you add them with `%%%` instead of `%%`:

```scala
libraryDependencies += "dev.zio" %%% "zio-json-interop-refined" % "1.1.3"
```

The Scalaz module has no Scala 3 artifact. The enumeratum module works on Scala 3, but enumeratum's own `findValues` macro needs the `-Yretain-trees` compiler option there for value enums, see [enumeratum on Scala 3](enumeratum.md#scala-3).

The other add-on modules (YAML, golden testing and `@jsonDerive` macros) are listed with their platforms in the [Modules](../index.md#modules) section of the Introduction page.
