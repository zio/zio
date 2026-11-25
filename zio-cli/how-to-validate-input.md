# How to validate and convert a parameter's value

> Every option and argument already checks that its input has the right type: `Options.integer` rejects `ten`. This guide adds the checks that the type cannot express, such as "a port between 1 and 65535" or "one of these three words", and converts the input into your own types. Each step shows the error message the user sees.

You need the `zio-cli` library on your classpath (see [Installation](index.md#installation)) and options and arguments to check; [Options](options.md) and [Args](args.md) describe how to build them.

## Choose a Method

Pick the method by the kind of check:

| Check                                              | Method                                    | Error text comes from            |
| -------------------------------------------------- | ----------------------------------------- | -------------------------------- |
| The value is one word from a fixed list            | `Options.enumeration`, `Args.enumeration` | ZIO CLI                          |
| The value matches some cases of a partial function | `Options#collect`                         | the message you pass             |
| Any rule, on an option                             | `Options#mapOrFail`                       | the `ValidationError` you return |
| Any rule, on an argument                           | `Args#mapOrFail`                          | the `HelpDoc` you return         |
| A conversion that may throw, on an argument        | `Args#mapTry`                             | the exception's message          |
| A path that must exist, or must be new             | an `Exists` value, or a `PrimType.Path`   | ZIO CLI                          |

`Options#mapOrFail` and `Args#mapOrFail` look alike but differ in their error type: the `Options` version returns a `ValidationError`, and the `Args` version returns a `HelpDoc`. `Options` has no counterpart of `Args#mapTry`.

## Steps

We first define a helper that parses a command line and returns the error text, so each step can show what the user sees. `CliApp` prints the same text when parsing fails:

```scala
import zio._
import zio.cli._

def errorFor[A](command: Command[A], input: String*): String =
  Unsafe.unsafe { implicit unsafe =>
    Runtime.default.unsafe
      .run(command.parse(input.toList, CliConfig.default).flip.map(_.error.toPlaintext(color = false).trim))
      .getOrThrowFiberFailure()
  }
```

The input starts with the command name, because `Command#parse` expects it.

### 1. Accept Only Fixed Words

`Options.enumeration` maps each accepted word to a value. To accept three log levels:

```scala
val level: Options[Int] = Options.enumeration("level")(("debug", 0), ("info", 1), ("warn", 2))
val log                 = Command("log", level)
```

A word outside the list fails, and so does a word in the wrong case:

```scala
println(errorFor(log, "log", "--level", "error"))
// Expected one of the following cases: debug, info, warn
println(errorFor(log, "log", "--level", "DEBUG"))
// Expected one of the following cases: debug, info, warn
```

### 2. Keep Some Values With Options#collect

`Options#collect` takes the message to show and a partial function. Values that the partial function does not match are rejected. To accept two colors and map them to codes:

```scala
val color: Options[Int] =
  Options.text("color").collect("color must be red or blue") {
    case "red"  => 1
    case "blue" => 2
  }
val paint = Command("paint", color)
```

With an unmatched color, the user sees the message:

```scala
println(errorFor(paint, "paint", "--color", "green"))
// color must be red or blue
```

### 3. Check an Option With Options#mapOrFail

`Options#mapOrFail` runs a function that returns `Right` with the converted value, or `Left` with a `ValidationError`. Use `ValidationErrorType.InvalidValue` for a value that does not meet the rule, and `HelpDoc.p` for the message. To accept only valid port numbers:

```scala
val port: Options[Int] =
  Options.integer("port").mapOrFail { (value: BigInt) =>
    if (value >= 1 && value <= 65535) Right(value.toInt)
    else Left(ValidationError(ValidationErrorType.InvalidValue, HelpDoc.p("Port must be between 1 and 65535.")))
  }
val serve = Command("serve", port)
```

The user sees the type error before our rule runs, and our message when the type is right but the rule fails:

```scala
println(errorFor(serve, "serve", "--port", "http"))
// http is not a integer.
println(errorFor(serve, "serve", "--port", "70000"))
// Port must be between 1 and 65535.
```

### 4. Check an Argument With Args#mapOrFail

`Args#mapOrFail` works the same way for positional arguments, but the `Left` holds only a `HelpDoc`. To accept a positive count:

```scala
val count: Args[Int] =
  Args.integer("count").mapOrFail { (n: BigInt) =>
    if (n > 0) Right(n.toInt) else Left(HelpDoc.p("The count must be positive."))
  }
val repeat = Command("repeat", count)
```

A count of zero fails with our message:

```scala
println(errorFor(repeat, "repeat", "0"))
// The count must be positive.
```

### 5. Convert With Code That Throws

`Args#mapTry` runs a conversion that may throw, and turns the exception's message into the error. To parse an argument as a URI:

```scala
val uri: Args[java.net.URI] = Args.text("uri").mapTry(java.net.URI.create)
val open                    = Command("open", uri)
```

The message of the exception that `java.net.URI.create` throws becomes the error:

```scala
println(errorFor(open, "open", "not a uri"))
// Illegal character in path at index 3: not a uri
```

`Options` has no counterpart of `Args#mapTry`. For an option, wrap the conversion in `scala.util.Try` inside `Options#mapOrFail`:

```scala
val endpoint: Options[java.net.URI] =
  Options.text("endpoint").mapOrFail { (text: String) =>
    scala.util.Try(java.net.URI.create(text)).toEither.left.map { e =>
      ValidationError(ValidationErrorType.InvalidValue, HelpDoc.p(e.getMessage))
    }
  }
```

### 6. Check That a Path Exists

The `exists` parameter of the path constructors checks the file system. `Exists.Yes` rejects a missing path, and `Exists.No` rejects an existing one. To require an existing input file:

```scala
val input = Command("cat", Args.file("input", exists = Exists.Yes))
```

A missing file fails before your program runs:

```scala
println(errorFor(input, "cat", "/no/such/file"))
// Path '/no/such/file' must exist.
```

No constructor accepts "an existing file or directory". For that rule, wrap a `PrimType.Path` in `Options.Single` or `Args.Single`:

```scala
val source: Options[java.nio.file.Path] =
  Options.Single("source", Vector.empty, PrimType.Path(PathType.Either, Exists.Yes))
val copy = Command("copy", source)
```

The help describes the rule, and a missing path fails the same way:

```scala
println(source.helpDoc.toPlaintext(color = false))
// --source <path>
//   An existing file or directory.
// 
// 
println(errorFor(copy, "copy", "--source", "/no/such/dir"))
// Path '/no/such/dir' must exist.
```

## Complete Example

The complete program combines a checked option and a converted argument:

```scala
import zio._
import zio.cli._

object FetchApp extends ZIOCliDefault {
  val port: Options[Int] =
    Options.integer("port").mapOrFail { (value: BigInt) =>
      if (value >= 1 && value <= 65535) Right(value.toInt)
      else Left(ValidationError(ValidationErrorType.InvalidValue, HelpDoc.p("Port must be between 1 and 65535.")))
    }

  val uri: Args[java.net.URI] = Args.text("uri").mapTry(java.net.URI.create)

  val fetch = Command("fetch", port, uri)

  val cliApp = CliApp.make("fetch", "1.0.0", HelpDoc.Span.text("Fetches a URI."), fetch) { case (port, uri) =>
    Console.printLine(s"Fetching $uri through port $port")
  }
}
```

## See Also

- [Value Types](value-types.md): the input each value type accepts, and the messages for type errors.
- [Validation and Errors](validation-and-errors.md): the `ValidationError` type and how `CliApp` reports errors.
