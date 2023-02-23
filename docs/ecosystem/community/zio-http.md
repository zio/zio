---
id: zio-http
title: "ZIO HTTP"
---

[ZIO HTTP](https://github.com/zio/zio-http) is a scala library to write HTTP applications.

## Introduction

ZIO HTTP is a Scala library for building HTTP applications. It is powered by ZIO and netty and aims at being the defacto solution for writing, highly scalable, and performant web applications using idiomatic scala.

## Installation

Please note that library versions `1.x` or `2.x` of ZIO HTTP are derived from Dream11, the organization that donated ZIO HTTP to the ZIO organization in 2022. 

Newer library versions, starting in 2023 and resulting from the ZIO organization start with `0.x`. 

In order to use this library, we need to add the following lines in our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-http" % "0.0.4"
```

## Example

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.model.Method

object HelloWorld extends ZIOAppDefault {

  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
  }

  override val run =
    Server.serve(app).provide(Server.default)
}
```

## Resources (please note that these resources are targeted at versions of ZIO HTTP before it was donated to the ZIO organization)

- [ZIO HTTP Tutorial: The REST of the Owl ](https://blog.rockthejvm.com/zio-http/) by Rock the JVM
- [ZIO World - ZIO HTTP](https://www.youtube.com/watch?v=dVggks9_1Qk&t=257s) by Tushar Mathur (March 2020) — At ZIO World Tushar Mathur unveiled a new open-source library 'ZIO HTTP' that gives you better performance than Vert.x, but with pure functional Scala and native ZIO integration.
