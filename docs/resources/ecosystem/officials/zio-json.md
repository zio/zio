---
id: zio-json
title: "ZIO JSON"
---

[ZIO Json](https://github.com/zio/zio-json) is a fast and secure JSON library with tight ZIO integration.

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
libraryDependencies += "dev.zio" %% "zio-json" % "0.3.0-RC8"
```

## Example

Let's try a simple example of encoding and decoding JSON using ZIO JSON:

```scala mdoc:compile-only
import zio.json._

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
