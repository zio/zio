# OpenAPI

> `zio-blocks-openapi` is a **complete, type-safe OpenAPI 3.1 data model** for building API documentation programmatically. It provides immutable case classes and sealed traits representing every OpenAPI concept—operations, parameters, security schemes, and components—enabling you to construct OpenAPI documents in compile-time-safe Scala and export them as JSON for consumption by tools like Swagger UI, Redoc, and API validators.

Core types: `OpenAPI`, `Info`, `Paths`, `PathItem`, `Operation`, `Parameter`, `RequestBody`, `Response`, `Components`, `SchemaObject`, `SecurityScheme`, `ReferenceOr`.

```scala
final case class OpenAPI(
  openapi: String,
  info: Info,
  servers: Option[Chunk[Server]] = None,
  paths: Option[Paths] = None,
  components: Option[Components] = None,
  security: Option[Chunk[SecurityRequirement]] = None
)
```

## Introduction

OpenAPI documents are the **lingua franca for API specifications**. They define request/response contracts, authentication methods, and data schemas in a standardized JSON or YAML format that external tools consume. Building these documents manually in JSON is error-prone; maintaining them as your API evolves is tedious.

The OpenAPI module bridges the gap by letting you author API specs as Scala code—leveraging the type system for compile-time correctness—then export to standard JSON that any OpenAPI tool understands. You get type safety during authoring plus interoperability with the entire OpenAPI ecosystem.

## Installation

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-openapi" % "0.0.51"

// You'll also need the schema module for Schema[A] integration:
libraryDependencies += "dev.zio" %% "zio-blocks-schema" % "0.0.51"
```

For Scala.js:

```scala
libraryDependencies += "dev.zio" %%% "zio-blocks-openapi" % "0.0.51"
```

Supported Scala versions: 2.13.x and 3.x.

## How They Work Together

The OpenAPI module follows a clear workflow:

**1. Define your data types** using ZIO Blocks `Schema`:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._

import zio.blocks.schema._

case class User(id: Int, name: String, email: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

case class ErrorResponse(code: Int, message: String)
object ErrorResponse {
  implicit val schema: Schema[ErrorResponse] = Schema.derived
}
```

**2. Create an OpenAPI document** by composing types:

```scala
val api = OpenAPI(
  openapi = "3.1.0",
  info = Info(
    title = "User API",
    version = "1.0.0",
    description = Some(md"API for managing users")
  ),
  paths = Some(Paths(ChunkMap(
    "/users" -> PathItem(
      get = Some(Operation(
        summary = Some(md"List all users"),
        description = Some(md"Returns a paginated list of users"),
        responses = Responses(ChunkMap(
          "200" -> ReferenceOr.Value(Response(
            description = md"Successful response",
            content = ChunkMap(
              "application/json" -> MediaType(
                schema = Some(ReferenceOr.Value(
                  Schema[List[User]].toOpenAPISchema
                ))
              )
            )
          ))
        ))
      ))
    ),
    "/users/{id}" -> PathItem(
      get = Some(Operation(
        summary = Some(md"Get a user by ID"),
        parameters = Chunk(
          ReferenceOr.Value(Parameter(
            name = "id",
            in = ParameterLocation.Path,
            required = true,
            schema = Some(ReferenceOr.Value(
              Schema[Int].toOpenAPISchema
            ))
          ))
        ),
        responses = Responses(ChunkMap(
          "200" -> ReferenceOr.Value(Response(
            description = md"User found",
            content = ChunkMap(
              "application/json" -> MediaType(
                schema = Some(ReferenceOr.Value(
                  Schema[User].toOpenAPISchema
                ))
              )
            )
          )),
          "404" -> ReferenceOr.Value(Response(
            description = md"User not found",
            content = ChunkMap(
              "application/json" -> MediaType(
                schema = Some(ReferenceOr.Value(
                  Schema[ErrorResponse].toOpenAPISchema
                ))
              )
            )
          ))
        ))
      ))
    )
  ))),
  components = Some(Components(
    schemas = ChunkMap(
      Schema[User].toRefSchema._2._1 -> ReferenceOr.Value(Schema[User].toRefSchema._2._2),
      Schema[ErrorResponse].toRefSchema._2._1 -> ReferenceOr.Value(Schema[ErrorResponse].toRefSchema._2._2)
    )
  ))
)
```

**3. Serialize to JSON** for tools to consume:

```scala
import zio.blocks.openapi.OpenAPICodec._

val json = openAPICodec.encodeValue(api)
```

**4. Render or serve** the JSON (e.g., to Swagger UI):

```scala
import zio.blocks.schema.json._

val jsonString = Json.jsonCodec.encodeToString(json, WriterConfig.withIndentionStep2)
// jsonString: String = """{
//   "openapi": "3.1.0",
//   "info": {
//     "title": "User API",
//     "version": "1.0.0",
//     "description": "API for managing users\n\n"
//   },
//   "paths": {
//     "/users": {
//       "get": {
//         "responses": {
//           "200": {
//             "description": "Successful response\n\n",
//             "content": {
//               "application/json": {
//                 "schema": {
//                   "items": {
//                     "properties": {
//                       "id": {
//                         "type": "integer",
//                         "maximum": 2147483647,
//                         "minimum": -2147483648
//                       },
//                       "name": {
//                         "type": "string"
//                       },
//                       "email": {
//                         "type": "string"
//                       }
//                     },
//                     "type": "object",
//                     "required": [
//                       "id",
//                       "name",
//                       "email"
//                     ],
//                     "title": "User"
//                   },
//                   "type": [
//                     "array",
//                     "null"
//                   ],
//                   "title": "List[User]"
//                 }
//               }
//             }
//           }
//         },
//         "summary": "List all users\n\n",
// ...
```

### Type Relationships Diagram

```
OpenAPI (root document)
├─ info: Info (metadata)
├─ servers: Option[Chunk[Server]]
├─ paths: Option[Paths] (map of path strings to PathItem)
│  └─ PathItem
│     ├─ get: Operation
│     ├─ post: Operation
│     ├─ put: Operation
│     └─ ... (other HTTP methods)
│        ├─ parameters: Chunk[ReferenceOr[Parameter]]
│        ├─ requestBody: ReferenceOr[RequestBody]
│        │  └─ content: Map[String, MediaType]
│        │     └─ schema: ReferenceOr[SchemaObject]
│        └─ responses: Responses
│           └─ Map[statusCode, ReferenceOr[Response]]
│              └─ content: Map[String, MediaType]
│                 └─ schema: ReferenceOr[SchemaObject]
├─ components: Option[Components]
│  ├─ schemas: ChunkMap[String, ReferenceOr[SchemaObject]]
│  ├─ responses: ChunkMap[String, ReferenceOr[Response]]
│  ├─ parameters: ChunkMap[String, ReferenceOr[Parameter]]
│  └─ securitySchemes: ChunkMap[String, ReferenceOr[SecurityScheme]]
└─ security: Option[Chunk[SecurityRequirement]]
```

## Common Patterns

### Building Reusable Schema Components

Avoid duplicating schema definitions by moving them to `components.schemas`:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

case class User(id: Int, name: String, email: String)
object User {
  implicit val schema: Schema[User] = Schema.derived
}

val userSchemaComponent = Schema[User].toRefSchema
// Returns: (ReferenceOr.Ref(...), ("User", SchemaObject(...)))
// Use the ref in operations, store the component in components.schemas
```

### Using `ReferenceOr` for Inline vs. Referenced Schemas

`ReferenceOr[A]` is a sealed trait with two cases:

- **`ReferenceOr.Ref`**: Points to a schema in `#/components/schemas/<name>`
- **`ReferenceOr.Value`**: Inline schema definition

Prefer `Ref` for reusable schemas; use `Value` for simple, one-off schemas:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

// Reusable: use Ref
val userRef = ReferenceOr.Ref(Reference(`$ref` = "#/components/schemas/User"))

// One-off: use Value
val simpleString = ReferenceOr.Value(Schema[String].toOpenAPISchema)
```

### Security Schemes

Define authentication methods in `components.securitySchemes`:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val apiKeyScheme = SecurityScheme.APIKey(
  name = "X-API-Key",
  in = APIKeyLocation.Header,
  description = Some(md"API key for authentication")
)

val oauthScheme = SecurityScheme.OAuth2(
  flows = OAuthFlows(
    authorizationCode = Some(OAuthFlow(
      authorizationUrl = Some("https://example.com/oauth/authorize"),
      tokenUrl = Some("https://example.com/oauth/token"),
      scopes = ChunkMap("read" -> "Read access", "write" -> "Write access")
    ))
  ),
  description = Some(md"OAuth 2.0 authorization")
)

val components = Components(
  securitySchemes = ChunkMap(
    "api_key" -> ReferenceOr.Value(apiKeyScheme),
    "oauth2" -> ReferenceOr.Value(oauthScheme)
  )
)
```

### Path Parameters vs. Query Parameters

Distinguish parameter locations using `ParameterLocation`:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val pathParam = Parameter(
  name = "id",
  in = ParameterLocation.Path,
  required = true,
  schema = Some(ReferenceOr.Value(Schema[Int].toOpenAPISchema))
)

val queryParam = Parameter(
  name = "limit",
  in = ParameterLocation.Query,
  required = false,
  schema = Some(ReferenceOr.Value(Schema[Int].toOpenAPISchema))
)

val headerParam = Parameter(
  name = "X-Custom-Header",
  in = ParameterLocation.Header,
  required = false,
  schema = Some(ReferenceOr.Value(Schema[String].toOpenAPISchema))
)
```

## Integration Points

The OpenAPI module integrates tightly with other ZIO Blocks components:

- **Schema Integration**: All OpenAPI types have `Schema.derived` instances, enabling round-trip serialization via `DynamicValue`. Use `Schema[A].toOpenAPISchema` to convert any schema to an OpenAPI component.
- **Markdown Support**: Description fields use the in-house `Doc` type, which supports CommonMark rendering. This ensures markdown descriptions round-trip correctly.
- **JSON AST**: All codecs operate on the `Json` AST from `zio-blocks-schema`, not external JSON libraries. To render as YAML, pipe the `Json` through `zio-blocks-schema-yaml` separately.

---



`OpenAPI` is the root document object representing a complete OpenAPI 3.1 specification.

### Definition

Every OpenAPI document requires:
- **`openapi`**: Version string (typically `"3.1.0"`)
- **`info`**: Metadata about the API (`Info`)

Optional top-level fields include:
- **`servers`**: Server definitions for the API (`Chunk[Server]`)
- **`paths`**: Map of endpoint paths to operations (`Paths`)
- **`components`**: Reusable schemas, responses, parameters, and other components (`Components`)
- **`security`**: Security requirements applied to the API (`Chunk[SecurityRequirement]`)

Response definitions are modeled per `Operation`, not as a top-level field on `OpenAPI`.

### Creating an OpenAPI Document

To construct an `OpenAPI` document:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val minimalApi = OpenAPI(
  openapi = "3.1.0",
  info = Info(title = "My API", version = "1.0.0")
)
```

Add paths, operations, and components as shown in the "How They Work Together" section above.

### Serialization

Encode an `OpenAPI` document to `Json` AST:

```scala
import zio.blocks.openapi._
import zio.blocks.openapi.OpenAPICodec._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

import zio.blocks.schema.json._

val myApi = OpenAPI(openapi = "3.1.0", info = Info(title = "My API", version = "1.0.0"))
val encoded: Json = openAPICodec.encodeValue(myApi)
```

Decode from `Json` AST back to an `OpenAPI` instance:

```scala
import zio.blocks.openapi._
import zio.blocks.openapi.OpenAPICodec._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

import zio.blocks.schema.json._

val myApi = OpenAPI(openapi = "3.1.0", info = Info(title = "My API", version = "1.0.0"))
val encoded: Json = openAPICodec.encodeValue(myApi)
val decoded: OpenAPI = openAPICodec.decodeValue(encoded)
```

---

## Info

`Info` contains metadata about the API: title, version, contact, and license.

### Definition

Required fields:
- **`title`**: API name (e.g., `"User API"`)
- **`version`**: API version (e.g., `"1.0.0"`)

Optional fields:
- **`description`**: Markdown-formatted description (`Doc`)
- **`termsOfService`**: Terms of service URL
- **`contact`**: Contact information (`Contact`)
- **`license`**: License information (`License`)

### Creating Info

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val info = Info(
  title = "Pet Store API",
  version = "3.0.0",
  description = Some(md"API for managing a pet store"),
  contact = Some(Contact(
    name = Some("API Support"),
    url = Some("https://example.com/support"),
    email = Some("support@example.com")
  )),
  license = Some(License(
    name = "Apache 2.0",
    identifier = Some("Apache-2.0")
  ))
)
```

---

## Paths & PathItem

`Paths` represents the collection of URL paths and their operations. `PathItem` groups HTTP methods (GET, POST, PUT, etc.) on a single path.

### Definition

`Paths` is a wrapper case class with two fields:
- **`paths`**: `ChunkMap[String, PathItem]`, where keys are path strings (e.g., `"/users/{id}"`)
- **`extensions`**: `ChunkMap[String, Json]`, for OpenAPI specification extensions

`PathItem` contains optional fields for each HTTP method:
- **`get`, `post`, `put`, `delete`, `patch`, `head`, `options`, `trace`**: `Operation` instances
- **`parameters`**: Path-level parameters shared by all methods on this path
- **`servers`**: Optional server overrides for this path

### Creating Path Items

To define a path with multiple operations:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val userPaths = Paths(ChunkMap(
  "/users" -> PathItem(
    get = Some(Operation(
      summary = Some(md"List users"),
      responses = Responses(ChunkMap(
        "200" -> ReferenceOr.Value(Response(
          description = md"User list",
          content = ChunkMap(
            "application/json" -> MediaType(schema = None)
          )
        ))
      ))
    )),
    post = Some(Operation(
      summary = Some(md"Create user"),
      requestBody = Some(ReferenceOr.Value(RequestBody(
        description = Some(md"User data"),
        content = ChunkMap(
          "application/json" -> MediaType(schema = None)
        ),
        required = true
      ))),
      responses = Responses(ChunkMap(
        "201" -> ReferenceOr.Value(Response(
          description = md"User created",
          content = ChunkMap(
            "application/json" -> MediaType(schema = None)
          )
        ))
      ))
    ))
  ),
  "/users/{id}" -> PathItem(
    parameters = Chunk(
      ReferenceOr.Value(Parameter(
        name = "id",
        in = ParameterLocation.Path,
        required = true,
        schema = Some(ReferenceOr.Value(Schema[String].toOpenAPISchema))
      ))
    ),
    get = Some(Operation(
      summary = Some(md"Get user by ID"),
      responses = Responses(ChunkMap(
        "200" -> ReferenceOr.Value(Response(
          description = md"User found",
          content = ChunkMap(
            "application/json" -> MediaType(schema = None)
          )
        )),
        "404" -> ReferenceOr.Value(Response(
          description = md"User not found",
          content = ChunkMap(
            "application/json" -> MediaType(schema = None)
          )
        ))
      ))
    ))
  )
))
```

---

## Operation

`Operation` represents a single HTTP operation (GET, POST, etc.) on a path.

### Definition

Key fields:
- **`responses`**: Required. Map of status codes to response definitions
- **`operationId`**: Unique operation identifier
- **`summary`**: Short description
- **`description`**: Detailed markdown description
- **`parameters`**: Path, query, header, and cookie parameters
- **`requestBody`**: Request payload definition
- **`deprecated`**: Whether the operation is deprecated
- **`tags`**: Group operations in documentation (e.g., `"users"`, `"products"`)

### Defining an Operation

With summary, description, and parameters:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val getUser = Operation(
  tags = Chunk("users"),
  summary = Some(md"Retrieve user"),
  description = Some(md"Fetches a single user by ID"),
  operationId = Some("getUserById"),
  parameters = Chunk(
    ReferenceOr.Value(Parameter(
      name = "id",
      in = ParameterLocation.Path,
      required = true,
      schema = Some(ReferenceOr.Value(Schema[String].toOpenAPISchema)),
      description = Some(md"User ID")
    ))
  ),
  responses = Responses(ChunkMap(
    "200" -> ReferenceOr.Value(Response(
      description = md"User found",
      content = ChunkMap(
        "application/json" -> MediaType(schema = None)
      )
    )),
    "404" -> ReferenceOr.Value(Response(
      description = md"User not found",
      content = ChunkMap(
        "application/json" -> MediaType(schema = None)
      )
    ))
  ))
)
```

---

## Parameter

`Parameter` represents query, path, header, or cookie parameters in a request.

### Definition

Required fields:
- **`name`**: Parameter name (e.g., `"id"`, `"limit"`)
- **`in`**: Location—`Path`, `Query`, `Header`, or `Cookie` (`ParameterLocation`)
- **`schema`**: Data type of the parameter (`ReferenceOr[SchemaObject]`)

Optional fields:
- **`description`**: Markdown description
- **`required`**: Whether the parameter is mandatory (default: `false`)
- **`deprecated`**: Whether the parameter is deprecated
- **`allowEmptyValue`**: Whether empty string values are allowed

### Creating Parameters

Path parameter (required):

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val idPathParam = Parameter(
  name = "id",
  in = ParameterLocation.Path,
  required = true,
  schema = Some(ReferenceOr.Value(Schema[String].toOpenAPISchema)),
  description = Some(md"User identifier")
)
```

Query parameter (optional with default):

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val limitQueryParam = Parameter(
  name = "limit",
  in = ParameterLocation.Query,
  required = false,
  schema = Some(ReferenceOr.Value(Schema[Int].toOpenAPISchema)),
  description = Some(md"Maximum number of results (default: 20)")
)
```

Header parameter:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val authHeaderParam = Parameter(
  name = "X-API-Key",
  in = ParameterLocation.Header,
  required = true,
  schema = Some(ReferenceOr.Value(Schema[String].toOpenAPISchema)),
  description = Some(md"API key for authentication")
)
```

---

## RequestBody & Response

`RequestBody` defines the structure of a request payload. `Response` defines the structure and status of a response.

### RequestBody Definition

Key fields:
- **`content`**: Map of MIME types to `MediaType` definitions
- **`description`**: Optional markdown description
- **`required`**: Whether the request body is mandatory (default: `false`)

### Creating a RequestBody

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._
import zio.blocks.schema.json._

case class User(name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val createUserBody = RequestBody(
  description = Some(md"User data to create"),
  content = ChunkMap(
    "application/json" -> MediaType(
      schema = Some(ReferenceOr.Value(Schema[User].toOpenAPISchema)),
      example = Some(Json.Object(Chunk(
        "name" -> Json.String("John Doe"),
        "email" -> Json.String("john@example.com")
      )))
    )
  ),
  required = true
)
```

### Response Definition

Key fields:
- **`description`**: Required. Markdown description of the response
- **`content`**: Map of MIME types to `MediaType` definitions
- **`headers`**: Optional response headers
- **`links`**: Optional links to related operations

### Creating a Response

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

case class User2(id: Int, name: String, email: String)
object User2 { implicit val schema: Schema[User2] = Schema.derived }
case class ErrorResponse2(code: Int, message: String)
object ErrorResponse2 { implicit val schema: Schema[ErrorResponse2] = Schema.derived }

val successResponse = Response(
  description = md"User successfully created",
  content = ChunkMap(
    "application/json" -> MediaType(
      schema = Some(ReferenceOr.Value(Schema[User2].toOpenAPISchema))
    )
  )
)

val errorResponse = Response(
  description = md"Request validation failed",
  content = ChunkMap(
    "application/json" -> MediaType(
      schema = Some(ReferenceOr.Value(Schema[ErrorResponse2].toOpenAPISchema))
    )
  )
)
```

### Responses

`Responses` is a map of HTTP status codes to `ReferenceOr[Response]`:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val ok = Response(description = md"Created", content = ChunkMap())
val err = Response(description = md"Bad request", content = ChunkMap())

val responses = Responses(ChunkMap(
  "201" -> ReferenceOr.Value(ok),
  "400" -> ReferenceOr.Value(err),
  "401" -> ReferenceOr.Value(Response(
    description = md"Unauthorized",
    content = ChunkMap()
  )),
  "500" -> ReferenceOr.Value(Response(
    description = md"Internal server error",
    content = ChunkMap()
  ))
))
```

---

## MediaType

`MediaType` specifies the schema and encoding for a particular MIME type in a request or response.

### Definition

Key fields:
- **`schema`**: Data type for this MIME type (`ReferenceOr[SchemaObject]`)
- **`example`**: Example value as `Json`
- **`encoding`**: Encoding rules for multipart form data
- **`extensions`**: Custom vendor extensions (`x-*` fields)

### Creating MediaType

With schema and example:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._
import zio.blocks.schema.json._

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val jsonMedia = MediaType(
  schema = Some(ReferenceOr.Value(Schema[User].toOpenAPISchema)),
  example = Some(Json.Object(
    "id" -> Json.Number(1),
    "name" -> Json.String("Alice"),
    "email" -> Json.String("alice@example.com")
  ))
)
```

For form data:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val formMedia = MediaType(
  schema = Some(ReferenceOr.Value(Schema[Map[String, String]].toOpenAPISchema)),
  encoding = ChunkMap(
    "file" -> Encoding(
      contentType = Some("application/octet-stream")
    )
  )
)
```

---

## Components

`Components` stores reusable schema and security definitions referenced throughout the document.

### Definition

Key fields:
- **`schemas`**: Reusable schema objects (`ChunkMap[String, ReferenceOr[SchemaObject]]`)
- **`responses`**: Reusable response definitions
- **`parameters`**: Reusable parameter definitions
- **`securitySchemes`**: Authentication method definitions
- **`examples`**, **`requestBodies`**, **`headers`**, **`links`**, **`callbacks`**: Additional reusable components

### Creating Components

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }
case class ErrorResponse(code: Int, message: String)
object ErrorResponse { implicit val schema: Schema[ErrorResponse] = Schema.derived }

val components = Components(
  schemas = ChunkMap(
    Schema[User].toRefSchema._2._1 -> ReferenceOr.Value(Schema[User].toRefSchema._2._2),
    Schema[ErrorResponse].toRefSchema._2._1 -> ReferenceOr.Value(Schema[ErrorResponse].toRefSchema._2._2)
  ),
  parameters = ChunkMap(
    "id" -> ReferenceOr.Value(Parameter(
      name = "id",
      in = ParameterLocation.Path,
      required = true,
      schema = Some(ReferenceOr.Value(Schema[String].toOpenAPISchema))
    ))
  ),
  responses = ChunkMap(
    "NotFound" -> ReferenceOr.Value(Response(
      description = md"Resource not found",
      content = ChunkMap(
        "application/json" -> MediaType(
          schema = Some(ReferenceOr.Value(
            Schema[ErrorResponse].toOpenAPISchema
          ))
        )
      )
    )),
    "Unauthorized" -> ReferenceOr.Value(Response(
      description = md"Unauthorized access",
      content = ChunkMap()
    ))
  ),
  securitySchemes = ChunkMap(
    "api_key" -> ReferenceOr.Value(SecurityScheme.APIKey(
      name = "X-API-Key",
      in = APIKeyLocation.Header,
      description = Some(md"API key header")
    ))
  )
)
```

---

## SchemaObject

`SchemaObject` wraps a JSON Schema 2020-12 definition with OpenAPI-specific extensions like discriminator, XML metadata, and examples.

### Definition

`SchemaObject` contains:
- **`jsonSchema`**: Raw JSON Schema 2020-12 as `Json` AST
- **`discriminator`**: Polymorphism discriminator for oneOf/anyOf
- **`xml`**: XML serialization metadata
- **`example`**: Example value for documentation
- **`extensions`**: Custom `x-*` fields

### Creating SchemaObject

Directly from a `Schema[A]`:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val userSchema = Schema[User].toOpenAPISchema
// Returns a SchemaObject with the User type's JSON Schema
```

Or with additional OpenAPI metadata:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._
import zio.blocks.schema.json._

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val enrichedSchema = SchemaObject(
  jsonSchema = Schema[User].toJsonSchema.toJson,
  discriminator = None,
  xml = Some(XML(
    name = Some("user"),
    namespace = None,
    prefix = None,
    attribute = false,
    wrapped = false
  )),
  example = Some(Json.Object(
    "id" -> Json.Number(1),
    "name" -> Json.String("John")
  )),
  extensions = ChunkMap(
    "x-generated" -> Json.String("true"),
    "x-version" -> Json.String("1.0.0")
  )
)
```

### Converting Schemas to SchemaObject

Use the `SchemaOps` extension methods on any `Schema[A]`:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val schemaObj: SchemaObject = Schema[User].toOpenAPISchema
val (ref, component) = Schema[User].toRefSchema
// ref = ReferenceOr.Ref pointing to #/components/schemas/User
// component = ("User", SchemaObject(...))
```

---

## ReferenceOr

`ReferenceOr[A]` is a sealed trait representing the OpenAPI pattern of choosing between a `$ref` and an inline value.

### Definition

Two cases:
- **`ReferenceOr.Ref`**: Points to a definition at `#/components/<type>/<name>`
- **`ReferenceOr.Value`**: Inline definition without reference

### Using ReferenceOr

Prefer `Ref` for reusable components:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val userRef = ReferenceOr.Ref(Reference(`$ref` = "#/components/schemas/User"))

val responseRef = ReferenceOr.Ref(Reference(
  `$ref` = "#/components/responses/NotFound"
))
```

Use `Value` for inline, one-off definitions:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

case class User(id: Int, name: String, email: String)
object User { implicit val schema: Schema[User] = Schema.derived }

val inlineUser = ReferenceOr.Value(Schema[User].toOpenAPISchema)

val inlineError = ReferenceOr.Value(Response(
  description = md"Quick error",
  content = ChunkMap()
))
```

### Pattern Matching on ReferenceOr

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

def describeRef[A](ref: ReferenceOr[A]): String = ref match {
  case ReferenceOr.Ref(r)   => s"Reference to ${r.`$ref`}"
  case ReferenceOr.Value(_) => "Inline value"
}
```

---

## SecurityScheme

`SecurityScheme` is a sealed trait representing different authentication methods. Variants include API Key, HTTP Basic/Bearer, OAuth 2.0, OpenID Connect, and Mutual TLS.

### Definition

Sealed trait variants:
- **`APIKey`**: API key in header, query, or cookie
- **`HTTP`**: HTTP authentication (Basic, Bearer, etc.)
- **`OAuth2`**: OAuth 2.0 authorization flows
- **`OpenIdConnect`**: OpenID Connect discovery
- **`MutualTLS`**: Mutual TLS certificate-based

### Creating Security Schemes

API Key authentication:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val apiKeySecurity = SecurityScheme.APIKey(
  name = "X-API-Key",
  in = APIKeyLocation.Header,
  description = Some(md"API key required in header")
)
```

HTTP Bearer token:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val bearerSecurity = SecurityScheme.HTTP(
  scheme = "bearer",
  bearerFormat = Some("JWT"),
  description = Some(md"JWT bearer token")
)
```

OAuth 2.0:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val oauthSecurity = SecurityScheme.OAuth2(
  flows = OAuthFlows(
    authorizationCode = Some(OAuthFlow(
      authorizationUrl = Some("https://example.com/oauth/authorize"),
      tokenUrl = Some("https://example.com/oauth/token"),
      scopes = ChunkMap(
        "read:users" -> "Read user data",
        "write:users" -> "Modify user data"
      )
    ))
  ),
  description = Some(md"OAuth 2.0 authorization")
)
```

:::note
The `OAuthFlows` type supports multiple flow types: `implicit`, `password`, `clientCredentials`, and `authorizationCode`. Choose the flow that matches your OAuth 2.0 configuration.
:::

OpenID Connect:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val oidcSecurity = SecurityScheme.OpenIdConnect(
  openIdConnectUrl = "https://example.com/.well-known/openid-configuration",
  description = Some(md"OpenID Connect discovery")
)
```

---

## Discriminator

`Discriminator` specifies how to distinguish between different variants in a polymorphic schema (using `oneOf` or `anyOf`).

### Definition

Key fields:
- **`propertyName`**: Field name used to discriminate (e.g., `"type"`, `"kind"`)
- **`mapping`**: Optional explicit mapping of discriminator values to schema references

### Creating a Discriminator

Simple discriminator by property name:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val discriminator = Discriminator(propertyName = "type")
```

With explicit value-to-schema mapping:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val mappedDiscriminator = Discriminator(
  propertyName = "kind",
  mapping = ChunkMap(
    "user" -> "#/components/schemas/User",
    "admin" -> "#/components/schemas/Admin",
    "guest" -> "#/components/schemas/Guest"
  )
)
```

---

## Server & ServerVariable

`Server` specifies base URLs and server-specific variables. `ServerVariable` allows parameterization of server URLs.

### Definition

`Server` contains:
- **`url`**: Server URL (may contain variable placeholders like `{base_path}`)
- **`description`**: Optional description
- **`variables`**: Map of variable names to `ServerVariable`

`ServerVariable` contains:
- **`enum`**: List of allowed values
- **`default`**: Default value
- **`description`**: Description of the variable

### Creating Servers

Single static server:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val server = Server(
  url = "https://api.example.com",
  description = Some(md"Production API")
)
```

Server with variables:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val variableServer = Server(
  url = "https://{host}:{port}/{basePath}",
  description = Some(md"Development API with variables"),
  variables = ChunkMap(
    "host" -> ServerVariable(
      default = "localhost",
      `enum` = Chunk("localhost", "staging.example.com", "api.example.com"),
      description = Some(md"API host")
    ),
    "port" -> ServerVariable(
      default = "8080",
      `enum` = Chunk("8080", "443"),
      description = Some(md"Port number")
    ),
    "basePath" -> ServerVariable(
      default = "v1",
      `enum` = Chunk("v1", "v2"),
      description = Some(md"API version path")
    )
  )
)
```

:::note
`ServerVariable` contains enumerable values for each variable. The field is named to avoid the reserved `enum` keyword in Scala.
:::

---

## Tag

`Tag` groups related operations under a heading in generated documentation.

### Definition

Key fields:
- **`name`**: Tag identifier (e.g., `"users"`, `"products"`)
- **`description`**: Markdown description of the tag
- **`externalDocs`**: Link to external documentation

### Creating Tags

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val userTag = Tag(
  name = "users",
  description = Some(md"User management operations")
)

val productsTag = Tag(
  name = "products",
  description = Some(md"Product catalog operations"),
  externalDocs = Some(ExternalDocumentation(
    url = "https://docs.example.com/products",
    description = Some(md"Full product API documentation")
  ))
)
```

---

## Common Extension Fields

All types support custom `x-*` extension fields for vendor-specific metadata. These extensions are preserved during encoding/decoding:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._
import zio.blocks.schema.json._

val operationWithExtensions = Operation(
  summary = Some(md"Get user"),
  responses = Responses(ChunkMap(
    "200" -> ReferenceOr.Value(Response(
      description = md"User found",
      content = ChunkMap()
    ))
  )),
  extensions = ChunkMap(
    "x-internal" -> Json.Boolean(true),
    "x-rate-limit" -> Json.Number(100),
    "x-deprecated-at" -> Json.String("2024-01-01")
  )
)
```

---

## Round-Tripping with Schema

All OpenAPI types have `Schema.derived` instances, enabling serialization through `DynamicValue`:

```scala
import zio.blocks.openapi._
import zio.blocks.docs._
import zio.blocks.chunk._
import zio.blocks.schema._

val myApi = OpenAPI(openapi = "3.1.0", info = Info(title = "My API", version = "1.0.0"))
val openAPISchema = Schema[OpenAPI]

val apiDynamic = openAPISchema.toDynamicValue(myApi)

val apiRestored = openAPISchema.fromDynamicValue(apiDynamic)
```

This enables integration with other ZIO Blocks modules that work with `DynamicValue`.
