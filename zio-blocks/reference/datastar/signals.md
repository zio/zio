# Signal

> `Signal[A]` is a named, typed handle on a piece of state that lives in the browser. `Signal#:=` pairs it with a value to produce a `SignalUpdate[A]`, serialized through the type's JSON codec, and `Signal#ref` produces the `$name` reference form used inside Datastar expressions. Names are validated — literals during compilation. The three types:

```scala
final class Signal[A] private (val name: String) {
  def :=(value: A)(implicit schema: Schema[A]): SignalUpdate[A]
  def ref: DatastarRef
}

final class SignalUpdate[A] private (val name: String, val serialized: String)

final class DatastarRef private (val signalName: String) {
  val value: String   // "$" + signalName
}
```

## Motivation

Datastar identifies state by name, as a string, on both sides of the wire. The browser reads `data-signals="{count: 0}"`, an expression refers to `$count`, and the server patches it with `signals {"count": 42}`. Three places, one name, and nothing checks that they agree.

They disagree in ways that produce no error. A signal referenced as `$cont` is simply undefined, so the expression evaluates to nothing and the element renders blank. A patch sent for `"counter"` when the page declared `"count"` creates a second signal nobody reads. Both look like the feature is broken rather than the name being wrong.

`Signal[A]` makes the name a value declared once, so all three uses are the same Scala identifier. Declaring it also validates it — and the type parameter decides how values serialize, so a `Signal[Int]` cannot be patched with a string.

## Quick Showcase

Declare a signal, then use the same value for the attribute, the expression, and the patch:

```scala
import zio.blocks.html._
import zio.http.datastar._

val count = Signal[Int]("count")
```

The three forms it produces — an initial value, a reference, and a patch:

```scala
(count := 0).serialized
// res0: String = "0"
count.ref.value
// res1: String = "$count"
DatastarEvent.patchSignals(count := 42).renderSSE
// res2: String = """event: datastar-patch-signals
// data: signals {"count":42}
// 
// """
```

## Construction

Two constructors. They differ in when a *literal* name is checked; for a computed name both check at runtime.

### `Signal.apply` — checked at compile time

`Signal[A]("name")` validates a literal name during compilation, so a malformed name is a build failure rather than a silently dead expression:

```scala
import zio.http.datastar._

val count   = Signal[Int]("count")
val nested  = Signal[String]("user.profile.name")
```

Dotted names are how Datastar expresses nested signal namespaces, and each segment is validated separately:

```scala
count.name
// res4: String = "count"
nested.name
// res5: String = "user.profile.name"
```

### `Signal.dynamic` — checked at runtime

`Signal.apply` also accepts a computed name — its macro falls back to a runtime check when the argument is not a literal — so `Signal.dynamic` is not strictly required. Prefer it anyway for computed names, because it states that intent at the call site. Either way the check throws `IllegalArgumentException`:

```scala
object Signal {
  def dynamic[A](name: String): Signal[A]
}
```

Use it for names derived from data — a row id, a tenant prefix — and validate before constructing if the source is untrusted:

```scala
import zio.http.datastar._

def rowSignal(id: Long): Signal[Boolean] = Signal.dynamic[Boolean](s"row$id.selected")
```

A well-formed computed name behaves exactly like a literal one:

```scala
rowSignal(17).name
// res7: String = "row17.selected"
rowSignal(17).ref.value
// res8: String = "$row17.selected"
```

A malformed one fails at the point of construction rather than in the browser:

```scala
scala.util.Try(Signal.dynamic[Int]("has spaces")).failed.map(_.getMessage)
// res9: Try[String] = Success(
//   "Invalid Datastar signal name 'has spaces'. Signal names must be dot-separated JavaScript identifiers and must not contain '__'."
// )
```

### Name Rules

A valid name is one or more dot-separated segments, each a Java identifier, and must not contain `__`:

| Name | Valid | Why |
| ---- | ----- | --- |
| `count` | yes | single identifier |
| `user.profile.name` | yes | dot-separated identifiers |
| `row17.selected` | yes | digits are identifier parts, just not the first character |
| `row.17.selected` | **no** | the segment `17` starts with a digit |
| `has spaces` | no | space is not an identifier character |
| `count__case` | no | `__` is reserved for Datastar modifier syntax |
| `` (empty) | no | at least one segment is required |
| `a..b` | no | empty segment |

Two of these catch people out. The `__` restriction exists because Datastar uses a double underscore to separate an attribute name from its modifiers, so a signal containing one would be parsed as a modifier suffix. And every segment must *start* with an identifier-start character, which rules out a purely numeric segment — the natural `s"row.$id.selected"` for a per-row signal is rejected, and `s"row$id.selected"` is the form that works.

## Producing Values

A signal on its own is just a name. Two methods turn it into something renderable.

### `Signal#:=` — pair a value with the name

`Signal#:=` requires a `Schema[A]` and serializes through that schema's JSON codec, producing a `SignalUpdate[A]`:

```scala
final class Signal[A] {
  def :=(value: A)(implicit schema: Schema[A]): SignalUpdate[A]
}
```

The update holds the name and the already-serialized JSON:

```scala
import zio.http.datastar._

final case class Point(x: Int, y: Int)

object Point {
  implicit val schema: zio.blocks.schema.Schema[Point] = zio.blocks.schema.Schema.derived[Point]
}

val origin = Signal[Point]("origin")
```

Because serialization goes through the schema, a case class signal works without extra ceremony:

```scala
(origin := Point(3, 4)).serialized
// res11: String = "{\"x\":3,\"y\":4}"
```

:::warning[`serialized` is inserted verbatim]
`SignalUpdate#serialized` is spliced directly into the rendered expression or SSE payload. It is produced by the schema codec, so it is valid JSON — but if you construct a payload by other means (`DatastarEvent.patchSignalsRaw`), you are responsible for the JSON being well formed.
:::

### `Signal#ref` — refer to the signal in an expression

`Signal#ref` returns a `DatastarRef`, whose `value` is the name prefixed with `$` — the form Datastar expressions use to read a signal:

```scala
import zio.blocks.html._
import zio.http.datastar._

val price    = Signal[Double]("price")
val quantity = Signal[Int]("quantity")
```

A reference renders as `$name`, and both `Signal` and `DatastarRef` have `ToJs` instances so either can be interpolated into a `js"..."` expression:

```scala
price.ref.value
// res13: String = "$price"
js"$price * $quantity".value
// res14: String = "$price * $quantity"
```

Interpolating the signal itself is equivalent to interpolating its ref, which is why most code never mentions `DatastarRef` explicitly.

## SignalUpdate

`SignalUpdate[A]` carries a name and its serialized value. Beyond being consumed by `DatastarEvent.patchSignals` and the `dataSignals` attribute, it renders as a JavaScript object literal.

### `SignalUpdate.objectExpression`

`SignalUpdate.objectExpression` renders one or more updates as a single object expression, which is what the `data-signals` attribute contains:

```scala
object SignalUpdate {
  def objectExpression(update: SignalUpdate[_], updates: SignalUpdate[_]*): String
}
```

Several updates become one object, with each key quoted:

```scala
import zio.http.datastar._

val price    = Signal[Double]("price")
val quantity = Signal[Int]("quantity")
```

Keys are escaped, so a dotted signal name stays a single key rather than becoming a nested path:

```scala
SignalUpdate.objectExpression(price := 9.99, quantity := 2)
// res16: String = "{\"price\": 9.99, \"quantity\": 2}"
```

An implicit `ToJs[SignalUpdate[A]]` renders a single update the same way, so an update can be interpolated into an expression directly.

## Integration Points

`Signal` sits at the centre of the module: [Attributes](./attributes.md) accept signals as values and as keys (`dataBind`, `dataComputed`, `dataIndicator`), [Event Handlers](./events.md) reference them inside expressions, and [Server-Sent Events](./sse.md) patch them by name.

Outside the module, `Signal#:=` depends on `Schema[A]` and its JSON codec from [Schema](../schema/index.md), and the `ToJs` instances come from [HTML](../html.md), which is what lets a signal be interpolated into `js"..."`.
