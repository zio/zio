# Endpoint (Module)

> `zio-blocks-endpoint` is a **pure, type-safe HTTP endpoint descriptor** for building clients, servers, and API documentation from a single source of truth. It provides composable types that describe every part of an HTTP surface — routes, query parameters, headers, request bodies, response bodies, error shapes, and authentication — without committing to any particular server or client implementation.

`zio-blocks-endpoint` is a **pure, type-safe HTTP endpoint descriptor** for building clients, servers, and API documentation from a single source of truth. It provides composable types that describe every part of an HTTP surface — routes, query parameters, headers, request bodies, response bodies, error shapes, and authentication — without committing to any particular server or client implementation.

Core types: `Endpoint`, `HttpCodec`, `RoutePattern`, `PathCodec`, `SegmentCodec`, `AuthType`, `RouteTree`. The top-level descriptor holds all of them together:

```scala
final case class Endpoint[PathInput, Input, Err, Output, Auth <: AuthType](
  route: RoutePattern[PathInput],
  input: HttpCodec[CodecKind.Request, Input],
  error: HttpCodec[CodecKind.Response, Err],
  output: HttpCodec[CodecKind.Response, Output],
  auth: Auth,
  doc: Doc
)
```

## Introduction

`zio-blocks-endpoint` separates the **description** of an HTTP surface from its **interpretation**. An `Endpoint` value is plain data — it can be handed to a ZIO HTTP server to generate routes, to a client generator to produce typed API calls, or to an OpenAPI renderer to produce specification documents. None of that interpretation code lives here; this module only describes what an endpoint looks like.

The DSL is designed to stay close to zio-http where that improves ergonomics, while adding precise types for error shapes, authentication, and content negotiation that zio-http does not encode directly.

## Motivation

Without a typed endpoint descriptor, HTTP surface definitions are scattered: routes in one place, request validation in another, error handling in a third. Adding a new endpoint means updating multiple layers by hand and hoping they stay consistent.

`zio-blocks-endpoint` solves this by encoding the full shape of an HTTP endpoint — including error variants, auth requirements, content types, and documentation — into a single composable value:

- **One source of truth**: change the endpoint descriptor and every interpreter (server, client, OpenAPI) updates automatically.
- **Type-safe error channels**: error types are encoded in the `Err` type parameter, not buried in `Either` chains or thrown exceptions.
- **Direction-checked codecs**: `HttpCodec[CodecKind.Request, A]` and `HttpCodec[CodecKind.Response, A]` are distinct types; the compiler prevents accidentally using a response codec where a request codec is expected.
- **Compile-time path validation**: path segment combinations (like `string ~ string`) that would be ambiguous to parse are rejected by the Scala 3 macro in `SegmentCodec` before the code compiles.

## Installation

The endpoint module is a cross-platform library (JVM + Scala.js). Add the dependency to your build definition:

**JVM (Scala 3.x):**
```scala
libraryDependencies += "dev.zio" %% "zio-blocks-endpoint" % "0.0.51"
```

**Scala.js (Scala 3.x):**
```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-endpoint" % "0.0.51"
```

**For Scala 3.7+**, the module name is rewritten to `zio-blocks-next-endpoint`:
```scala
libraryDependencies += "dev.zio" %% "zio-blocks-next-endpoint" % "0.0.51"  // JVM
libraryDependencies += "dev.zio" %%% "zio-blocks-next-endpoint" % "0.0.51" // Scala.js
```

Supported Scala versions: 3.x (Scala 3 only — the endpoint module uses Scala 3-only DSL and macro code).

## Overview

These seven types form the complete endpoint DSL:

**[Endpoint](./endpoint.md)** is the top-level descriptor. It holds a route, three codec channels (input, error, output), an auth type, and documentation. Endpoint is pure data with no server or client logic.

**[HttpCodec](./http-codec.md)** is a composable typed descriptor for HTTP request and response parts. Query parameters, headers, bodies, and status codes are all `HttpCodec` nodes, combined with `++` (sequential) or `|` (alternative).

**[RoutePattern](./route-pattern.md)** pairs an HTTP method with a typed path pattern. The primary syntax is `Method.GET / "users" / PathCodec.int("id")`.

**[PathCodec](./path-codec.md)** is a composable path descriptor. Segments are combined with `/`, and literal alternatives with `orElse`. It supports bidirectional path conversion via `decode` and `format`.

**[SegmentCodec](./segment-codec.md)** describes a single URL path segment. It supports typed segment kinds (`SegmentCodec.bool`, `SegmentCodec.int`, `SegmentCodec.long`, `SegmentCodec.string`, `SegmentCodec.uuid`) and intra-segment composition via `~`, with ambiguous combinations rejected at compile time.

**[AuthType](./auth-type.md)** describes an authentication scheme as a first-class type parameter. Built-in variants include `None`, `Basic`, `Bearer`, and `Digest`; custom schemes and `Or` combinations are also supported.

**[RouteTree](./route-tree.md)** is a routing trie keyed by HTTP method and path. The trie matches literals first, then dynamic segments in priority order. Server-side interpreters use it to build efficient route dispatch tables.

## How They Work Together

A typical endpoint definition flows like this:

```
1. Define a RoutePattern       Method.GET / "users" / PathCodec.int("id")
2. Create an Endpoint          Endpoint(route)
3. Describe request input      .query("verbose", Schema.boolean)
                               .header("X-Trace", Schema.string)
                               .in(Schema.string)
4. Describe success output     .out(Schema.string)
                               .out(Status.Created, Schema.int)
5. Describe error output       .outError(Status.NotFound, Schema.string)
                               .orOutError(Status.Conflict, Schema.int)   // Scala 3 unions
6. Set authentication          .auth(AuthType.Bearer)
7. Add documentation           .doc(Doc.paragraph("Returns a user by ID"))
```

The full type-level view:

```
RoutePattern[PathInput]
  └─ method: Method (GET, POST, ...)
  └─ pathCodec: PathCodec[PathInput]
       └─ Segment(SegmentCodec[A])  ──  literal / int / string / uuid / bool / long / trailing
       └─ Concat(left, right)       ──  left ++ right
       └─ Transform(codec, f, g)    ──  bidirectional type mapping

Endpoint[PathInput, Input, Err, Output, Auth]
  ├─ route:  RoutePattern[PathInput]
  ├─ input:  HttpCodec[Request,  Input]   ──  Query | Header | Body  (combined with ++)
  ├─ error:  HttpCodec[Response, Err]     ──  Body + Status          (alternatives with |)
  ├─ output: HttpCodec[Response, Output]  ──  Body + Status          (alternatives with |)
  └─ auth:   Auth <: AuthType             ──  None | Basic | Bearer | Digest | Custom | Or
```

The phantom type `CodecKind` (either `Request` or `Response`) on `HttpCodec` means the compiler rejects mixing the two directions, even before a server interprets the value.

## Common Patterns

Several composition patterns appear regularly when building endpoints.

**Single success response:** Use `Endpoint#out` for a 200 OK response with a body:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.Method

val getUser = Endpoint(Method.GET / "users" / PathCodec.int("id"))
  .out(Schema.string)
```

**Multiple success variants:** Chain additional `Endpoint#out` calls to add alternatives. The output type widens to an `Either`-based union:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val createOrUpdate = Endpoint(Method.POST / "users")
  .in(Schema.string)
  .out(Status.Created, Schema.int)
  .out(Status.Ok, Schema.string)
```

**Scala 3 union errors:** Use `Endpoint#orOutError` to accumulate error types as a Scala 3 union rather than nested `Either`s:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

val withUnionErrors = Endpoint(Method.GET / "users")
  .orOutError(Status.NotFound, Schema.string)
  .orOutError(Status.Conflict, Schema.int)

val typed: Endpoint[Unit, Unit, String | Int, Unit, AuthType.None.type] = withUnionErrors
```

**Path prefixing with `RoutePattern#nest`:** Use `RoutePattern#nest` to prepend a version prefix to an existing pattern without rewriting it:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val route  = Method.GET / "users" / PathCodec.int("id")
val versioned = route.nest(PathCodec("/api/v1"))
```

**Auth composition with `|`:** Combine auth types when an endpoint accepts multiple schemes:

```scala
import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.Method

val flexAuth = Endpoint(Method.GET / "me")
  .auth(AuthType.Basic | AuthType.Bearer)
```

## Integration Points

The endpoint types integrate with each other and with the broader ZIO Blocks ecosystem:

```
Endpoint
  ├─ uses RoutePattern      for routing lookup
  ├─ uses HttpCodec         for all three channels (input, error, output)
  ├─ uses AuthType          to carry the typed auth requirement
  └─ uses Doc               from zio-blocks-docs for API documentation

HttpCodec
  ├─ uses Schema            from zio-blocks-schema for body and header serialization
  ├─ uses MediaType         from zio-blocks-mediatype for content negotiation
  └─ uses Doc               for per-field documentation and examples

RoutePattern
  └─ uses PathCodec         for typed path composition

PathCodec
  └─ uses SegmentCodec      for individual segment descriptors

RouteTree (server-side only)
  └─ uses RoutePattern      to build the routing trie
  └─ uses SegmentSubtree    for per-level trie nodes
```

Cross-module: `zio-blocks-openapi` consumes `Endpoint` values to generate OpenAPI 3.1 specifications. `zio-blocks-schema` provides the `Schema[A]` instances that `HttpCodec.Body` uses for serialization.

## Running the Examples

All code from this section is available as runnable examples in the `endpoint-examples` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-blocks.git
cd zio-blocks
```

**2. Run individual examples with sbt:**

### Basic Endpoint Definition

Constructs `Endpoint` values from `RoutePattern` and chains request body, query parameters, headers, success outputs, response headers, and typed error variants using the builder DSL.

```scala title="endpoint-examples/src/main/scala/endpointexamples/BasicEndpointDefinition.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package endpointexamples

import scala.language.implicitConversions

import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

/**
 * Endpoint — Basic Endpoint Definition
 *
 * Demonstrates constructing an `Endpoint` from a `RoutePattern` and attaching
 * request body, query parameters, headers, success outputs, and error outputs
 * using the builder DSL.
 *
 * Run with: sbt "endpoint-examples/runMain
 * endpointexamples.BasicEndpointDefinition"
 */
@main def BasicEndpointDefinition(): Unit = {

  // Simplest endpoint: GET /health with a string response
  val health = Endpoint(Method.GET / "health")
    .out(Schema.string)

  println(s"Health route: ${health.route.render}")

  // POST with a typed request body and a 201 Created response
  val createUser = Endpoint(Method.POST / "users")
    .in(Schema.string)
    .out(Status.Created, Schema.int)

  println(s"Create user route: ${createUser.route.render}")

  // GET with query parameters and a request header
  val listUsers = Endpoint(Method.GET / "users")
    .query("page", Schema.int)
    .query("limit", Schema.int)
    .header("X-Trace-Id", Schema.string)
    .out(Schema.string)

  println(s"List users route: ${listUsers.route.render}")

  // GET with a dynamic path segment and typed error variants
  val getUser = Endpoint(Method.GET / "users" / PathCodec.int("id"))
    .out(Schema.string)
    .outError(Status.NotFound, Schema.string)
    .outError(Status.BadRequest, Schema.string)

  println(s"Get user route: ${getUser.route.render}")

  // Response header on the success channel
  val withRespHeader = Endpoint(Method.GET / "users")
    .out(Schema.string)
    .outHeader("X-Total-Count", Schema.int)

  println(s"With response header route: ${withRespHeader.route.render}")

  println("BasicEndpointDefinition complete")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/endpoint-examples/src/main/scala/endpointexamples/BasicEndpointDefinition.scala))

```bash
sbt "endpoint-examples/runMain endpointexamples.BasicEndpointDefinition"
```

### HttpCodec Smart Constructors and Composition

Builds `HttpCodec` atoms for query parameters, request headers, response headers, request bodies, response bodies, and status codes. Shows sequential composition with `++` and alternative composition with `|`.

```scala title="endpoint-examples/src/main/scala/endpointexamples/HttpCodecConstruction.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package endpointexamples

import zio.blocks.chunk.Chunk
import zio.blocks.docs.Doc
import zio.blocks.endpoint._
import zio.blocks.mediatype.MediaTypes
import zio.blocks.schema.Schema

/**
 * HttpCodec — Smart Constructors and Composition
 *
 * Demonstrates building `HttpCodec` atoms (query parameters, headers, bodies,
 * status codes) and composing them with `++` (sequential) and `|`
 * (alternative).
 *
 * Run with: sbt "endpoint-examples/runMain
 * endpointexamples.HttpCodecConstruction"
 */
@main def HttpCodecConstruction(): Unit = {

  // --- Smart constructors ---

  // Query parameter with an optional default value
  val pageCodec  = HttpCodec.query("page", Schema.int, default = Some(1))
  val limitCodec = HttpCodec.query("limit", Schema.int)

  println(s"Query 'page'  default: ${pageCodec.default}")
  println(s"Query 'limit' default: ${limitCodec.default}")

  // Request header by name and schema
  val traceHeader = HttpCodec.requestHeader("X-Trace-Id", Schema.string)
  println(s"Request header name: ${traceHeader.name}")

  // Response header
  val totalCount = HttpCodec.responseHeader("X-Total-Count", Schema.int)
  println(s"Response header name: ${totalCount.name}")

  // Request body restricted to JSON
  val jsonBody =
    HttpCodec.requestBody(Schema.string, mediaTypes = Chunk.single(MediaTypes.application.`json`))
  println(s"Request body codec node: ${jsonBody.getClass.getSimpleName}")

  // Response body
  val respBody = HttpCodec.responseBody(Schema.string)
  println(s"Response body codec node: ${respBody.getClass.getSimpleName}")

  // Status code atoms — predefined constants
  println(s"Ok status:       ${HttpCodec.Ok}")
  println(s"Created status:  ${HttpCodec.Created}")
  println(s"NotFound status: ${HttpCodec.NotFound}")

  // --- Sequential composition with ++ ---
  // Combines two request-side codecs into a single codec whose value is a tuple
  val nameAndAgeQuery: HttpCodec[CodecKind.Request, (String, Int)] =
    HttpCodec.query("name", Schema.string) ++ HttpCodec.query("age", Schema.int)

  println(s"Sequential codec: ${nameAndAgeQuery.getClass.getSimpleName}")

  // --- Alternative composition with | ---
  // Builds a fallback: try the left codec, then the right
  val okOrCreated =
    (HttpCodec.responseBody(Schema.string) ++ HttpCodec.Ok) |
      (HttpCodec.responseBody(Schema.int) ++ HttpCodec.Created)

  println(s"Alternative codec: ${okOrCreated.getClass.getSimpleName}")

  // --- Metadata: doc, examples, default ---
  val richQuery = HttpCodec.query(
    name = "limit",
    schema = Schema.int,
    default = Some(20),
    doc = Doc.empty,
    examples = Chunk("default" -> 20, "max" -> 100)
  )
  println(s"Rich query default: ${richQuery.default}")
  println(s"Rich query examples count: ${richQuery.examples.length}")

  // --- Auth codecs ---
  val bearerCodec = HttpCodec.bearerAuth
  val basicCodec  = HttpCodec.basicAuth
  val digestCodec = HttpCodec.digestAuth
  println(s"Bearer codec: ${bearerCodec.getClass.getSimpleName}")
  println(s"Basic codec:  ${basicCodec.getClass.getSimpleName}")
  println(s"Digest codec: ${digestCodec.getClass.getSimpleName}")

  println("HttpCodecConstruction complete")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/endpoint-examples/src/main/scala/endpointexamples/HttpCodecConstruction.scala))

```bash
sbt "endpoint-examples/runMain endpointexamples.HttpCodecConstruction"
```

### PathCodec and SegmentCodec

Demonstrates all `SegmentCodec` kinds, intra-segment composition with `~` for patterns like `v42`, bidirectional `PathCodec` decode and format, `RoutePattern` matching, `nest` for version prefixes, and `transform`/`transformOrFail` for domain type mapping.

```scala title="endpoint-examples/src/main/scala/endpointexamples/PathAndSegmentCodecs.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package endpointexamples

import scala.language.implicitConversions

import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.http.{Method, Path}

/**
 * PathCodec and SegmentCodec — Typed Path Construction and Decoding
 *
 * Demonstrates `SegmentCodec` kinds, intra-segment composition with `~`,
 * `PathCodec` construction, bidirectional decode/format, `RoutePattern`
 * matching, nesting, and type transformations.
 *
 * Run with: sbt "endpoint-examples/runMain
 * endpointexamples.PathAndSegmentCodecs"
 */
@main def PathAndSegmentCodecs(): Unit = {

  // --- SegmentCodec kinds ---
  val intSeg   = SegmentCodec.int("id")
  val strSeg   = SegmentCodec.string("slug")
  val uuidSeg  = SegmentCodec.uuid("id")
  val boolSeg  = SegmentCodec.bool("flag")
  val longSeg  = SegmentCodec.long("id")
  val trailing = SegmentCodec.Trailing

  println(s"Segment kinds: int=${intSeg.render()}, string=${strSeg.render()}, uuid=${uuidSeg.render()}")
  println(s"  bool=${boolSeg.render()}, long=${longSeg.render()}, trailing=${trailing.render()}")

  // Intra-segment composition: single path segment containing a literal prefix and an integer
  // "v42" decodes to 42 and formats 42 back to "v42"
  val versionSeg: SegmentCodec[Int] =
    SegmentCodec.literal("v") ~ SegmentCodec.int("major")

  println(s"Version segment renders as: ${versionSeg.render()}")
  val formattedVersion: Path = versionSeg.format(3)
  println(s"Version 3 formats to: $formattedVersion")

  // --- PathCodec construction ---
  val usersPath   = PathCodec.literal("users") / PathCodec.int("id")
  val apiPath     = PathCodec("/api/v1/users")
  val versionPath = PathCodec(versionSeg)
  println(s"versionPath renders as: ${versionPath.render}")

  println(s"usersPath renders as: ${usersPath.render}")
  println(s"apiPath renders as:   ${apiPath.render}")

  // Bidirectional: decode a Path to a typed value
  val decoded: Either[String, Int] = PathCodec.int("id").decode(Path("/42"))
  println(s"Decoded /42: $decoded")

  // Bidirectional: format a typed value back to a Path
  val uuidValue = java.util.UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
  val formatted = PathCodec.uuid("id").format(uuidValue)
  println(s"Formatted UUID: $formatted")

  // Literal alternatives: match either /users or /members
  val eitherPath: PathCodec[Unit] =
    PathCodec.literal("users").orElse(PathCodec.literal("members"))

  println(s"orElse matches /users: ${eitherPath.matches(Path("/users"))}")
  println(s"orElse matches /members: ${eitherPath.matches(Path("/members"))}")

  // --- RoutePattern construction and operations ---
  val route = Method.GET / "users" / PathCodec.int("id")

  // Decode: extract a typed value from a method + path
  val routeDecoded: Either[String, Int] = route.decode(Method.GET, Path("/users/42"))
  println(s"Route decoded: $routeDecoded")

  // Encode: rebuild (Method, Path) from a typed value
  val routeEncoded: Either[String, (Method, Path)] = route.encode(42)
  println(s"Route encoded: $routeEncoded")

  // Render: human-readable string matching OpenAPI path parameter convention
  println(s"Route rendered: ${route.render}")

  // Nest: prepend a version prefix to an existing pattern
  val versioned = route.nest(PathCodec("/api/v1"))
  println(s"Versioned route: ${versioned.render}")

  // --- Type transformations ---
  final case class UserId(value: Int)

  val userIdCodec: PathCodec[UserId] =
    PathCodec.int("id").transform[UserId](UserId(_), _.value)

  val decodedUserId = userIdCodec.decode(Path("/99"))
  println(s"UserId decoded: $decodedUserId")

  // transformOrFail: reject non-positive segment values at parse time
  val positiveInt: PathCodec[Int] =
    PathCodec
      .int("count")
      .transformOrFail[Int](
        n => if (n > 0) Right(n) else Left(s"Expected positive, got $n"),
        n => Right(n)
      )

  println(s"Positive decode 5:  ${positiveInt.decode(Path("/5"))}")
  println(s"Positive decode -1: ${positiveInt.decode(Path("/-1"))}")

  println("PathAndSegmentCodecs complete")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/endpoint-examples/src/main/scala/endpointexamples/PathAndSegmentCodecs.scala))

```bash
sbt "endpoint-examples/runMain endpointexamples.PathAndSegmentCodecs"
```

### AuthType Patterns

Shows all built-in `AuthType` variants (None, Basic, Bearer, Digest), a custom API-key variant, OR composition with `|`, scoped bearer tokens for OAuth scope metadata, and overriding the default unauthorized status.

```scala title="endpoint-examples/src/main/scala/endpointexamples/AuthTypePatterns.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package endpointexamples

import scala.language.implicitConversions

import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Status}

/**
 * AuthType — Authentication Scheme Variants and Composition
 *
 * Demonstrates all built-in `AuthType` variants (None, Basic, Bearer, Digest),
 * the Custom variant for API keys, OR composition with `|`, scoped bearer
 * tokens, and overriding the default unauthorized status.
 *
 * Run with: sbt "endpoint-examples/runMain endpointexamples.AuthTypePatterns"
 */
@main def AuthTypePatterns(): Unit = {

  // --- AuthType.None (default) ---
  // No authentication required; every new Endpoint starts with None
  val publicEndpoint = Endpoint(Method.GET / "health")
    .out(Schema.string)

  println(s"Public endpoint auth: ${publicEndpoint.auth}")

  // --- AuthType.Basic ---
  val basicEndpoint = Endpoint(Method.GET / "admin")
    .auth(AuthType.Basic)

  println(s"Basic auth unauth status: ${basicEndpoint.auth.unauthorizedStatus}")

  // --- AuthType.Bearer ---
  val bearerEndpoint = Endpoint(Method.GET / "me")
    .auth(AuthType.Bearer)

  println(s"Bearer auth unauth status: ${bearerEndpoint.auth.unauthorizedStatus}")

  // --- AuthType.Digest ---
  val digestEndpoint = Endpoint(Method.GET / "secure")
    .auth(AuthType.Digest)

  println(s"Digest auth unauth status: ${digestEndpoint.auth.unauthorizedStatus}")

  // --- AuthType.Custom ---
  // Wrap any HttpCodec for schemes not covered by the built-in variants
  val apiKeyCodec = HttpCodec.requestHeader("X-Api-Key", Schema.string)
  val apiKeyAuth  = AuthType.Custom(apiKeyCodec)
  val keyEndpoint = Endpoint(Method.GET / "data").auth(apiKeyAuth)

  println(s"Custom auth unauth status: ${keyEndpoint.auth.unauthorizedStatus}")

  // --- OR composition: accept either scheme ---
  // The codec tries the left scheme first and falls back to the right
  val flexEndpoint = Endpoint(Method.GET / "resource")
    .auth(AuthType.Basic | AuthType.Bearer)

  println(s"Flex auth unauth status: ${flexEndpoint.auth.unauthorizedStatus}")

  // --- Scoped bearer: attach OAuth scope metadata ---
  val scopedEndpoint = Endpoint(Method.GET / "admin")
    .auth(AuthType.Scoped(AuthType.Bearer, List("admin:read", "admin:write")))

  println(s"Scoped auth unauth status: ${scopedEndpoint.auth.unauthorizedStatus}")

  // --- Override the default unauthorized status ---
  // Default is Status.NotFound (to avoid leaking endpoint existence);
  // override to Status.Unauthorized when endpoint existence is public knowledge
  val strictEndpoint = Endpoint(Method.GET / "me")
    .auth(AuthType.Bearer)
    .unauthorizedStatus(Status.Unauthorized)

  println(s"Strict unauth status: ${strictEndpoint.auth.unauthorizedStatus}")

  println("AuthTypePatterns complete")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/endpoint-examples/src/main/scala/endpointexamples/AuthTypePatterns.scala))

```bash
sbt "endpoint-examples/runMain endpointexamples.AuthTypePatterns"
```

### Complete REST API

Assembles a full users CRUD API combining `Endpoint`, `RoutePattern`, `PathCodec`, `SegmentCodec`, `HttpCodec`, `AuthType`, and `RouteTree`. Demonstrates versioned routes via `nest`, Scala 3 union error types via `orOutError`, and `RouteTree` lookup priority for efficient O(depth) dispatch.

```scala title="endpoint-examples/src/main/scala/endpointexamples/CompleteApiDefinition.scala" 
/*
 * Copyright 2024-2026 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package endpointexamples

import scala.language.implicitConversions

import zio.blocks.endpoint._
import zio.blocks.endpoint.RoutePattern._
import zio.blocks.schema.Schema
import zio.http.{Method, Path, Status}

/**
 * Complete REST API — All Endpoint Types Working Together
 *
 * Shows a full users CRUD API built from `Endpoint`, `RoutePattern`,
 * `PathCodec`, `SegmentCodec`, `HttpCodec`, `AuthType`, and `RouteTree`.
 * Demonstrates versioned routes via `nest`, Scala 3 union error types via
 * `orOutError`, and `RouteTree` lookup priority.
 *
 * Run with: sbt "endpoint-examples/runMain
 * endpointexamples.CompleteApiDefinition"
 */
@main def CompleteApiDefinition(): Unit = {

  // --- Domain type with a typed path codec ---
  final case class UserId(value: Int)

  val userIdPath: PathCodec[UserId] =
    PathCodec.int("id").transform[UserId](UserId(_), _.value)

  // --- Endpoint definitions ---

  // GET /users?page=&limit=  — paginated list, bearer-secured
  val listUsers = Endpoint(Method.GET / "users")
    .query("page", Schema.int)
    .query("limit", Schema.int)
    .out(Schema.string)
    .outError(Status.BadRequest, Schema.string)
    .auth(AuthType.Bearer)

  // GET /users/{id}  — fetch a single user, bearer-secured
  val getUser = Endpoint(Method.GET / "users" / userIdPath)
    .out(Schema.string)
    .outError(Status.NotFound, Schema.string)
    .auth(AuthType.Bearer)

  // POST /users  — create a user, 201 on success, bearer-secured
  val createUser = Endpoint(Method.POST / "users")
    .in(Schema.string)
    .out(Status.Created, Schema.int)
    .outError(Status.BadRequest, Schema.string)
    .outError(Status.Conflict, Schema.string)
    .auth(AuthType.Bearer)

  // DELETE /users/{id}  — remove a user, bearer-secured
  val deleteUser = Endpoint(Method.DELETE / "users" / PathCodec.int("id"))
    .out(Status.NoContent, Schema.unit)
    .outError(Status.NotFound, Schema.string)
    .auth(AuthType.Bearer)

  // GET /health  — public health check, no auth required
  val health = Endpoint(Method.GET / "health")
    .out(Schema.string)

  // --- Union error types (Scala 3 only) ---
  // orOutError accumulates error types as a native union instead of nested Eithers;
  // the first call sets Err directly, subsequent calls widen to a union
  val withUnionErrors = Endpoint(Method.GET / "items" / PathCodec.int("id"))
    .orOutError(Status.NotFound, Schema.string)
    .orOutError(Status.Conflict, Schema.int)

  val _: Endpoint[Int, Unit, String | Int, Unit, AuthType.None.type] = withUnionErrors

  // --- Versioned routes via nest ---
  val v1ListUsers = listUsers.route.nest(PathCodec("/api/v1"))
  val v2ListUsers = listUsers.route.nest(PathCodec("/api/v2"))

  println("Endpoint routes:")
  println(s"  ${listUsers.route.render}")
  println(s"  ${getUser.route.render}")
  println(s"  ${createUser.route.render}")
  println(s"  ${deleteUser.route.render}")
  println(s"  ${health.route.render}")
  println(s"  v1: ${v1ListUsers.render}")
  println(s"  v2: ${v2ListUsers.render}")

  // --- RouteTree: O(depth) routing trie ---
  // Literals are matched first; dynamic segments follow priority ordering
  // (int > long > uuid > bool > string > combined > trailing)
  val tree = RouteTree
    .empty[String]
    .add(Method.GET / "users", "list-users")
    .add(Method.GET / "users" / PathCodec.int("id"), "get-user")
    .add(Method.POST / "users", "create-user")
    .add(Method.DELETE / "users" / PathCodec.int("id"), "delete-user")
    .add(Method.GET / "health", "health")

  println("\nRouteTree lookups:")
  println(s"  GET    /users       → ${tree.get(Method.GET, Path("/users"))}")
  println(s"  GET    /users/42    → ${tree.get(Method.GET, Path("/users/42"))}")
  println(s"  POST   /users       → ${tree.get(Method.POST, Path("/users"))}")
  println(s"  DELETE /users/7     → ${tree.get(Method.DELETE, Path("/users/7"))}")
  println(s"  GET    /health      → ${tree.get(Method.GET, Path("/health"))}")
  // HEAD falls back to GET per HTTP spec
  println(s"  HEAD   /users       → ${tree.get(Method.HEAD, Path("/users"))}")
  // Unregistered path returns None
  println(s"  GET    /notfound    → ${tree.get(Method.GET, Path("/notfound"))}")

  // --- RouteTree merge: right-hand side wins on conflict ---
  val treeA  = RouteTree.empty[String].add(Method.GET / "users", "users-v1")
  val treeB  = RouteTree.empty[String].add(Method.GET / "users", "users-v2")
  val merged = treeA.merge(treeB)
  println(s"\nMerged GET /users → ${merged.get(Method.GET, Path("/users"))}")

  // --- RoutePattern decode and encode ---
  val route   = Method.GET / "users" / PathCodec.int("id")
  val decoded = route.decode(Method.GET, Path("/users/99"))
  val encoded = route.encode(99)
  println(s"\nRoute decode /users/99 → $decoded")
  println(s"Route encode 99        → $encoded")

  println("\nCompleteApiDefinition complete")
}
```

([source](https://github.com/zio/zio-blocks/blob/main/endpoint-examples/src/main/scala/endpointexamples/CompleteApiDefinition.scala))

```bash
sbt "endpoint-examples/runMain endpointexamples.CompleteApiDefinition"
```

**3. Or compile all examples at once:**

```bash
sbt "endpoint-examples/compile"
```
