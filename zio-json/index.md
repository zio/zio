# Getting Started with ZIO Json

> [ZIO Json](https://github.com/zio/zio-json) is a fast and secure JSON library with tight ZIO integration.

[ZIO Json](https://github.com/zio/zio-json) is a fast and secure JSON library with tight ZIO integration.

@PROJECT_BADGES@

## Introduction

The goal of this project is to create the best all-round JSON library for Scala:

- **Performance** to handle more requests per second than the incumbents, i.e. reduced operational costs.
- **Security** to mitigate against adversarial JSON payloads that threaten the capacity of the server.
- **Fast Compilation** no shapeless, no type astronautics.
- **Future-Proof**, prepared for Scala 3 and next-generation Java.
- **Simple** small codebase, concise documentation that covers everything.
- **Helpful errors** are readable by humans and machines.
- **ZIO Integration** so nothing more is required.

## Installation

In order to use this library, we need to add the following line in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-json" % "@VERSION@"
```

For cross-platform projects with Scala.js and Scala Native need to replace `%%` operator by `%%%`, 
and optionally when using `java.time.ZoneId` and `java.time.ZonedDateTime` types need to add 
the dependency on the latest version of Timezone DB:

```scala
libraryDependencies += "io.github.cquiroz" %%% "scala-java-time-tzdb" % "latest.integration"
```

## Example

Let's try a simple example of encoding and decoding JSON using ZIO JSON.

All the following code snippets assume that the following imports have been declared

<Tabs groupId="language">
  <TabItem value="scala 2" label="Scala 2">

```scala

```

  </TabItem>
  <TabItem value="scala 3" label="Scala 3" default>

```scala

```

  </TabItem>
</Tabs>

Say we want to be able to read some JSON like

```json
{"curvature":0.5}
```

into a Scala `case class`

```scala
final case class Banana(curvature: Double)
```

<Tabs groupId="language">
  <TabItem value="scala 2" label="Scala 2">

To do this, we create an *instance* of the `JsonDecoder` typeclass for `Banana` using the `zio-json` code generator. It is best practice to put it on the companion of `Banana`, like so

```scala
object Banana {
  implicit val decoder: JsonDecoder[Banana] = DeriveJsonDecoder.gen[Banana]
}
```

  </TabItem>
  <TabItem value="scala 3" label="Scala 3" default>

To do this, we derive an *instance* of the `JsonDecoder` typeclass for `Banana`.

```scala
final case class Banana(curvature: Double) derives JsonDecoder
```

Note: If your case class is defining default parameters, -Yretain-trees needs to be added to scalacOptions.

  </TabItem>
</Tabs>

Now we can parse JSON into our object

```
scala> """{"curvature":0.5}""".fromJson[Banana]
val res: Either[String, Banana] = Right(Banana(0.5))
```

Likewise, to produce JSON from our data we derive a `JsonEncoder`

<Tabs groupId="language">
  <TabItem value="scala 2" label="Scala 2">

```scala
object Banana {
  ...
  implicit val encoder: JsonEncoder[Banana] = DeriveJsonEncoder.gen[Banana]
}
```

  </TabItem>
  <TabItem value="scala 3" label="Scala 3" default>

```scala
final case class Banana(curvature: Double) derives JsonEncoder
```

  </TabItem>
</Tabs>

```
scala> Banana(0.5).toJson
val res: String = {"curvature":0.5}

scala> Banana(0.5).toJsonPretty
val res: String =
{
  "curvature" : 0.5
}
```

And bad JSON will produce an error in `jq` syntax with an additional piece of contextual information (in parentheses)

```
scala> """{"curvature": womp}""".fromJson[Banana]
val res: Either[String, Banana] = Left(.curvature(expected a Double))
```

Say we extend our data model to include more data types

<Tabs groupId="language">
  <TabItem value="scala 2" label="Scala 2">

```scala
sealed trait Fruit
final case class Banana(curvature: Double) extends Fruit
final case class Apple (poison: Boolean)   extends Fruit
```

  </TabItem>
  <TabItem value="scala 3" label="Scala 3" default>

```scala
enum Fruit {
  case Banana(curvature: Double)
  case Apple (poison: Boolean)
}
```

  </TabItem>
</Tabs>

we can generate the encoder and decoder for the entire `sealed` family

<Tabs groupId="language">
  <TabItem value="scala 2" label="Scala 2">

```scala
object Fruit {
  implicit val decoder: JsonDecoder[Fruit] = DeriveJsonDecoder.gen[Fruit]
  implicit val encoder: JsonEncoder[Fruit] = DeriveJsonEncoder.gen[Fruit]
}
```

  </TabItem>
  <TabItem value="scala 3" label="Scala 3" default>

```scala
enum Fruit derives JsonCodec {
  case Banana(curvature: Double)
  case Apple (poison: Boolean)
}
```

  </TabItem>
</Tabs>

allowing us to load the fruit based on a single field type tag in the JSON

```
scala> """{"Banana":{"curvature":0.5}}""".fromJson[Fruit]
val res: Either[String, Fruit] = Right(Banana(0.5))

scala> """{"Apple":{"poison":false}}""".fromJson[Fruit]
val res: Either[String, Fruit] = Right(Apple(false))
```

Almost all of the standard library data types are supported as fields on the case class, and it is easy to add support if one is missing.

<Tabs groupId="language">
  <TabItem value="scala 2" label="Scala 2">

```scala mdoc:compile-only

sealed trait Fruit                   extends Product with Serializable
case class Banana(curvature: Double) extends Fruit
case class Apple(poison: Boolean)    extends Fruit

object Fruit {
  implicit val decoder: JsonDecoder[Fruit] =
    DeriveJsonDecoder.gen[Fruit]

  implicit val encoder: JsonEncoder[Fruit] =
    DeriveJsonEncoder.gen[Fruit]
}

val json1         = """{ "Banana":{ "curvature":0.5 }}"""
val json2         = """{ "Apple": { "poison": false }}"""
val malformedJson = """{ "Banana":{ "curvature": true }}"""

json1.fromJson[Fruit]
json2.fromJson[Fruit]
malformedJson.fromJson[Fruit]

List(Apple(false), Banana(0.4)).toJsonPretty
```

  </TabItem>
  <TabItem value="scala 3" label="Scala 3" default>

```scala

enum Fruit derives JsonCodec {
  case Banana(curvature: Double)
  case Apple(poison: Boolean)
}

export Fruit.*

val json1         = """{ "Banana":{ "curvature":0.5 }}"""
val json2         = """{ "Apple": { "poison": false }}"""
val malformedJson = """{ "Banana":{ "curvature": true }}"""

json1.fromJson[Fruit]
json2.fromJson[Fruit]
malformedJson.fromJson[Fruit]

List(Apple(false), Banana(0.4)).toJsonPretty
```

  </TabItem>
</Tabs>

# How

Extreme **performance** is achieved by decoding JSON directly from the input source into business objects (inspired by [plokhotnyuk](https://github.com/plokhotnyuk/jsoniter-scala)). Although not a requirement, the latest advances in [Java Loom](https://wiki.openjdk.java.net/display/loom/Main) can be used to support arbitrarily large payloads with near-zero overhead.

Best in class **security** is achieved with an aggressive *early exit* strategy that avoids costly stack traces, even when parsing malformed numbers. Malicious (and badly formed) payloads are rejected before finishing reading.

**Fast compilation** and **future-proofing** is possible thanks to [Magnolia](https://propensive.com/opensource/magnolia/) which allows us to generate boilerplate in a way that will survive the exodus to Scala 3. `zio-json` is internally implemented using a [`java.io.Reader`](https://docs.oracle.com/en/java/javase/14/docs/api/java.base/java/io/Reader.html) / [`java.io.Writer`](https://docs.oracle.com/en/java/javase/14/docs/api/java.base/java/io/Writer.html)-like interface, which is making a comeback to center stage in Loom.

**Simplicity** is achieved by using well-known software patterns and avoiding bloat. The only requirement to use this library is to know about Scala's encoding of typeclasses, described in [Functional Programming for Mortals](https://leanpub.com/fpmortals/read#leanpub-auto-functionality).

**Helpful errors** are produced in the form of a [`jq`](https://stedolan.github.io/jq/) query, with a note about what went wrong, pointing to the exact part of the payload that failed to parse.
