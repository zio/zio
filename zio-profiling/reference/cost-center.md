# CostCenter Reference

> A `CostCenter` is a named location in your program: a path of names from the outermost tagged effect down to the innermost one. Both profilers record their results per cost center. The compiler plugin creates cost centers for you, and you can create them yourself with the functions below. The type lives in package `zio.profiling`.

```scala
import zio._
import zio.profiling.CostCenter
```

## The type

```text
final case class CostCenter(locations: Chunk[String])
```

A cost center is a list of location names. The empty list is the root cost center, which is where code runs when no tag is active.

| Member | Type | Description |
|--------|------|-------------|
| `render` | `String` | The names joined with `;`. The root renders as the empty string. |
| `isRoot` | `Boolean` | `true` when there are no names. |
| `/(location)` | `CostCenter` | A child cost center, nested under this one, with `location` as its last name. |
| `location` | `Option[String]` | The last name, or `None` for the root. |
| `isChildOf(other)` | `Boolean` | `true` when `other`'s names are a prefix of this cost center's names. |
| `hasParent(name)` | `Boolean` | `true` when `name` is one of the names in the path, at any depth. |
| `hasParentMatching(regex)` | `Boolean` | `true` when some name in the path matches `regex` somewhere. |

Build a path with `/`, starting from `Root`:

```scala
val path = CostCenter.Root / "foo" / "bar"
// path: CostCenter = CostCenter(locations = IndexedSeq("foo", "bar"))

path.render
// res0: String = "foo;bar"

path.location
// res1: Option[String] = Some(value = "bar")

path.isChildOf(CostCenter.Root / "foo")
// res2: Boolean = true

path.hasParent("foo")
// res3: Boolean = true

path.hasParentMatching("^ba".r)
// res4: Boolean = true
```

`hasParent` compares whole names. `hasParentMatching` takes a regular expression and checks whether any name in the path contains a match, so `"^ba"` matches `bar`.

## Companion object

| Member | Type | Description |
|--------|------|-------------|
| `Root` | `CostCenter` | The root cost center, with no names. |
| `withChildCostCenter(name)(zio)` | `(name: String)(zio: ZIO[R, E, A]): ZIO[R, E, A]` | Runs `zio` in a child cost center named `name`, nested under the active one. |
| `withChildCostCenterStream(name)(stream)` | `(name: String)(stream: ZStream[R, E, A]): ZStream[R, E, A]` | The same, for a `ZStream`. |
| `getCurrent` | `UIO[CostCenter]` | The cost center that the running fiber is in. |
| `getCurrentUnsafe(fiber)` | `(implicit unsafe) => CostCenter` | The cost center of a given fiber, read without an effect. The sampling profiler uses it. |

The how-to guide [Tag effects by hand](../how-to/tag-effects-by-hand.md) shows the wrapping functions in use. The compiler plugin rewrites each tagged definition into a call to `withChildCostCenter` with a name of the form `fullName(File.scala:line)`.

```scala
val parse: UIO[Int] = CostCenter.withChildCostCenter("parse")(ZIO.succeed(21))
```

## Fibers

The active cost center is stored in a `FiberRef`. A fiber that is forked starts in the cost center of its parent. When the fiber is joined, the parent keeps its own cost center, so tags set inside a child fiber never leak back out.

## Naming

Names are compared exactly, so `"parse"` and `"Parse"` are different cost centers. Keep names stable between runs. A profile compares samples per cost center, and a name that changes on each run produces a new cost center each time.

## Related

- [Sampling profiler reference](sampling-profiler.md) reports samples per cost center.
- [Causal profiler reference](causal-profiler.md) selects cost centers for experiments. `candidateSelector` receives the last name of each one.
