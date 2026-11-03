# Tapir

> [Tapir](https://tapir.softwaremill.com) is a library to describe HTTP APIs, expose them as a server, consume them as a client, and automatically document them using open standards. It has first-class ZIO support through the `tapir-zio` and `tapir-zio-http-server` modules.

## Introduction

Key features of Tapir:
- **Type Safety** — Compile-time guarantees, develop-time completions, and read-time information.
- **Declarative** — Separate the shape of an endpoint (the "what") from the server logic (the "how").
- **Documentation** — Generate OpenAPI, AsyncAPI, and JSON Schema documentation from endpoint descriptions.
- **Observability** — Use the endpoint metadata to report rich metrics and tracing information.
- **Library, Not a Framework** — Tapir integrates with the stack we already use, so we can expose Tapir endpoints alongside native ones.

## Installation

The `tapir-zio` module defines the `ZServerEndpoint` type alias and the helpers to work with ZIO:

```scala
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-zio" % "1.14.1"
```

To expose the endpoints using the ZIO HTTP server, add the ZIO HTTP interpreter instead, which already depends on `tapir-zio`:

```scala
libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server" % "1.14.1"
```

Tapir also provides ZIO-based integrations for other servers, JSON libraries, and observability tools. See the [Tapir documentation](https://tapir.softwaremill.com) for the complete list.

## Example

Instead of the usual `import sttp.tapir.*`, we import `sttp.tapir.ztapir.*`. It brings into scope all the input and output descriptions, and replaces the standard server logic methods with `zServerLogic` and `zServerSecurityLogic`, which accept ZIO effects:

```scala
import sttp.tapir.PublicEndpoint
import sttp.tapir.ztapir.*
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import zio.http.{Response, Routes}
import zio.*

def countCharacters(s: String): ZIO[Any, Nothing, Int] =
  ZIO.succeed(s.length)

val countCharactersEndpoint: PublicEndpoint[String, Unit, Int, Any] =
  endpoint.in(stringBody).out(plainBody[Int])

val countCharactersHttp: Routes[Any, Response] =
  ZioHttpInterpreter().toHttp(countCharactersEndpoint.zServerLogic(countCharacters))
```

The resulting `Routes` can be served like any other ZIO HTTP routes, and a single application can contain both Tapir-generated and native ZIO HTTP routes.

The ZIO HTTP interpreter also supports streaming request and response bodies using `ZStream`, and web sockets.
