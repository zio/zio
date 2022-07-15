---
id: build-a-restful-webservice
title: "Tutorial: How to Build a RESTful Web Service"
sidebar_label: "Building a RESTful Web Service"
---

ZIO provides good support for building RESTful web services. Using _Service Pattern_, we can build web services that are modular and easy to test and maintain. On the other hand, we have several powerful official and community libraries that can help us to work with JSON data types, and databases and also work with HTTP protocol.

In this tutorial, we will learn how to build a RESTful web service using ZIO. The corresponding source code for this tutorial is available on [GitHub](https://github.com/zio/zio-quickstart-restful-webservice). If you haven't read the [ZIO Quickstart: Building RESTful Web Service](../quickstarts/build-a-restful-webservice.md) yet, we recommend you to read it first and download and run the source code, before reading this tutorial.

## Dependencies

In this tutorial we are going to use the following projects other than the ZIO itself:

- [ZIO HTTP](https://dream11.github.io/zio-http/) for the HTTP server
- [ZIO JSON](https://zio.github.io/zio-json/) for the JSON serialization
- [ZIO Quill](https://zio.github.io/zio-quill/) for type-safe queries on the JDBC database

## Introduction to The `Http` Data Type

Before we start to build a RESTful web service, we need to understand the `Http` data type. It is a data type that models an HTTP application, just like the `ZIO` data type that models ZIO workflows. We can think of the `Http[R, E, Request, Response]` as a description of an HTTP application that accepts requests and returns responses. It can use the environment of type `R` and may fail with an error of type `E`.

The `Http` is defined as follows:

```scala
trait Http[-R, +E, -A, +B] extends (A => ZIO[R, Option[E], B])
```

We can say that `Http[R, E, A, B]` is a function that takes an `A` and returns a `ZIO[R, Option[E], B]`. To put it another way, `HTTP[R, E, A, B]` is an HTTP application that:
- Accepts an `A` and returns `B`
- Uses the `R` from the environment
- Will fail with `E` if there is an error

Like the `ZIO` data type, it can be transformed and also composed with other `Http` data types to build complex and large HTTP applications.

## Modeling Http Applications

Let's try to model some HTTP applications using the `Http` data type. So first, we are going to learn some basic `Http` constructors and how to combine them to build more complex HTTP applications.

### Creation of `Http` Applications

The `Http.succeed` constructor creates an `Http` application that always returns a successful response:

```scala
val app: Http[Any, Nothing, Any, String] = Http.succeed("Hello, world!")
```

We have the same constructor for failures called `Http.fail`. It creates an `Http` application that always returns a failed response:

```scala
val app: Http[Any, String, Any, Nothing] = Http.fail("Something went wrong")
```

We can also create `Http` programs from total and partial functions. The `Http.fromFunction` constructor takes a total function of type `A => B` and then creates an `Http` application that accepts an `A` and returns a `B`:

```scala
val app: Http[Any, Nothing, Int, Double] = Http.fromFunction[Int](_ / 2.0)
```

And the `Http.collect` constructor takes a partial function of type `PartialFunction[A, B]` and then creates an `Http` application that accepts an `A` and returns a `B`:

```scala
val app: Http[Any, Nothing, String, Int] =
  Http.collect {
    case "case 1" => 1
    case "case 2" => 2
  }
``` 

Http applications can be effectual. We have a couple of constructors that can be used to create an `Http` applications that are effectual:

- `Http.fromZIO`
- `Http.fromStream`
- `Http.fromFunctionZIO`
- `Http.collectZIO`
- `Http.fromFile`

There are lots of other constructors, but we will not go into them here.

### Combining `Http` Applications

The `Http` data type is composable like `ZIO`. We can create new complex `Http` applications by combining existing simple ones by using `flatMap`, `zip`, `andThen`, `orElse`, and `++` methods:

```scala
val a           : Http[Any, Nothing, Int, Double]    = ???
val b           : Http[Any, Nothing, Double, String] = ???
def c(i: Double): Http[Any, Nothing, Long, String]   = ???

val d = a >>= c // a flatMap c (combine two http sequentially)
val e = a ++ b  // a defaultWith b (combine two http app)
val f = a >>> b // a andThen b (pipe output of a to input of b)
val g = a <<< b // a compose b (pipe input of b to output of a)
val h = a <> b  // a orElse b  (run a, if it fails, run b)
```

Assume we have written several `Http` apps, like `GreetingApp`, `DownloadApp`, `CounterApp`, and `UserApp` we can combine them and ask the `Server` to run them in parallel:

```scala
Server
  .start(
    port = 8080,
    http = GreetingApp() ++ DownloadApp() ++ CounterApp() ++ UserApp()
  )
```

## `Request` and `Response`

Until now, we have learned how to create `Http` applications with some simple request and response types, e.g. `String` and `Int` in an `Http[Any, Nothing, String, Int]`. But, in real life, when we want to deal with HTTP requests and responses, we need to have a more complex type for the request and response.

ZIO HTTP provides a type `Request` for HTTP requests and a type `Response` for HTTP responses. It has a built-in decoder for `Request` and encoder for `Response`. So we don't need to worry about the details of how requests and responses are decoded and encoded.

In order to start an HTTP server, the ZIO HTTP requires an HTTP application of type `HttpApp[R, E]` which is type alias for `Http[R, E, Request, Response]`:

```scala
type HttpApp[-R, +E] = Http[R, E, Request, Response]

object Server {
  def start[R](
    port: Int,
    http: HttpApp[R, Throwable],
  ): ZIO[R, Throwable, Nothing] = ???
}
```
