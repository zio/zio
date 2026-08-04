# HxEncoding

> `HxEncoding` represents the `hx-encoding` attribute, controlling how form data is encoded when sent in an HTMX request. The primary use case is file uploads, which require `multipart/form-data` encoding instead of the default URL-encoded form submission.

`HxEncoding` represents the `hx-encoding` attribute, controlling how form data is encoded when sent in an HTMX request. The primary use case is file uploads, which require `multipart/form-data` encoding instead of the default URL-encoded form submission.

Use `HxEncoding.Multipart` to enable multipart form data encoding for requests that include file fields. Here is the core pattern:

```scala
import zio.http.htmx._

// Multipart encoding (for file uploads)
HxEncoding.Multipart
```

## Encoding Strategies

**`HxEncoding.Multipart`** sets the `hx-encoding` attribute to `multipart/form-data`, enabling file uploads and other multipart data:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/upload",
  hxEncoding := HxEncoding.Multipart,
  input(name := "file", `type` := "file"),
  input(name := "description"),
  button("Upload")
)
```

When you omit `hxEncoding`, HTMX uses the default encoding (URL-encoded form data), so there is no DSL value for the default case. This keeps the DSL focused on the special cases that deviate from the default.

## Common Patterns

Multipart encoding enables file uploads in HTMX forms. Here are practical usage patterns:

### File Upload with Metadata

Send a file along with additional form fields:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/api/upload-document",
  hxEncoding := HxEncoding.Multipart,
  hxTarget := HxTarget.css("#upload-status"),
  hxSwap := HxSwap.InnerHTML,
  fieldset(
    legend("Upload Document"),
    input(
      name := "file",
      `type` := "file",
      accept := ".pdf,.doc,.docx"
    ),
    input(
      name := "title",
      placeholder := "Document Title"
    ),
    textarea(name := "description", "Description"),
    button("Upload")
  )
)
```

### Profile Picture Upload

Update a profile picture with multipart encoding:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/api/profile-picture",
  hxEncoding := HxEncoding.Multipart,
  hxTarget := HxTarget.css("#profile-pic"),
  hxSwap := HxSwap.OuterHTML,
  input(
    name := "image",
    `type` := "file",
    accept := "image/*"
  ),
  button("Update Picture")
)
```

## Integration with Other Request Control Attributes

`HxEncoding` works alongside `HxParams` to control both how data is encoded and which fields are sent:

```scala
import zio.blocks.html._
import zio.http.htmx._

form(
  hxPost := "/upload",
  hxEncoding := HxEncoding.Multipart,
  hxParams := HxParams.only("file", "title"),  // only send these fields
  input(name := "file", `type` := "file"),
  input(name := "title"),
  input(name := "csrf_token"),  // excluded by hxParams
  button("Upload")
)
```

The `ToHtmxValue[HxEncoding]` instance renders automatically, so `HxEncoding` values work seamlessly with the `hxEncoding` attribute key.
