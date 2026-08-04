# HTTP Model

> `zio-http-model` is a **pure, zero-dependency HTTP data model** for building HTTP clients and servers. It provides immutable types representing all HTTP concepts: requests, responses, headers, URLs, paths, query parameters, methods, status codes, versions, cookies, and forms. The module separates protocol concerns (representing HTTP messages) from effect concerns (actually sending/receiving them), enabling portable, testable HTTP code across any Scala application.

# HTTP Model

`zio-http-model` is a **pure, zero-dependency HTTP data model** for building HTTP clients and servers. It provides immutable types representing all HTTP concepts: requests, responses, headers, URLs, paths, query parameters, methods, status codes, versions, cookies, and forms. The module separates protocol concerns (representing HTTP messages) from effect concerns (actually sending/receiving them), enabling portable, testable HTTP code across any Scala application.

## Core Capabilities

The HTTP model is organized into two complementary modules:

### [HTTP Model](./model.md) — Pure Data Types

The foundation of the HTTP model: immutable data types representing HTTP requests, responses, and all associated primitives. No effects, no I/O, no coupling to specific runtimes.

**Key types:** `Request`, `Response`, `URL`, `Headers`, `Body`, `Method`, `Status`, `Version`, `Scheme`, `Path`, `QueryParams`, `ContentType`, `RequestCookie`, `ResponseCookie`, `Form`.

**Use this when:** Building request/response data structures, parsing HTTP primitives, serializing messages for storage or caching, or sharing HTTP types across effect systems.

### [Schema-Based Typed Access](./schema.md) — Type-Safe Extraction

Extension methods that add **type-safe, validated extraction** of query parameters and headers to the core HTTP model. Automatically decode string values to typed objects using schema-based decoding with comprehensive error reporting.

**Key features:** Typed `QueryParams#query[T]`, `Headers#header[T]`, `Request#query[T]` methods with schema-based decoding, automatic error reporting for missing or malformed values, support for 11 primitive types and custom types via `Schema[T]`.

**Use this when:** You need to extract and validate query parameters or headers with type safety, decode values to domain types, or provide clear error messages for malformed input.

## How They Work Together

1. **Build pure HTTP data** using the core HTTP model types
2. **Add type-safe extraction** with schema-based extension methods
3. **Hand off to an HTTP client/server library** (ZIO HTTP, Akka, Play, etc.) for the actual I/O work

This separation keeps your domain logic portable and testable while maintaining full expressiveness for HTTP interactions.

## Getting Started

Start with the [HTTP Model](./model.md) to understand the core data types and how they compose. Then explore [Schema-Based Typed Access](./schema.md) to learn how to safely extract and validate HTTP parameters and headers.

---

**Modules:**
- [`zio-http-model`](./model.md) — Core immutable data types for HTTP
- [`zio-http-model-schema`](./schema.md) — Schema-based typed extraction for query parameters and headers
