# Tag Effects by Hand

> The compiler plugin names effects for you. Sometimes you want a name it does not give: a block inside a large `def`, a part of a stream, or a project that cannot use the plugin. Then you can wrap the effect yourself with `CostCenter`. This guide shows how.

## Wrap an effect in a cost center

`CostCenter.withChildCostCenter(name)(effect)` runs `effect` inside a new child cost center called `name`. The child is nested under whatever cost center is active when the call runs, so calls can be nested to build a path:

```scala
import zio._
import zio.profiling.CostCenter

val parse: UIO[Int] = CostCenter.withChildCostCenter("parse")(ZIO.succeed(21))

val render: UIO[String] = CostCenter.withChildCostCenter("render")(ZIO.succeed("done"))

val handle: UIO[String] = CostCenter.withChildCostCenter("handle") {
  for {
    n <- parse
    s <- render
  } yield s"$s $n"
}
```

When `handle` runs, the time spent in `parse` is attributed to the path `handle;parse`, and the time in `render` to `handle;render`. The effect's result, errors and interruption behave as they would without the wrapper.

## Wrap a stream

For a `ZStream`, use `CostCenter.withChildCostCenterStream`. It has the same shape:

```scala
import zio._
import zio.stream._
import zio.profiling.CostCenter

val numbers: ZStream[Any, Nothing, Int] =
  CostCenter.withChildCostCenterStream("numbers")(ZStream.range(1, 1000))
```

## Choose names that you can search for

The name is the only part of the path you choose, so pick names that make the flamegraph readable:

- Use the name of the operation, such as `"parse"` or `"write-response"`, rather than a log message.
- Keep the names stable. Renaming a cost center splits its samples from older profiles, which makes comparisons harder.
- Do not add the line number or a counter. The profiler groups samples by name, so a name that changes on every run is treated as a new cost center each time.

## Use the current cost center

`CostCenter.getCurrent` returns the cost center the running fiber is in. It is useful when you want to log which part of the program produced a result:

```scala
import zio._
import zio.profiling.CostCenter

val whereAmI: UIO[CostCenter] = CostCenter.withChildCostCenter("check")(CostCenter.getCurrent)
```

## Related

- [Set up the tagging compiler plugin](setup-tagging-plugin.md) does this automatically for every `def` and `val`.
- [CostCenter reference](../reference/cost-center.md) lists every method.
