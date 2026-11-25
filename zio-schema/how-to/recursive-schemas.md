# How to Define Schemas for Recursive Types

> Give a schema to a type that refers to itself, by derivation or by hand with Schema.defer, and encode it as JSON.

In this guide, we give schemas to types that refer to themselves: a comment thread whose replies are comments, an expression tree whose nodes hold other expressions, and a menu whose items can open another menu. By the end, each type has a working `Schema` and round-trips through the JSON codec.

We start with `DeriveSchema.gen`, which handles recursion for us, then write a recursive schema by hand with `Schema.defer`, and finish with two types that refer to each other.

## The Problem

A recursive type needs a schema that contains itself. A comment has a list of replies, each reply is a comment, so the schema of the `replies` field is "a list of the schema we are defining right now".

Written as an ordinary value, that definition reads its own name before the name has a value. With a strict `val`, the inner reference sees `null`, because the field is not assigned until the whole right-hand side finishes. With a `lazy val`, the inner reference starts the initialization again from the beginning, and on Scala 2 this repeats until the stack overflows. Neither fails at compile time, so the mistake shows up only when the schema is first used.

ZIO Schema solves this with `Schema.Lazy`, a schema that builds its inner schema only when a codec or operation first asks for it. `Schema.defer` creates one, and `DeriveSchema.gen` uses it automatically.

## Prerequisites

The examples use the core module and the JSON codec, which brings in `DeriveSchema`:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-schema"      % "1.9.2",
  "dev.zio" %% "zio-schema-json" % "1.9.2"
)
```

On Scala 2, `DeriveSchema` also needs `scala-reflect` at compile time; the [Installation](../index.md#installation) section shows the line to add. The [Schema Data Types](../basic-building-blocks.md) page describes `Schema.Lazy` and the other schema cases this guide builds by hand.

## Derive a Schema for a Tree

`DeriveSchema.gen` detects the recursion and wraps every reference back to the type in `Schema.defer`, so a recursive case class needs nothing special. We still declare the schema as an `implicit lazy val`, the safe default for any schema in a cycle: unlike a strict `val`, it is never read as `null`, whatever order the objects that hold the schemas initialize in:

```scala
import zio.schema._
import zio.schema.codec.JsonCodec

final case class Comment(author: String, text: String, replies: List[Comment])

object Comment {
  implicit lazy val schema: Schema[Comment] = DeriveSchema.gen[Comment]
}

val thread: Comment =
  Comment(
    "ana",
    "Ship it?",
    List(Comment("ben", "Tests first.", List(Comment("ana", "They pass.", Nil))))
  )

val commentCodec = JsonCodec.jsonCodec(Comment.schema)
```

The JSON codec follows the recursion to any depth:

```scala
println(commentCodec.encodeJson(thread, None))
// {"author":"ana","text":"Ship it?","replies":[{"author":"ben","text":"Tests first.","replies":[{"author":"ana","text":"They pass.","replies":[]}]}]}
```

Decoding the same text gives the thread back:

```scala
commentCodec.decodeJson(commentCodec.encodeJson(thread, None)) == Right(thread)
// res1: Boolean = true
```

Derivation also works for a recursive sealed trait and for a generic type such as `Tree[A]`. Write the schema by hand only when you cannot derive it, for example when the type is not a case class or sealed trait, or when you build schemas at runtime.

## Write a Recursive Schema by Hand

When we write the schema ourselves, the rule is: declare every schema in the cycle as a `lazy val`, and wrap every reference back into the cycle in `Schema.defer`. `Schema.defer` takes its argument by name and returns a `Schema.Lazy`, so the reference is read only after every `lazy val` has a value.

The following expression tree has two cases: a number, and the sum of two expressions. The `Add` schema refers back to `Expr.schema` through `Schema.defer`, and `Expr.schema` refers to the `Add` schema the same way:

```scala
sealed trait Expr

object Expr {
  final case class Num(value: Int)              extends Expr
  final case class Add(left: Expr, right: Expr) extends Expr

  lazy val numSchema: Schema[Num] =
    Schema.CaseClass1[Int, Num](
      id0 = TypeId.parse("example.Expr.Num"),
      field0 = Schema.Field[Num, Int]("value", Schema[Int], get0 = _.value, set0 = (num, value) => num.copy(value = value)),
      defaultConstruct0 = Num(_)
    )

  lazy val addSchema: Schema[Add] =
    Schema.CaseClass2[Expr, Expr, Add](
      id0 = TypeId.parse("example.Expr.Add"),
      field01 = Schema.Field[Add, Expr]("left", Schema.defer(schema), get0 = _.left, set0 = (add, left) => add.copy(left = left)),
      field02 = Schema.Field[Add, Expr]("right", Schema.defer(schema), get0 = _.right, set0 = (add, right) => add.copy(right = right)),
      construct0 = Add(_, _)
    )

  implicit lazy val schema: Schema[Expr] =
    Schema.Enum2[Num, Add, Expr](
      id = TypeId.parse("example.Expr"),
      case1 = Schema.Case[Expr, Num](
        id = "Num",
        schema = numSchema,
        unsafeDeconstruct = _.asInstanceOf[Num],
        construct = num => num,
        isCase = _.isInstanceOf[Num]
      ),
      case2 = Schema.Case[Expr, Add](
        id = "Add",
        schema = Schema.defer(addSchema),
        unsafeDeconstruct = _.asInstanceOf[Add],
        construct = add => add,
        isCase = _.isInstanceOf[Add]
      )
    )
}

val sum: Expr = Expr.Add(Expr.Num(1), Expr.Add(Expr.Num(2), Expr.Num(3)))

val exprCodec = JsonCodec.jsonCodec(Expr.schema)
```

The hand-written schema encodes a nested expression like a derived one, with each case wrapped in an object named after the case:

```scala
println(exprCodec.encodeJson(sum, None))
// {"Add":{"left":{"Num":{"value":1}},"right":{"Add":{"left":{"Num":{"value":2}},"right":{"Num":{"value":3}}}}}}

exprCodec.decodeJson(exprCodec.encodeJson(sum, None)) == Right(sum)
// res3: Boolean = true
```

`Num` takes no part in the cycle, so `numSchema` needs no `Schema.defer`. Adding one would do no harm: `Schema.defer` costs one extra layer, and codecs and operations look through it.

:::warning
Do not drop `Schema.defer` and rely on `lazy val` alone. A `lazy val` whose right-hand side reads the same `lazy val` again, directly or through another schema, keeps restarting its own initialization. Wrapping the back-reference in `Schema.defer` is what breaks the loop.
:::

## Handle Types That Refer to Each Other

Two types are mutually recursive when each one contains the other. A `Menu` has items, and one kind of item, `Submenu`, holds another `Menu`. We give each type its own derived schema in its companion object:

```scala
final case class Menu(title: String, items: List[MenuItem])

object Menu {
  implicit lazy val schema: Schema[Menu] = DeriveSchema.gen[Menu]
}

sealed trait MenuItem

object MenuItem {
  final case class Link(label: String, url: String) extends MenuItem
  final case class Submenu(menu: Menu)              extends MenuItem

  implicit lazy val schema: Schema[MenuItem] = DeriveSchema.gen[MenuItem]
}

val mainMenu: Menu =
  Menu(
    "Main",
    List(
      MenuItem.Link("Home", "/"),
      MenuItem.Submenu(Menu("Help", List(MenuItem.Link("FAQ", "/faq"))))
    )
  )

val menuCodec = JsonCodec.jsonCodec(Menu.schema)
```

`DeriveSchema.gen[Menu]` finds the implicit `MenuItem.schema` and wraps it in `Schema.defer`, and `DeriveSchema.gen[MenuItem]` does the same with `Menu.schema`, so neither `lazy val` reads the other while it initializes. Encoding goes through both schemas in turn:

```scala
println(menuCodec.encodeJson(mainMenu, None))
// {"title":"Main","items":[{"Link":{"label":"Home","url":"/"}},{"Submenu":{"menu":{"title":"Help","items":[{"Link":{"label":"FAQ","url":"/faq"}}]}}}]}

menuCodec.decodeJson(menuCodec.encodeJson(mainMenu, None)) == Right(mainMenu)
// res5: Boolean = true
```

A hand-written pair follows the same rule as the expression tree: both schemas are `lazy val`s, and each reference to the other goes through `Schema.defer`. The `Expr` example above is already such a pair, since `Expr.schema` and `Expr.addSchema` refer to each other.

## Inspect a Recursive Schema

Code that pattern matches on a schema, such as a custom codec, meets `Schema.Lazy` nodes. A derived schema puts one around every field schema that comes from an implicit, such as the `Schema[String]` of `Comment#author`, not only around recursive references. `Schema.force` removes the `Lazy` layers from the outside of a schema, so a match sees the real case:

```scala
val authorSchema: Schema[_] = Comment.schema match {
  case record: Schema.Record[_] => record.fields.head.schema
  case other                    => other
}
// authorSchema: Schema[_] = Lazy(schema0 = <function0>)

authorSchema.isInstanceOf[Schema.Lazy[_]]
// res6: Boolean = true
Schema.force(authorSchema)
// res7: Schema[_] = Primitive(
//   standardType = string,
//   annotations = IndexedSeq()
// )
```

Only unwrap a `Lazy` when you are about to use the schema inside it. A function that forces every nested schema up front, instead of one level at a time, loops forever on a recursive type. The [How to Write a Schema-Driven Codec](generic-codec.md) guide shows a codec that unwraps one level at a time.

## Going Further

- [Schema Data Types](../basic-building-blocks.md) documents `Schema.Lazy`, `Schema.defer`, and `Schema.force` with every other schema case.
- [Manual Schema Construction](../manual-schema-construction.md) covers the record and enum constructors used in the hand-written example.
- [MetaSchema](../operations/serialization-of-the-schema-itself.md) explains how a recursive schema is kept when it is turned into data: the repeated part becomes a reference to the earlier node.
- [How to Send a Schema over the Wire](send-schema-over-the-wire.md) sends such a schema to another service.
