# Rollout

> `Rollout` is the expression language behind dynamic flags. An expression is a semicolon-separated list of choices, each optionally targeted at a slash-separated path pattern and a percentage bucket. `Rollout` parses expressions into `Rollout.Choices`, evaluates them against a path and a bucket, and computes the deterministic bucket for a key. Supporting types: `Rollout.Choice`, `Rollout.Selector`, `Rollout.Segment`, `Flag.ReloadResult`, `DynamicFlag.UpdateRecord`. The parsed form of an expression:

```scala
object Rollout {
  final case class Choices(entries: List[Choice])

  sealed trait Choice
  object Choice {
    final case class Targeted(value: String, selector: Selector) extends Choice
    final case class CatchAll(value: String)                     extends Choice
  }

  final case class Selector(segments: List[Segment], percentage: Maybe[Int])

  sealed trait Segment
  object Segment {
    case object Wildcard                    extends Segment
    final case class Literal(value: String) extends Segment
  }
}
```

## Motivation

A feature flag that is only on or off cannot express the thing teams actually want: on for 10% of users, on for everyone in one region, off everywhere else. Encoding that as three separate booleans means three flags to keep consistent, and encoding it as code means a deploy for every adjustment.

A rollout expression puts the whole decision in one string, which is what makes it deployable through a config source. `"true@*/eu/100%; true@*/50%; false"` reads as three rules in priority order: everyone in `eu`, then half of everyone else, then off. Changing the rollout is changing that string.

The percentage is a function of the key, not a coin flip. The same key always lands in the same bucket, so a user who sees the new checkout flow sees it on every request, and raising 50% to 60% keeps the original 50% inside the group rather than reshuffling everyone.

## Grammar

An expression is a list of choices; a targeted choice pairs a value with a selector; a selector is a path pattern with an optional percentage:

```
expression = choice { ";" choice }
choice     = value "@" selector | value
selector   = path [ "/" percentage ]
path       = segment { "/" segment }
segment    = "*" | literal
percentage = digits "%"
```

Whitespace around choices and segments is trimmed. A choice with no `@` is a catch-all that matches everything.

The percentage is always the final slash-separated component, which is why `prod/50%` means "path `prod`, 50 percent" rather than a two-segment path. A selector consisting only of a percentage is rejected — there is no implicit "match everything" path, so write `*/50%` when you mean half of all keys.

## Evaluation

Evaluation walks the choices left to right and returns the first match. A catch-all matches immediately, so anything after it is unreachable.

### Path Matching

A path matches a selector only when both have the **same number of segments**, compared position by position. `Segment.Wildcard` matches any single segment; `Segment.Literal` requires equality:

```scala
import zio.blocks.config._

val bucket = Rollout.bucketFor("user-1234")
```

A single-literal selector matches only that exact one-segment path:

```scala
Rollout.select("on@beta; off", "beta", bucket)
// res0: Maybe[String] = "on"
Rollout.select("on@beta; off", "prod", bucket)
// res1: Maybe[String] = "off"
```

A wildcard matches any one segment, but still only one:

```scala
Rollout.select("on@*; off", "anything", bucket)
// res2: Maybe[String] = "on"
Rollout.select("on@*; off", "two/segments", bucket)
// res3: Maybe[String] = "off"
```

Multi-segment patterns mix the two, matching a path of exactly that length:

```scala
Rollout.select("on@*/eu; off", "user-1234/eu", bucket)
// res4: Maybe[String] = "on"
Rollout.select("on@*/eu; off", "user-1234/us", bucket)
// res5: Maybe[String] = "off"
```

:::warning[Segment count is exact]
There is no prefix or suffix matching and no multi-segment wildcard. A selector with two segments never matches a three-segment path. This is the most common cause of a rollout that silently falls through to its catch-all.
:::

### Percentages

The percentage is compared against the bucket, with three values special-cased: an absent percentage always matches a matching path, `0%` never matches, and `100%` always matches. Otherwise the choice matches when `bucket < percentage`:

| Percentage | Matches when the path matches   |
| ---------- | ------------------------------- |
| *(absent)* | Always                          |
| `0%`       | Never                           |
| `100%`     | Always                          |
| `n%`       | `bucket < n`                    |

Because the comparison is a strict less-than against a bucket in `0..99`, a percentage behaves as the literal share of keys it names.

### Bucketing

`Rollout.bucketFor` hashes a key with MurmurHash3 and reduces it to `0..99`. The result is stable for a given key across processes and restarts:

```scala
Rollout.bucketFor("user-1234")
// res6: Int = 7
Rollout.bucketFor("user-1234") == Rollout.bucketFor("user-1234")
// res7: Boolean = true
```

Different keys spread across the range, which is what makes a percentage approximate a uniform share:

```scala
(1 to 5).map(i => Rollout.bucketFor(s"user-$i"))
// res8: IndexedSeq[Int] = Vector(64, 97, 86, 68, 0)
```

Stability is the reason to pass a user or session identifier as the key rather than something that changes per call. A random key would re-roll the dice on every evaluation.

## API

Five methods cover parsing, evaluation, and diagnostics. `Rollout.select` is the one-shot form; the others let you parse once and evaluate many times.

### Rollout.select

`Rollout.select` parses and evaluates in one call, returning `Maybe.absent` both when nothing matches and when the expression will not parse:

```scala
Rollout.select("on@prod/50%; off", "prod", 10)
// res9: Maybe[String] = "on"
Rollout.select("@@@not-valid", "prod", 10)
// res10: Maybe[String] = zio.blocks.maybe.Absent$@53f42f8c
```

Because a parse failure is indistinguishable from a non-match, use `Rollout.select` only where a malformed expression should behave as "no decision". Parse explicitly when you need to know.

### Rollout.parseChoices

`Rollout.parseChoices` returns the parsed `Choices` or the first `ConfigError` it encountered:

```scala
Rollout.parseChoices("on@prod/50%; off")
// res11: Either[ConfigError, Choices] = Right(
//   Choices(
//     List(
//       Targeted(
//         value = "on",
//         selector = Selector(segments = List(Literal("prod")), percentage = 50)
//       ),
//       CatchAll("off")
//     )
//   )
// )
```

An expression with a malformed choice reports the failure rather than skipping it:

```scala
Rollout.parseChoices("on@prod/150%")
// res12: Either[ConfigError, Choices] = Left(
//   InvalidValue(
//     path = "rollout",
//     value = "prod/150%",
//     expectedType = "percentage <= 100",
//     source = "rollout",
//     cause = None
//   )
// )
```

Parsing is what `DynamicFlag` does at initialization and on every update, so these are the errors a flag rejects.

### Rollout.evaluateIndex

`Rollout.evaluateIndex` evaluates already-parsed choices, which is what the flag does on the hot path — parsing happens once per update, not once per evaluation:

```scala
val choices = Rollout.parseChoices("on@*/eu; off").toOption.get
```

Evaluating the same choices for several paths reuses the parse:

```scala
Rollout.evaluateIndex(choices, "user-1/eu", 42)
// res13: Maybe[String] = "on"
Rollout.evaluateIndex(choices, "user-1/us", 42)
// res14: Maybe[String] = "off"
```

### Rollout.validate

`Rollout.validate` parses an expression and returns warnings about rules that are suspicious rather than invalid. It flags choices rendered unreachable by an earlier catch-all:

```scala
Rollout.validate("off; on@prod")
// res15: Either[ConfigError, List[String]] = Right(
//   List("Choices after index 0 are unreachable (catch-all found)")
// )
```

It also flags a pattern whose percentages sum above 100, which usually means someone added a rule instead of adjusting one:

```scala
Rollout.validate("a@prod/70%; b@prod/60%")
// res16: Either[ConfigError, List[String]] = Right(
//   List("Cumulative percentage for pattern 'prod' is 130% (exceeds 100%)")
// )
```

A clean expression returns an empty list:

```scala
Rollout.validate("on@*/eu; on@*/50%; off")
// res17: Either[ConfigError, List[String]] = Right(List())
```

Warnings are advisory — `DynamicFlag` does not run them. Call `Rollout.validate` in a test or an admin endpoint that accepts expressions from operators.

### Parse Failures

Every parse failure is a `ConfigError.InvalidValue` with path and source `"rollout"`, and an `expectedType` describing what was wanted:

| Expression        | Rejected because                                     |
| ----------------- | ---------------------------------------------------- |
| `""`              | Empty expression.                                     |
| `"@prod"`         | No value before `@`.                                  |
| `"on@"`           | No selector after `@`.                                |
| `"on@50%"`        | Percentage with no path; write `on@*/50%`.             |
| `"on@prod/"`      | Trailing slash with no percentage.                    |
| `"on@prod/abc%"`  | Non-numeric percentage.                              |
| `"on@prod/150%"`  | Percentage above 100.                                 |

## The Parsed Form

`Rollout.Choices` wraps an ordered `List[Choice]`, and the order is the evaluation order. Matching on the ADT is how an admin tool can render or rewrite an expression rather than treating it as opaque text:

```scala
import zio.blocks.config._

val parsed = Rollout.parseChoices("on@*/eu/25%; off").toOption.get
```

A `Choice.Targeted` carries its value and selector; a `Choice.CatchAll` carries only a value:

```scala
parsed.entries
// res19: List[Choice] = List(
//   Targeted(
//     value = "on",
//     selector = Selector(
//       segments = List(Wildcard, Literal("eu")),
//       percentage = 25
//     )
//   ),
//   CatchAll("off")
// )
```

`Selector#segments` is the path pattern as a list, and `Selector#percentage` is a `Maybe[Int]` that is absent when the selector had no percentage:

```scala
parsed.entries.collect { case t: Rollout.Choice.Targeted => (t.selector.segments, t.selector.percentage) }
// res20: List[Tuple2[List[Segment], Maybe[Int]]] = List(
//   (List(Wildcard, Literal("eu")), 25)
// )
```

## Reload Lifecycle

A dynamic flag's expression can change while the process runs, either from code or from a source. Both paths record what happened so the change is auditable.

### Flag.ReloadResult

`DynamicFlag#reload` re-reads the expression from `FlagSource.Registry` and reports the outcome:

| Result                              | Meaning                                                       |
| ----------------------------------- | ------------------------------------------------------------- |
| `Flag.ReloadResult.NoSource`        | No registered source provides this flag's name; nothing changed. |
| `Flag.ReloadResult.Unchanged`       | The source's expression is identical to the current one.        |
| `Flag.ReloadResult.Updated(old, new)` | The expression was replaced; both versions are reported.       |
| `Flag.ReloadResult.Failed(error)`   | The source's expression will not parse; the old one is kept.     |

`Failed` keeping the previous expression is deliberate: a bad push to a flag service degrades to "no change", not to the constructor default.

Reloading is manual. Nothing in the module polls, so drive it from whatever scheduler your application already has:

```scala
import zio.blocks.config._

object newCheckout extends DynamicFlag[Boolean](false, "false")

newCheckout.reload() match {
  case Flag.ReloadResult.Updated(from, to) => println(s"rollout changed: $from -> $to")
  case Flag.ReloadResult.Failed(error)     => println(s"rollout rejected: ${error.message}")
  case Flag.ReloadResult.Unchanged         => ()
  case Flag.ReloadResult.NoSource          => println("no source registered for this flag")
}
```

### DynamicFlag.UpdateRecord

Every successful change — from `DynamicFlag#update` or from a reload — appends a `DynamicFlag.UpdateRecord` holding the old expression, the new one, and a millisecond timestamp:

```scala
final case class UpdateRecord(oldExpression: String, newExpression: String, timestampMillis: Long)
```

`DynamicFlag#updateHistory` returns them most-recent-first, capped at the last ten. Older records are discarded, so the history answers "what changed just now?" rather than serving as an audit log.

### Evaluation Counters

`DynamicFlag#apply` increments a per-key counter, and `DynamicFlag#counters` returns a snapshot as a `Map[String, Long]`. The counters are thread-safe but approximate — they use `LongAdder`, so a snapshot taken during traffic may miss in-flight increments.

Distinct keys are capped at 100. Once that many have been seen, every new key is counted under the single bucket `"other"` instead of getting its own entry. No exception is thrown and existing counters keep working, so a flag keyed by user id degrades to "100 known users plus a bulk count" rather than leaking memory.

`DynamicFlag#evaluate` bypasses counting entirely. Use it on paths where you do not want the bookkeeping, and reserve `DynamicFlag#apply` for the call sites whose distribution you want to observe.

`DynamicFlag#parseErrorCount` counts evaluations where a matched value could not be parsed into the flag's type, causing a fall back to the default. A non-zero count is always a misconfigured expression: the rollout matched, but the value it selected was not readable. Because every call still returns the default, this is otherwise silent.

## Integration Points

`Rollout` depends only on `Maybe` and `ConfigError`. It is used by `DynamicFlag` for initialization, updates, reloads, and every evaluation, and it can be used directly wherever a path-and-percentage decision is needed without a flag object.

See [Flags](./flags.md) for the flag types that drive it, and [Errors](./errors.md) for the error type its parser returns.
