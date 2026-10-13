# JWT

> `zio-blocks-jwt` is a zero-dependency, cross-platform (JVM + Scala.js) JWT library for Scala 2.13 and Scala 3.

## Getting Started

Add the dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-blocks-jwt" % "<version>"
```

Install a platform backend **once** at application startup:

```scala
import zio.blocks.jwt._
JvmJwtCryptoBackend.install()
```

For Scala.js (Node.js) use `JsJwtCryptoBackend.install()` instead (available on the JS platform with `jwtJS`).

## Signing

```scala
import zio.blocks.jwt._
val key    = "0123456789ABCDEF0123456789ABCDEF".getBytes("UTF-8") // 32 bytes for HS256 (JWA minimum)
val claims = JwtClaims(sub = Some("user-123"), iss = Some("my-app"))
val token: Either[JwtError, String] = Jwt.sign(claims, key, Algorithm.HS256)
```

## Decoding and Verifying

```scala
import zio.blocks.jwt._
val key    = "0123456789ABCDEF0123456789ABCDEF".getBytes("UTF-8")
val claims = JwtClaims(sub = Some("user-123"), iss = Some("my-app"))
val token  = Jwt.sign(claims, key, Algorithm.HS256).getOrElse("")
val result: Either[JwtError, JwtClaims] =
  Jwt.decode(token, key, Algorithm.HS256, clockSkewSeconds = 30L, issuer = Some("my-app"))
```

## Claims

`JwtClaims` models the RFC 7519 registered claims plus arbitrary extra claims:

| Field   | Type                                  | RFC claim |
|---------|---------------------------------------|-----------|
| `iss`   | `Option[String]`                      | Issuer    |
| `sub`   | `Option[String]`                      | Subject   |
| `aud`   | `Option[JwtAudience]`                 | Audience  |
| `exp`   | `Option[Long]`                        | Expiration (Unix seconds) |
| `nbf`   | `Option[Long]`                        | Not Before (Unix seconds) |
| `iat`   | `Option[Long]`                        | Issued At (Unix seconds)  |
| `jti`   | `Option[String]`                      | JWT ID    |
| `extra` | `Map[String, JwtValue]`                 | Custom claims, preserving JSON type |

`exp`/`nbf`/`iat` are `NumericDate` per RFC 7519 §2: JSON numbers (integer, fractional, exponent) truncated toward zero to seconds, rejected if negative, non-finite, or out of `Long` range.

### Extra claims

`JwtValue` is the public JSON ADT for `extra` (and for `JwtClaims` round-trip):

```scala
import zio.blocks.jwt._
import zio.blocks.chunk.Chunk
val claims = JwtClaims(
  sub   = Some("user-123"),
  extra = Map(
    "role"   -> JwtValue.Str("admin"),
    "level"  -> JwtValue.Num("3"),
    "active" -> JwtValue.Bool(true),
    "meta"   -> JwtValue.Null,
    "tags"   -> JwtValue.Arr(Chunk(JwtValue.Str("a"), JwtValue.Num("1"))),
    "profile"-> JwtValue.Obj(Map("age" -> JwtValue.Num("30"), "nested" -> JwtValue.Obj(Map("k" -> JwtValue.Str("v")))))
  )
)
```

Objects and arrays nest arbitrarily and are preserved exactly; no values are dropped during parsing.

### Audience

Per RFC 7519 §4.1.3, `aud` may be a single string or an array. `None` and `JwtValue.Null` are treated as absent. Arrays must contain only strings; mixed or non-string elements are rejected with `InvalidToken`:

```scala
import zio.blocks.jwt._
import zio.blocks.chunk.Chunk
val c1 = JwtClaims(aud = Some(JwtAudience.Single("my-service")))
val c2 = JwtClaims(aud = Some(JwtAudience.Multiple(Chunk("service-a", "service-b"))))
val c3 = JwtClaims(aud = None) // absent
```

When `expectedAudience` is set in `JwtDecodeOptions`, `aud` is validated (must be present and contain the expected value; missing yields `MissingClaim("aud")`, mismatch yields `InvalidToken`); otherwise `aud` is only parsed.

### Limits and Options

Resource limits are conservative and checked before allocation:

```scala
import zio.blocks.jwt._
val limits = JwtLimits(maxTokenChars = 8192, maxSegmentChars = 4096, maxJsonChars = 8192, maxDepth = 32, maxFields = 512, maxArrayElements = 512)
val decodeOpts = JwtDecodeOptions(
  issuer = Some("my-app"),
  expectedAudience = Some("my-service"),
  clockSkewSeconds = 30L,
  limits = limits,
  nowSeconds = Some(1700000000L)
)
val signOpts = JwtSignOptions(limits = limits)
val key = "0123456789ABCDEF0123456789ABCDEF".getBytes("UTF-8")
val claims = JwtClaims(sub = Some("user-123"))
val token = Jwt.sign(claims, key, Algorithm.HS256, signOpts).getOrElse("")
Jwt.sign(claims, key, Algorithm.HS256, signOpts)
Jwt.decode(token, key, Algorithm.HS256, decodeOpts)
```

`JwtHeader` also carries `kid` and `typ` (default `"JWT"`):

```scala
import zio.blocks.jwt._
val hdr = JwtHeader(Algorithm.HS256, typ = "JWT", kid = Some("key-1"))
val key = "0123456789ABCDEF0123456789ABCDEF".getBytes("UTF-8")
val claims = JwtClaims(sub = Some("user-123"))
Jwt.sign(claims, key, Algorithm.HS256, hdr)
```

## Supported Algorithms and Key Strength

| Algorithm | JVM | JS (Node.js) | Pure Scala | Key minima / curve |
|-----------|-----|--------------|-----------|--------------------|
| HS256     | ✓   | ✓            | ✓         | 32 bytes |
| HS384     | ✓   | ✓            | ✓         | 48 bytes |
| HS512     | ✓   | ✓            | ✓         | 64 bytes |
| RS256     | ✓   | ✓            | —         | RSA ≥2048 bits |
| RS384     | ✓   | ✓            | —         | RSA ≥2048 bits |
| RS512     | ✓   | ✓            | —         | RSA ≥2048 bits |
| PS256     | ✓   | —            | —         | RSA ≥2048 bits, PSS |
| PS384     | ✓   | —            | —         | RSA ≥2048 bits, PSS |
| PS512     | ✓   | —            | —         | RSA ≥2048 bits, PSS |
| ES256     | ✓   | ✓            | —         | P-256 (`prime256v1`) |
| ES384     | ✓   | ✓            | —         | P-384 (`secp384r1`) |
| ES512     | ✓   | ✓            | —         | P-521 (`secp521r1`, 66-byte components) |
| EdDSA     | ✓   | —            | —         | Ed25519 |

Keys shorter than the HWA minima are rejected with `InvalidKey` (not `UnsupportedAlgorithm` or `InvalidToken`). RSA &lt;2048 bits and EC curve mismatches (e.g. `ES256` with `secp384r1`) are also rejected with `InvalidKey`.

Query what a backend supports at runtime:

```scala
import zio.blocks.jwt._
JvmJwtCryptoBackend.supportedAlgorithms // Set[Algorithm]
// res7: Set[Algorithm] = Set(
//   RS256,
//   PS384,
//   RS512,
//   PS512,
//   HS384,
//   ES256,
//   PS256,
//   EdDSA,
//   ES512,
//   HS512,
//   RS384,
//   ES384,
//   HS256
// )
```

`UnsupportedAlgorithm` is returned when the backend does not support the algorithm: `PS*`/`EdDSA` on JS (Node), any asymmetric alg when `JsCryptoCapability` reports unavailable (browser/ESM without Node `crypto`), or an unknown `alg` header (`FOO`).

Browser/ESM without Node `crypto` (detected via `JsCryptoCapability.isAvailable`) returns `UnsupportedAlgorithm` for `RS*`/`ES*`; HMAC via `SharedJwtCryptoBackend` still works.

## Error Handling

All errors are represented as `JwtError` subtypes (no stack traces). Limits and validation map to distinct cases:

| Error                | Meaning                                      |
|----------------------|----------------------------------------------|
| `InvalidToken`       | Malformed structure, bad claim type, `aud` mixed, `iss`/`sub` not string, `exp` not number, etc. |
| `ExpiredToken`       | `exp` is in the past (with `clockSkewSeconds` and `nowSeconds`) |
| `NotYetValid`        | `nbf` is in the future                       |
| `InvalidSignature`   | Signature did not verify (after `alg` check, before claims parsing) |
| `UnsupportedAlgorithm` | Backend does not support the algorithm or `alg` header is `FOO` |
| `MissingClaim`       | Required claim absent (`alg`, or `iss` when `issuer` expected) |
| `AlgorithmMismatch`  | `alg` header differs from requested algorithm (`header.alg != alg` on `sign` or `decode`) |
| `TokenTooLarge`      | Compact token > `maxTokenChars` |
| `SegmentTooLarge`    | A segment > `maxSegmentChars` |
| `JsonTooLarge`       | Decoded JSON > `maxJsonChars` |
| `TooDeep`            | JSON depth > `maxDepth` |
| `TooManyFields`      | Object fields > `maxFields` |
| `TooManyElements`    | Array elements > `maxArrayElements` |

## Base64URL Encoding

`Base64Url` is a public object (exposed for testing, but considered internal API) that encodes/decodes with no padding (`=`), per RFC 7515 §2 and RFC 4648 §5. Tokens containing `=` are rejected with `InvalidToken`; length `mod 4 == 1` and non-Base64Url chars (`+`, `/`, `!`) are rejected; trailing bits for 2- or 3-char tails must be zero (e.g. `AB` / `ABC` are rejected).
