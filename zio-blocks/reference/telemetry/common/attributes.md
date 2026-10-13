# Attributes

> Typed key-value metadata attached to spans, log records, and measurements — built once, read by key, and cheap enough for a hot path.

`Attributes` is the metadata carried alongside a signal — the `http.route` on a span, the `orderId` on a log record, the `method=GET` on a measurement. It's a set of key-value pairs, and every signal the telemetry module emits has one.

It exists because a signal without detail can't answer questions. "A request was slow" is useless; "a `GET` on `/orders` that returned `503` was slow" is something you can search for, filter on, and group by. Attributes are what carry that detail into your backend.

Two properties shape the type. Values are reached through a typed [`AttributeKey`](./attribute-key.md), so reading one gives you back a `String` or a `Long` rather than something you must cast. And because a set is built on every span and every measurement — the hottest paths in an instrumented application — primitives are stored without boxing, and a finished set is immutable, so it can be shared rather than copied.

## Building a Set

Three ways in, depending on how many pairs you have:

```scala
import zio.blocks.telemetry._

val none = Attributes.empty

val one = Attributes.of(Attributes.ServiceName, "payments")

val several = Attributes.builder
  .put(AttributeKey.string("http.route"), "/orders")
  .put("http.status_code", 200L)
  .put("cache.hit", false)
  .build
```

`of` takes exactly one pair, so reach for `builder` as soon as you have two. The builder accepts either a typed key or a plain name with a `String`, `Long`, `Double`, or `Boolean` value, and putting the same key twice keeps the last value. `Attributes.ServiceName` and `ServiceVersion` are predefined keys for the conventional OpenTelemetry names; define your own with [`AttributeKey`](./attribute-key.md).

## Reading a Value

`get` takes a key and returns the value at that key's declared type:

```scala
import zio.blocks.telemetry._

val route = AttributeKey.string("http.route")
val attrs = Attributes.of(route, "/orders")

val found: Option[String] = attrs.get(route)               // Some("/orders")
val missing = attrs.get(AttributeKey.string("db.system"))  // None
```

The `Option` covers a key that isn't there. `size` and `isEmpty` report how many pairs a set holds without reading any of them.

## Merging Two Sets

`++` combines sets, which is how a signal's own attributes join those of its surroundings:

```scala
import zio.blocks.telemetry._

val env = AttributeKey.string("deployment.environment")

val base = Attributes.of(env, "staging")
val merged = base ++ Attributes.of(env, "production")

merged.get(env)  // Some("production") — the right side wins
```

Merging appends rather than deduplicates, so a key on both sides stays in the set twice: the right side wins every lookup, but `size` counts it twice and anything iterating the set — an exporter included — sees it twice. Where two sets may share a key, build one set with a builder instead.

## Iterating

Reading a whole set back — to export it, or to check it in a test — comes in two forms, and the difference is worth understanding before you pick one.

A `Long` you store here is kept as a plain number, not as an object. Handing it to code that accepts *any* kind of value means wrapping it in an object first — **boxing** — and that wrapper is a small allocation, per attribute, every time. Fine once in a test; not fine on a path that runs on every request.

`accept` avoids it by asking you for one method per kind of value. Each method's parameter is that exact type, so the number is passed as a number and nothing is wrapped:

```scala
import zio.blocks.telemetry._

val attrs = Attributes.builder.put("http.route", "/orders").put("http.status_code", 200L).build

attrs.accept(new AttributeVisitor {
  def visitString(key: String, value: String): Unit   = println(s"$key=$value")
  def visitLong(key: String, value: Long): Unit       = println(s"$key=$value")
  def visitDouble(key: String, value: Double): Unit   = println(s"$key=$value")
  def visitBoolean(key: String, value: Boolean): Unit = println(s"$key=$value")
})
```

Those four are the only methods you must write. There are four more for sequence values, and they already do nothing — so if you store a sequence attribute and don't override its method, `accept` passes over it in silence. Nothing fails; the attribute simply never reaches your exporter.

The other form is for when convenience matters more than a few allocations, which is most code outside an exporter. `foreach` and `toMap` do the wrapping for you, handing each value over as an [`AttributeValue`](./any-value.md) — one wrapper per kind, so you can match on which kind you got:

```scala
import zio.blocks.telemetry._

val attrs = Attributes.of(Attributes.ServiceName, "payments")

attrs.foreach { (key, value) =>
  value match {
    case AttributeValue.StringValue(s) => println(s"$key is text: $s")
    case other                         => println(s"$key is $other")
  }
}

val asMap: Map[String, AttributeValue] = attrs.toMap
```

Two sets holding the same pairs are equal regardless of the order they were built in, so a test can compare an expected set directly against a recorded one.

## See Also

- [AttributeKey](./attribute-key.md) — defining typed keys
- [AttributeValue](./any-value.md) — the boxed wrapper `foreach`/`toMap` hand back
- [Resource](./resource.md) — service identity, itself an `Attributes` set
- [Common Types](./index.md) — the types every pillar uses
