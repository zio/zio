# How to make an option optional or give it a default

> An option built with a constructor such as `Options.text` is required: a command without it fails with `Expected to find --user option.`. This guide makes options optional, so the user can leave them out, and chooses what the program receives when they do.

You need the `zio-cli` library on your classpath (see [Installation](index.md#installation)) and a command whose options you want to relax; [Options](options.md) describes how options are built.

## Choose What a Missing Option Means

Pick the method by the value your program needs when the user leaves the option out:

| The program needs                    | Use                               | Value type  |
| ------------------------------------ | --------------------------------- | ----------- |
| To know whether the option was given | `Options#optional`                | `Option[A]` |
| A fixed value                        | `Options#withDefault`             | `A`         |
| `true` or `false` from a flag        | `Options.boolean`, nothing to add | `Boolean`   |

## Steps

We build the options of a `serve` command step by step: a flag, an option with a default, an optional option, and a pair of alternatives.

### 1. Use a Flag for a Yes-or-No Choice

A boolean option already has a default, the opposite of the value it gives when present. To declare a `--quiet` flag that is `false` unless given:

```scala
import zio.cli._

val quiet: Options[Boolean] = Options.boolean("quiet")
```

To invert a flag, pass `ifPresent = false`, as described in [Boolean Options](options.md#boolean-options).

### 2. Give an Option a Default

`Options#withDefault` uses its value when the option is missing. To default the number of retries to 3, convert the `BigInt` of `Options.integer` first and annotate the type:

```scala
val retries: Options[Int] = Options.integer("retries").map(_.toInt).withDefault(3)
```

The type annotation matters: without it, `Options.integer("retries").withDefault(3)` compiles as an `Options[Any]`, as [Defaults](options.md#defaults) explains.

### 3. Make an Option Optional

`Options#optional` gives `Some(value)` when the option is given and `None` when it is missing. To accept an optional user name:

```scala
val user: Options[Option[String]] = Options.text("user").optional
```

### 4. Put a Default on Alternatives

For two alternative options combined with `Options#orElseEither` or `Options#|`, put the default on the whole alternative, not on each option. To accept `--port` or `--socket`, with port 8080 when neither is given:

```scala
val endpoint: Options[Either[BigInt, String]] =
  (Options.integer("port") orElseEither Options.text("socket")).withDefault(Left(BigInt(8080)))
```

An option with a default inside an alternative always reports a collision; see [Choosing Between Options](options.md#choosing-between-options).

### 5. Combine the Options in a Command

`Options#++` combines the options, and the command receives a flat tuple with one value per option, in the order they are combined. To build the command:

```scala
val serve: Command[(Boolean, Int, Option[String], Either[BigInt, String])] =
  Command("serve", quiet ++ retries ++ user ++ endpoint)
```

### 6. Check the Help

The help of a single option with `Options#withDefault` ends with `This setting is optional. Default: '<value>'.`, and the help of an option with `Options#optional` ends with `This setting is optional.`. Alternatives with a default get no such sentence. To print the help of the command:

```scala
println(serve.helpDoc.toPlaintext(color = false))
// 
// OPTIONS
// 
//   --quiet
//     A true or false value.
// 
//     This setting is optional. Default: 'false'.
// 
//   --retries <integer>
//     An integer.
// 
//     This setting is optional. Default: '3'.
// 
//   --user <text>
//     A user-defined piece of text.
// 
//     This setting is optional.
// 
//   --port <integer>
//     An integer.
// 
//   --socket <text>
//     A user-defined piece of text.
// 
//
```

The usage line shows every optional option, including the alternatives with a default, in square brackets:

```scala
println(serve.synopsis.helpDoc.toPlaintext(color = false))
// serve [--quiet] [--retries <integer>] [--user <text>] [--port <integer>|--socket <text>]
//
```

## What the Default Does Not Cover

A default is used only when the option is missing; an invalid value such as `--retries many` still fails with `many is not a integer.`, and the default itself is not validated (see [Defaults](options.md#defaults)).

Positional arguments have no counterpart of `Options#withDefault` or `Options#optional`. To make an argument optional, accept at most one value with `Args#atMost(1)`, as described in [Args](args.md#methods).

## Complete Example

The complete program runs the `serve` command and prints the values it receives:

```scala
import zio._
import zio.cli._

object ServeApp extends ZIOCliDefault {
  val quiet: Options[Boolean]       = Options.boolean("quiet")
  val retries: Options[Int]         = Options.integer("retries").map(_.toInt).withDefault(3)
  val user: Options[Option[String]] = Options.text("user").optional
  val endpoint: Options[Either[BigInt, String]] =
    (Options.integer("port") orElseEither Options.text("socket")).withDefault(Left(BigInt(8080)))

  val serve = Command("serve", quiet ++ retries ++ user ++ endpoint)

  val cliApp = CliApp.make("serve", "1.0.0", HelpDoc.Span.text("Starts the server."), serve) {
    case (quiet, retries, user, endpoint) =>
      Console.printLine(s"quiet=$quiet retries=$retries user=$user endpoint=$endpoint")
  }
}
```

Run without options, the program prints `quiet=false retries=3 user=None endpoint=Left(8080)`.

## See Also

- [Options](options.md#defaults): the reference for `Options#optional` and `Options#withDefault`.
- [How to validate and convert a parameter's value](how-to-validate-input.md): reject values that parse but are not acceptable.
