---
id: restful-webservice
title: "ZIO Quickstart: Building RESTful Web Service"
sidebar_label: "RESTful Web Service"
---

This quickstart shows how to build a RESTful web service using ZIO. It uses
- [ZIO HTTP](https://zio.dev/zio-http/) for the HTTP server
- [ZIO JSON](https://zio.dev/zio-json/) for the JSON serialization
- [ZIO Quill](https://zio.dev/zio-quill/) for type-safe queries on the JDBC database

## Running The Example

First, open the console and clone the project using `git` (or you can simply download the project) and then change the directory:

```scala
git clone git@github.com:zio/zio-quickstarts.git 
cd zio-quickstarts/zio-quickstart-restful-webservice
```

Once you are inside the project directory, run the application:

```bash
sbt run
```

## Testing The Quickstart

In this quickstart, we will build a RESTful web service that has the following Http apps:

- **Greeting App**— shows how to write a basic Http App.
- **Download App**— shows how to work with files, headers, and status codes and also streaming data.
- **Counter App**— shows how to have a stateful web service and how to use the ZIO environment for Http Apps.
- **User App**— shows how to have a stateful web service to register and manage users.

The most important part of this quickstart is learning how to build an `Http` data type that is used to build the HTTP apps:

```scala
trait Http[-R, +E, -A, +B] extends (A => ZIO[R, Option[E], B])
```

It is a data type that models an HTTP application, just like the `ZIO` data type that models ZIO workflows.

We can say that `Http[R, E, A, B]` is a function that takes an `A` and returns a `ZIO[R, Option[E], B]`. To put it another way, `HTTP[R, E, A, B]` is an HTTP application that:
- Accepts an `A` and returns `B`
- Uses the `R` from the environment
- Will fail with `E` if there is an error

Like the `ZIO` data type, it can be transformed and also composed with other `Http` data types to build complex and large HTTP applications.

### 1. Greeting App

The Greeting App is a simple Http App that returns a greeting message. First, let's see how this app is defined:

```scala
object GreetingApp {
  def apply(): Http[Any, Nothing, Request, Response] = ???
}
```

So this means that this app doesn't require any services from the environment (`Any`), it doesn't fail (`Nothing`), and it takes a request (`Request`) and returns a response (`Response`).

It has three routes, and we are going to test them one by one:

1. When we build and run this quickstart, there is a greeting app that we can access using the following endpoint:

```bash
GET http://localhost:8080/greet
```

Let's try to access this endpoint using curl and see what we get:

```bash
user@host ~> curl -i localhost:8080/greet
HTTP/1.1 200 OK
content-type: text/plain
content-length: 12

Hello World!⏎
```

2. We have another endpoint that pattern matches the `/greet/:name` request:

```bash
GET http://localhost:8080/greet/:name
```

Using this endpoint, we can greet a user by its name:

```bash
user@host ~> curl -i http://localhost:8080/greet/John
HTTP/1.1 200 OK
content-type: text/plain
content-length: 10

Hello John!⏎
```

3. Finally, we have a third endpoint that extracts the names from the query parameters:

```bash
GET http://localhost:8080/greet?name=:name
```

Let's try to request this endpoint and see what we get:

```bash
user@host ~> curl -i "http://localhost:8080/greet?name=John"
HTTP/1.1 200 OK
content-type: text/plain
content-length: 11

Hello John!⏎
```

It also works for more than one query params:

```bash
user@host ~> curl -i "http://localhost:8080/greet?name=Jane&name=John"
HTTP/1.1 200 OK
content-type: text/plain
content-length: 21

Hello Jane and John!⏎
```

### 2. Download App

The next example shows how to download a file from the server. First, let's look at the type of the `downloadApp`:

```scala
object DownloadApp {
  def apply(): Http[Any, Throwable, Request, Response] = ???
}
```

It is an Http App that doesn't require any environment, it may fail with `Throwable` and it consumes a `Request` and produces a `Response` respectively.

Let's try to access this endpoint using curl and see what we get:


1. Our first endpoint is `/download` which downloads a file from the server:

```bash
GET http://localhost:8080/download
```

If we try to request this endpoint using curl, we will see the following output:

```bash
user@host ~> curl -i http://localhost:8080/download
HTTP/1.1 200 OK
Content-Type: application/octet-stream
Content-Disposition: attachment; filename=file.txt
transfer-encoding: chunked

line number 1
1, 2, 3, 4, 5
line number 3
end of file
```

Also, if we try to access this URL from the browser, the browser will prompt us to download the file with `file.txt` as the name.

2. The second endpoint is an example of downloading a big file when we want to stream the chunks of the file to the client:

```bash
GET http://localhost:8080/download/stream
```

When we try to access this endpoint using curl, we will see the following output:

```bash
curl -i http://localhost:8080/download/stream
HTTP/1.1 200 OK
transfer-encoding: chunked

1
2
3
...
```

We have scheduled some delays between each line to simulate downloading a big file. So when we run the above `curl` command, we can see that the content of the file will be downloaded gradually.

### 3. Counter App

The next example shows how we can have a stateful web service. Let's look at the type of the `counterApp`:

```scala
object CounterApp {
  def apply(): Http[Ref[Int], Nothing, Request, Response] = ???
}
```

This is an Http app that requires a `Ref[Int]` as an environment, it cannot fail and it consumes a `Request` and produces a `Response` respectively.

This counter increments every time we access the `/up` endpoint and decrements every time we access the `/down` endpoint:

```bash
GET http://localhost:8080/up
Get http://localhost:8080/down
```

Let's try to access the `up` endpoint 100 times and then access the `down` endpoint 25 times:

```bash
user@host ~> for i in {1..100}; do curl http://localhost:8080/up; echo -n ' '; done;
1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 43 44 45 46 47 48 49 50 51 52 53 54 55 56 57 58 59 60 61 62 63 64 65 66 67 68 69 70 71 72 73 74 75 76 77 78 79 80 81 82 83 84 85 86 87 88 89 90 91 92 93 94 95 96 97 98 99 100
user@host ~> for i in {1..25}; do curl http://localhost:8080/down; echo -n ' '; done;
99 98 97 96 95 94 93 92 91 90 89 88 87 86 85 84 83 82 81 80 79 78 77 76 75
```

We can see that the state of the counter is maintained between requests. In this example, we used the ZIO environment to store the access and store the state of the counter.


### 4. User App

The `UserApp()` is an Http app with the following definition:

```scala
object UserApp {
  def apply(): Http[UserRepo, Throwable, Request, Response] = ???
}
```

It requires a `UserRepo` service from the ZIO environment, it can fail with `Throwable` and it consumes a `Request` and produces a `Response` respectively. In this example, we use the in-memory version of the `UserRepo` service called `InmemoryUserRepo`.

This app has three endpoints:

```bash
POST http://localhost:8080/users -d '{"name": "John", "age": 30}'
GET  http://localhost:8080/users
GET  http://localhost:8080/users/:id
```

Let's try to register a new user:

```bash
user@host ~> curl -i http://localhost:8080/users -d '{"name": "John", "age": 35}'
HTTP/1.1 200 OK
content-type: text/plain
content-length: 36

f0f319ea-404d-4a55-abd0-41bee4ce887e
```

Now, we can get any registered user by its id:

```bash
user@host ~> curl -i http://localhost:8080/users/f0f319ea-404d-4a55-abd0-41bee4ce887e
HTTP/1.1 200 OK
content-type: application/json
content-length: 24

{"name":"John","age":35}
```

While this app is stateful, it is not persistent. We just provided the in-memory version of the `UserRepo` service called `InmemoryUserRepo`:

```scala
Server.serve(
  (GreetingApp() ++ DownloadApp() ++ CounterApp() ++ UserApp()).withDefaultErrorResponse
).provide(
  Server.defaultWithPort(8080),
  ZLayer.fromZIO(Ref.make(0)),
  InmemoryUserRepo.layer
)
```

To make it persistent, we can provide the `PersistentUserRepo` service instead:

```scala
Server.serve(
  (GreetingApp() ++ DownloadApp() ++ CounterApp() ++ UserApp()).withDefaultErrorResponse
).provide(
  Server.defaultWithPort(8080),
  ZLayer.fromZIO(Ref.make(0)),
  PersistentUserRepo.layer
)
```

Now, if we register a new user, the user will be persisted and if the application is restarted, the user will be available.
