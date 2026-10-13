# Schema Search and Update

> `SchemaSearch` and `TypeSearch` are the two `DynamicOptic` node kinds that turn a path into a search: instead of naming one field, they match every value of a shape or type anywhere in a structure. This page covers the machinery behind that search once a path resolves against a value or a schema: `SchemaMatch`, the structural predicate the search matches against; `SearchTraversal`, the `Traversal[S, A]` engine that executes a search over a typed value and lets you fold, modify, or check the matches; and `Updater`, the mechanism for rewriting a `Reflect`/`Term` tree at a resolved path rather than the values it describes.

For the search DSLs themselves — the typed `.searchFor[T]` macro, the `#Pattern` string syntax, and the supported pattern grammar — see [DynamicOptic](./dynamic-optic.md#search-optics). This page assumes you can already build a search path and focuses on what runs once you have one.

## Design & Structure

A search path splits into two phases that operate on different representations of your data:

```
Typed value S                          Schema tree Reflect[F, S]
      │                                        │
      ▼                                        ▼
SearchTraversal[S, A]                  Reflect#updated(path)(Updater)
  (built from .searchFor[T]              (rewrites the Reflect/Term
   or SearchTraversal.apply)              node found at a path)
      │
      ▼
DynamicValue tree, walked depth-first
      │
      ▼
SchemaMatch.matches(pattern, value)     ← only for #Pattern searches;
  (the structural predicate)              .searchFor[T] matches by TypeId instead
```

`SearchTraversal` finds and transforms **values** — it decodes candidate subtrees of a concrete `S` and collects the ones that decode as `A`. `Updater` rewrites **schema metadata** — it walks the `Reflect`/`Term` tree that describes a type and replaces the node at a path, independent of any particular value. The two are easy to conflate because both start from a `DynamicOptic` path, but a `TypeSearch`/`SchemaSearch` node is only meaningful to the value-searching side: `Reflect#updated` does not special-case those nodes, so a path that reaches `Reflect#updated`/`Schema#updated` must resolve through `Field`/`Case`/`AtIndex`/`Elements`/`Wrapped`/`MapKeys`/`MapValues` nodes only.

## SchemaMatch — Structural Pattern Matching

`SchemaMatch.matches` is the predicate a `#Pattern` search node matches against once it reaches a `DynamicValue`: given a `SchemaRepr` pattern and a `DynamicValue`, it returns whether the value has that shape:

```scala
object SchemaMatch {
  def matches(pattern: SchemaRepr, value: DynamicValue): Boolean
}
```

`SchemaRepr` is the small pattern language `#Pattern` strings parse into — `Wildcard`, `Primitive(name)`, `Record(fields)`, `Variant(cases)`, `Sequence(element)`, `Map(key, value)`, `Optional(inner)`, and `Nominal(name)`. Constructing one directly and matching it against a `DynamicValue` shows the same rules the `#record { ... }` syntax compiles down to:

```scala
import zio.blocks.schema._

val personPattern = SchemaRepr.Record(
  IndexedSeq("name" -> SchemaRepr.Primitive("string"), "age" -> SchemaRepr.Primitive("int"))
)

val alice = DynamicValue.Record("name" -> DynamicValue.string("Alice"), "age" -> DynamicValue.int(30))
val bob   = DynamicValue.Record("name" -> DynamicValue.string("Bob"), "role" -> DynamicValue.string("admin"))
```

`Record` matching is a subset match — every field named in the pattern must exist with a matching type, but extra fields on the value are fine, which is why `bob` fails only because `age` is missing, not because of the extra `role` field:

```scala
SchemaMatch.matches(personPattern, alice)
// res0: Boolean = true
SchemaMatch.matches(personPattern, bob)
// res1: Boolean = false
```

`Wildcard` matches anything, and `Sequence`/`Map` patterns match when every element or entry matches — an empty sequence or map always matches, since there is nothing to fail on:

```scala
import zio.blocks.schema._

val ages = DynamicValue.Sequence(DynamicValue.int(1), DynamicValue.int(2))
SchemaMatch.matches(SchemaRepr.Sequence(SchemaRepr.Primitive("int")), ages)
SchemaMatch.matches(SchemaRepr.Wildcard, ages)
```

`Nominal(name)` always returns `false` against a `DynamicValue`, because a decoded value carries no record of the Scala type it came from — the same limitation documented for `#Person`-style patterns in [DynamicOptic's known limitation](./dynamic-optic.md#known-limitation-nominal-matching-in-untyped-contexts). Matching by nominal type requires the typed `.searchFor[T]` API instead, which matches by `TypeId` rather than by structure.

## SearchTraversal — Executing a Search Over Values

`SearchTraversal` is the `Traversal[S, A]` that `.searchFor[T]` and `#Pattern` searches compile down to. Construct one directly from a pair of schemas when you want the traversal without going through the macro or the path interpolator:

```scala
import zio.blocks.schema._

case class Address(city: String)
object Address {
  implicit val schema: Schema[Address] = Schema.derived
}

case class Person(name: String, age: Int, address: Address)
object Person {
  implicit val schema: Schema[Person] = Schema.derived
}

case class Team(name: String, lead: Person, members: List[Person])
object Team {
  implicit val schema: Schema[Team] = Schema.derived
}

case class Company(name: String, ceo: Person, teams: List[Team])
object Company extends CompanionOptics[Company] {
  implicit val schema: Schema[Company] = Schema.derived
}

val findPeople: Traversal[Company, Person] = SearchTraversal[Company, Person]

val acme = Company(
  "Acme",
  ceo = Person("Alice", 45, Address("NYC")),
  teams = List(
    Team("Platform", Person("Bob", 32, Address("SF")), List(Person("Carol", 28, Address("SF")))),
    Team("Sales", Person("Dave", 39, Address("LA")), Nil)
  )
)
```

`SearchTraversal[Company, Person]` resolves the two implicit `Schema` instances the same way `Company.optic(_.searchFor[Person])` does—both produce the identical traversal, so pick whichever reads better at the call site. `Traversal#fold` walks every match depth-first, left-to-right, which is why the CEO comes before any team, and each team's lead comes before its members:

```scala
findPeople.fold(acme)(List.empty[String], (names, p) => names :+ p.name)
// res3: List[String] = List("Alice", "Bob", "Carol", "Dave")
```

`Traversal#modify` rewrites every match in place and rebuilds the structure around it, `modifyOption` returns `None` instead of a no-op result when nothing matched, and `modifyOrFail` surfaces a decoding failure as `Left` instead of silently keeping the original value:

```scala
findPeople.modify(acme, p => p.copy(age = p.age + 1)).ceo.age
// res4: Int = 46
SearchTraversal[Company, Address].modifyOption(acme, a => a.copy(city = a.city.toUpperCase)).map(_.ceo.address.city)
// res5: Option[String] = Some("NYC")
SearchTraversal[Team, Address].modifyOption(acme.teams.head, a => a).isDefined
// res6: Boolean = true
```

`Traversal#check` reports whether a traversal has at least one match, which is how a search-backed traversal signals "nothing here" without throwing:

```scala
SearchTraversal[Company, Person].check(acme).isEmpty
// res7: Boolean = true
SearchTraversal[Team, Boolean].check(acme.teams.head).isEmpty
// res8: Boolean = false
```

### Composing a Search With Other Optics

A search traversal composes with `Lens`, `Prism`, `Optional`, and other `Traversal`s on either side, so you can narrow the search to part of a structure or refine each match further. Searching from a specific field finds only what is reachable from there, and appending a lens after a search projects each match down to one of its fields:

```scala
import zio.blocks.schema._

object CompanyOptics extends CompanionOptics[Company] {
  implicit val schema: Schema[Company] = Company.schema

  // Search restricted to one team: only Bob and Carol, never Alice or Dave
  val platformMembers: Traversal[Company, Person] = optic(_.teams.at(0).searchFor[Person])

  // Search first, then focus each match's city
  val allCities: Traversal[Company, String] = optic(_.searchFor[Address].city)
}
```

Both directions rebuild the same way a plain path-based traversal does: `Traversal#modify`, `Traversal#fold`, and `Traversal#check` all work on the composed traversal exactly as they do on a bare `SearchTraversal`, because composition produces another `Traversal[S, A]` — the fact that a search sits inside it is an implementation detail, not a different API.

### Recursive Types Are Safe to Search

Because `SearchTraversal` walks the decoded `DynamicValue` of a concrete value rather than the schema definition, searching a recursive type (a tree, a linked structure) terminates naturally — the value itself is always finite, even though its `Reflect` describes an unbounded type. There is nothing extra to opt into; a self-referential case class searches the same way a flat one does.

## Updater — Rewriting Schema Metadata

`Reflect.Updater` and `Term.Updater` are the callbacks behind `Schema#updated` and `Reflect#updated` — the mechanism for rewriting a schema's metadata (documentation, defaults, validations, or a field's shape entirely) at a resolved path, as opposed to rewriting the values that schema describes:

```scala
object Reflect {
  trait Updater[F[_, _]] {
    def update[A](reflect: Reflect[F, A]): Reflect[F, A]
  }
}

object Term {
  trait Updater[F[_, _]] {
    def update[S, A](input: Term[F, S, A]): Option[Term[F, S, A]]
  }
}
```

The two differ in one important way: `Reflect.Updater#update` is total — it always returns a `Reflect`, because a schema node can be re-shaped but not removed. `Term.Updater#update` is partial — returning `None` deletes the field or case the updater targets, which is how `Record#modifyField` and `Variant#modifyCase` support dropping a member rather than only renaming or retyping it.

[Schema](./schema.md#updating-nested-schemas) already covers the common case, updating one field through an optic with a plain function: `Schema[Person].updated(Person.address)(_.doc("Mailing address"))`. That convenience overload builds a `Reflect.Updater` for you. Reaching for `Reflect.Updater` directly is what you need for the case that overload cannot express — a `DynamicOptic` path built at runtime, or a rewrite that needs the full node rather than just its focus:

```scala
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding

case class Config(host: String, port: Int)
object Config {
  implicit val schema: Schema[Config] = Schema.derived
}

// Attach documentation to a field found by a runtime-built DynamicOptic path
val documented: Option[Schema[Config]] =
  Schema[Config].updated(DynamicOptic.root.field("port"))(new Reflect.Updater[Binding] {
    def update[A](reflect: Reflect[Binding, A]): Reflect[Binding, A] =
      reflect.doc("The TCP port the server listens on")
  })
```

`Term.Updater` operates one level up, on the named field or case itself rather than its value, which is what makes rename and delete possible. Renaming reuses the term's existing `value`; deleting a field returns `None` and the field disappears from the record entirely:

```scala
import zio.blocks.schema._
import zio.blocks.schema.binding.Binding

case class LegacyUser(id: Long, username: String, internalNotes: String)
object LegacyUser {
  implicit val schema: Schema[LegacyUser] = Schema.derived
}

val userRecord = Schema[LegacyUser].reflect.asRecord.get

// Rename username -> name
val renamed = userRecord.modifyField("username")(new Term.Updater[Binding] {
  def update[S, A](input: Term[Binding, S, A]): Option[Term[Binding, S, A]] =
    Some(input.copy(name = "name"))
})

// Drop internalNotes entirely by returning None
val withoutNotes = userRecord.modifyField("internalNotes")(new Term.Updater[Binding] {
  def update[S, A](input: Term[Binding, S, A]): Option[Term[Binding, S, A]] = None
})
```

Both updaters ran against the same `userRecord` independently, so `renamed` still has three fields with one renamed, while `withoutNotes` has two:

```scala
renamed.map(_.fields.map(_.name))
// res11: Option[IndexedSeq[String]] = Some(
//   Vector("id", "name", "internalNotes")
// )
withoutNotes.map(_.fields.map(_.name))
// res12: Option[IndexedSeq[String]] = None
```

:::warning[Search nodes are not valid `updated` paths]
`Reflect#updated`/`Schema#updated` walk a `DynamicOptic` through `Field`, `Case`, `AtIndex`/`AtIndices`/`Elements`, `Wrapped`, and `MapKeys`/`MapValues` nodes only. A path containing a `TypeSearch` or `SchemaSearch` node is not rejected outright, but it is not handled either — it falls into the same branch as `MapKeys`/`MapValues` and produces an unspecified result. Build search-based rewrites with `SearchTraversal#modify` on a value instead of `Schema#updated` on a schema. `DynamicMigration`, by contrast, rejects search nodes outright with an explicit error, since a migration requires one statically-known path.
:::

## Where Search Nodes Are (and Aren't) Handled

The same `TypeSearch`/`SchemaSearch` node means different things depending on which API resolves the path it's part of:

| API                       | Search nodes                                             |
| -------------------------- | ---------------------------------------------------------- |
| `SearchTraversal` / `.searchFor[T]` / `#Pattern` on a value | The intended use — this is what builds and executes the search |
| `Reflect#get` / `Schema#get`         | Supported — resolves the first match, then continues the remaining path from there |
| `Json` / `DynamicValue` patch paths (`JsonPatch`) | Supported — rewrites every match, not just the first |
| `Reflect#updated` / `Schema#updated` | Not handled — falls through to the map-key/value branch; use `SearchTraversal#modify` instead |
| `DynamicMigration`          | Rejected outright with `"Type/Schema search nodes are not supported in migration paths"` |

## See Also

- [DynamicOptic](./dynamic-optic.md#search-optics) — the `.searchFor[T]` and `#Pattern` search DSLs, and the full pattern grammar table.
- [Path Interpolator](./path-interpolator.md) — the `p"..."` string syntax that `#Pattern` search nodes parse from.
- [Schema](./schema.md#updating-nested-schemas) — `Schema#updated` and `Schema#@@` for the common, optic-based case of rewriting one field's metadata.
- [Reflect](./reflect.md) — the node types (`Record`, `Variant`, `Sequence`, `Map`, `Wrapper`, `Deferred`) that `Updater` rewrites and `SearchTraversal` decodes against.
- [Optics](./optics.md) — the base `Traversal[S, A]` type that `SearchTraversal` implements and composes with.
