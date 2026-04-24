# Compiler performance

> Quill will probably make the slow scala compiler even slower, since a lot of additional `Parsing`, `Typechecking`, `Implicit resolution` works introduced to expand a Query.

Quill will probably make the slow scala compiler even slower, since a lot of additional `Parsing`, `Typechecking`, `Implicit resolution` works introduced to expand a Query.

Following tips may help improving compilation time.

## Use `-Yprofile-trace` scalac options.
With `-Yprofile-trace` option, a chrome trace file will be produced after compilation.
It will help figure out what slowing down the compiler.

Note, this option need some tweak if you are running on java 9 or newer version.

## Split large module into multiple submodules
Since scalac is not fully parallelized, split into independent submodules can significantly reduce build time on multi-core cpu.

## Define decoder/encoder directly instead of `MappedEncoding`

`MappedEncoding` introduce more implicit resolutions, which may slow down compiler.

It is possible to define instance directly.

```scala
case class FooId(id: Long)
implicit val fooIdEncoder: Encoder[FooId] = mappedEncoder(MappedEncoding[FooId, Long](_.id), longEncoder)
implicit val fooIdDecoder: Decoder[FooId] = mappedDecoder(MappedEncoding[Long, FooId](FooId(_)), longDecoder)
```

## Share `QueryMeta` instance

`QueryMeta` generation requires `Decoder` resolution, tree generation, typechecking, etc which can be very slow.

`QueryMeta` is not shared by default, so define shared `QueryMeta` instance may reduce build time.

```scala
val ctx = SqlMirrorContext(MirrorIdiom, Literal)

// Prevent using default macro generated query meta instance.
// Use `_` instead of `*` if `-Xsource:3` not enabled.

 // Instance type must not be specified here, otherwise it will become dynamic query.
implicit val orderQueryMeta = ctx.materializeQueryMeta[Order]

ctx.run {
  query[Order]
}
```
Note, to use [`-Xsource:3`](https://github.com/scala/scala/pull/10439) scalac options, `-Xmigration` or `-Wconf:cat=scala3-migration:w` is required.

Otherwise, it will not compile due to lack of explicit type of implicit definition.
