# AttributeKey

> Type-safe key for Attributes storage. Binds a string name to a specific value type A (String, Boolean, Long, Double, and their Seq variants).

`AttributeKey[A]` is the type-safe key type for reading and writing entries in an `Attributes` collection. It binds a string name to a specific value type `A` via an `AttributeType` discriminator, so `Attributes#get[A](key)` returns `Option[A]` without casting. There are eight supported value types, each with its own factory method on the companion object.

```scala
sealed trait AttributeKey[A] {
  def name: String           // the string label used as the key
  def `type`: AttributeType  // discriminator: StringType, LongType, DoubleType, BooleanType, + 4 Seq variants
}

object AttributeKey {
  // Scalar factory methods
  def string(name: String):     AttributeKey[String]
  def boolean(name: String):    AttributeKey[Boolean]
  def long(name: String):       AttributeKey[Long]
  def double(name: String):     AttributeKey[Double]

  // Sequence factory methods
  def stringSeq(name: String):  AttributeKey[Seq[String]]
  def longSeq(name: String):    AttributeKey[Seq[Long]]
  def doubleSeq(name: String):  AttributeKey[Seq[Double]]
  def booleanSeq(name: String): AttributeKey[Seq[Boolean]]
}
```

## Usage

```scala
import zio.blocks.telemetry._

// Typed factory methods
val envKey      = AttributeKey.string("environment")
val retryKey    = AttributeKey.long("retry.count")
val debugKey    = AttributeKey.boolean("debug.enabled")
val latencyKey  = AttributeKey.double("latency.ms")
val tagsKey     = AttributeKey.stringSeq("tags")

// Build an Attributes collection using typed keys
val attrs = Attributes.builder
  .put(envKey, "production")
  .put(retryKey, 3L)
  .put(debugKey, false)
  .put(latencyKey, 12.5)
  .put(tagsKey, Seq("important", "v2"))
  .build
```

## Passing to Span and Metric APIs

`AttributeKey` is used directly in `Span#setAttribute` and `Attributes.of`:

```scala
import zio.blocks.telemetry._

val provider = TracerProvider.builder.build()
val tracer   = provider.get("com.example")

val dbSystem = AttributeKey.string("db.system")
val rowCount = AttributeKey.long("db.rows")

tracer.span("db.query", SpanKind.Client) { span =>
  span.setAttribute(dbSystem, "postgresql")
  span.setAttribute(rowCount, 42L)
}

provider.shutdown()
```

## Predefined Constants

`Attributes.ServiceName` and `Attributes.ServiceVersion` are predefined `AttributeKey[String]` constants for the standard OTel `"service.name"` and `"service.version"` attribute names. Use them instead of string literals to avoid typos:

```scala
import zio.blocks.telemetry._

val attrs = Attributes.of(Attributes.ServiceName, "order-service")
assert(attrs.get(Attributes.ServiceName) == Some("order-service"))
```
