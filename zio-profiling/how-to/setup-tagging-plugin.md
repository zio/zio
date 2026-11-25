# Set Up the Tagging Compiler Plugin

> The tagging compiler plugin gives each ZIO effect a name made from its place in your source code. Both profilers use those names. This guide shows how to turn the plugin on for a module, and what it changes in your code.

## Step 1: Add the plugin to the module you want to profile

The plugin must be enabled in the module that contains the code you want to profile. The rewritten code calls `CostCenter` from the `zio-profiling` library, so that dependency is needed in the same module:

```sbt
libraryDependencies += "dev.zio" %% "zio-profiling" % "0.3.5"
libraryDependencies += compilerPlugin("dev.zio" %% "zio-profiling-tagging-plugin" % "0.3.5")
```

In a multi-module build, add the `compilerPlugin` line only to the modules that contain effects you want named. The plugin is published for Scala 2.12, Scala 2.13 and Scala 3. Use the same `%%` form on every version; sbt picks the matching artifact.

## Step 2: Know what the plugin rewrites

The plugin looks at each `def` and `val` with a right-hand side whose type is a ZIO effect (`ZIO[R, E, A]`) or a stream (`ZStream[R, E, A]`). It wraps the right-hand side in a call to `CostCenter.withChildCostCenter` (or `withChildCostCenterStream` for streams). The name it passes is the fully qualified name of the definition, followed by the file name and line number.

Your code stays the same. The rewrite happens during compilation:

```text
val testEffect = ZIO.unit

// is compiled as if it were

val testEffect = CostCenter.withChildCostCenter("foo.Foo.testEffect(Foo.scala:12)")(ZIO.unit)
```

Because the name holds the line number, it changes when you move code. That is expected: the name always points at the definition that was tagged.

## Step 3: Check that the plugin runs

Run `sbt clean compile` once after you add the plugin, so that every source file is compiled again with the rewrite. Then run a profile. With the plugin active, the names in the output look like the one above. Without it, the output contains a single entry with no name, because every sample lands in the root cost center.

:::note[Definitions that are not tagged]
Only `def` and `val` definitions are rewritten. Effects created inline, such as `ZIO.succeed(...)` passed straight into a call, are part of the enclosing definition and get that name. To give such an effect its own name, wrap it with `CostCenter.withChildCostCenter`. See [Tag effects by hand](tag-effects-by-hand.md).
:::

## Related

- [Tag effects by hand](tag-effects-by-hand.md) gives you the same names without the plugin.
- [CostCenter reference](../reference/cost-center.md) lists the methods on cost centers.
