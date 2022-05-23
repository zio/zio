---
id: restful-webservice
title: "ZIO Quickstart: Building RESTful Web Service"
sidebar_label: "RESTful Web Service"
---

This quickstart shows how to build a RESTful web service using ZIO. It uses
- [ZIO HTTP](https://dream11.github.io/zio-http/) for the HTTP server
- [ZIO JSON](https://zio.github.io/zio-json/) for the JSON serialization
- [ZIO Logging](https://zio.github.io/zio-logging/) for integrate logging with slf4j
- [ZIO Config](https://zio.github.io/zio-config/) for loading configuration data

## Running The Example

First, open the console and clone the project using `git` (or you can simply download the project) and then change the directory:

```scala
git clone git@github.com:khajavi/zio-quickstart-restful-webservice.git 
cd zio-quickstart-restful-webservice
```

Once you are inside the project directory, run the application:

```bash
sbt run
```

## Explanation

In this quickstart, we will build a RESTful web service that has the following Http apps:

- **Greeting App**— shows how to write a basic Http App.
- **Download App**— shows how to work with files, headers, and status codes and also streaming data.
- **Counter App**— shows how to have a stateful web service and how to use the ZIO environment for Http Apps.
- **User App**— shows how to have a stateful web service to register and manage users.

## Greeting App

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

