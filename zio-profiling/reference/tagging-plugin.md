# Tagging Compiler Plugin Reference

> The tagging plugin is a compiler plugin that wraps ZIO effects and streams in a [`CostCenter`](cost-center.md) at compile time. It has no options and no runtime API. This page describes what it rewrites. For the steps to enable it, see [Set up the tagging compiler plugin](../how-to/setup-tagging-plugin.md).

## Artifact

```scala
libraryDependencies ++= Seq(
  compilerPlugin("dev.zio" %% "zio-profiling-tagging-plugin" % "0.3.5")
)
```

The artifact is published for Scala 2.12, Scala 2.13 and Scala 3. The code it generates calls `zio.profiling.CostCenter`, so the module that uses the plugin must also depend on `zio-profiling`.

## What the plugin tags

The plugin visits every `def` and `val` that has a right-hand side, and tags it when its declared or inferred type, after resolving type aliases, is one of:

| Type | Rewritten to |
|------|--------------|
| `ZIO[R, E, A]` | `CostCenter.withChildCostCenter(name)(rhs)` |
| `ZStream[R, E, A]` | `CostCenter.withChildCostCenterStream(name)(rhs)` |

Other types are left alone, including `ZLayer`, `ZSink` and `ZPipeline`.

## Names

The name is the fully qualified name of the definition, followed by the file name and the line number in parentheses:

```text
zio.profiling.ProfilerExamples.slow(ProfilerExamples.scala:9)
```

Because the name holds the line number, it changes when you move the definition.

## Phases

On Scala 2 the plugin is named `zio-profiling-tagging` and runs after `typer` and before `patmat`. On Scala 3 the phase has the same name and runs after `Pickler` and before `Staging`.

## Related

- [Set up the tagging compiler plugin](../how-to/setup-tagging-plugin.md)
- [Tag effects by hand](../how-to/tag-effects-by-hand.md) for effects the plugin does not name.
