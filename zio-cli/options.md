# Options

> The `Options` data type models command-line options. Contrary to arguments, options are named, position-independent parameters passed to a command-line program that modify its behavior. Note that the user must specify the name of the option just before its content. As an example, the Git CLI has a command named `git checkout` that allows changing between different development branches. It has different options that modify the functionality of the command like option `quiet`. This option allows suppressing feedback messages. It can be specified in the following manners:
```
git checkout --quiet
git checkout -q
```
Both `--quiet` and `-q` refer to the same option. `-q` is the alias of `quiet`, a shorter form of the option. Note that the alias is preceded only by `-`.

The `Options` data type models command-line options. Contrary to arguments, options are named, position-independent parameters passed to a command-line program that modify its behavior. Note that the user must specify the name of the option just before its content. As an example, the Git CLI has a command named `git checkout` that allows changing between different development branches. It has different options that modify the functionality of the command like option `quiet`. This option allows suppressing feedback messages. It can be specified in the following manners:
```
git checkout --quiet
git checkout -q
```
Both `--quiet` and `-q` refer to the same option. `-q` is the alias of `quiet`, a shorter form of the option. Note that the alias is preceded only by `-`.

In **ZIO CLI**, Options are represented by instances of class `Options[_]`. `Options[A]` is a description of the process of constructing an instance of `A` from a valid input of the CLI. It is not yet a specified option for the CLI. In other words, an instance of `Options[A]` defines a collection of valid commands and a way to construct a value `A` from them.

## Construction of basic Options
**ZIO CLI** offers a variety of methods to create basic `Options` that take a string from the user as input and transform it into a value of the corresponding type. The benefit of using these methods is that it is automatically checked if they describe a value of the given type and, in that case, are transformed into it. In case of an invalid input, a `ValidationError` will be thrown.

The following methods construct an `Options` with a `name` that requires an input of the corresponding type from the user. Observe that for `Options` it is mandatory to specify the `name`.
### Boolean Options
Produces an `Options[Boolean]`. It accepts as input `true` or `false`, while other pieces of text are not valid.
```scala

val name = "name"

Options.boolean(name)
```
### Enumeration Options
If we desire to allow the user to choose between different options, we can use the `enumeration` method. It must specify a name and tuples `(String, A)` to create an instance of `Options[A]`. A valid input from the user is a key of a tuple and it will produce the corresponding value. We can use this method if there are different ways to execute a command. As an example, the command `git commit` allows choosing between options `--all`, `--interactive` and `--patch` that control how the user specifies the changes to be committed. One way to implement this would use `enumeration`:
```scala
sealed trait Mode
case class All() extends Mode
case class Interactive() extends Mode
case class Patch() extends Mode

Options.enumeration[Mode]("mode")(("all", All()), ("interactive", Interactive()), ("patch", Patch()))
```
The output produced by the HelpDoc is
```
OPTIONS

  --mode all | interactive | patch
    One of the following cases: all, interactive, patch.
```

### Path Options
They are used to produce an `Options[Path]`.
- Produces `Options[java.nio.file.Path]`.
```scala
Options.file(name) // Used for a file
```
- Produces `Options[java.nio.file.Path]`.
```scala
Options.directory(name) // Used for a directory
```
By default, the `CliApp` accepts paths pointing to files or directories that might or not exist. If you need to work with one of them exclusively you can use the type `Exists`:
```scala
// Path can point to both existing or non-existing files or directories
Options.file(name, exists = Exists.Either)
Options.directory(name, exists = Exists.Either)

// Path must point to existing files or directories
Options.file(name, exists = Exists.Yes)
Options.directory(name, exists = Exists.Yes)

// Path must point to non-existing files or directories
Options.file(name, exists = Exists.Yes)
Options.directory(name, exists = Exists.Yes)
```
### Text Options
Produces an `Options[String]`.
```scala
Options.text(name)
```
### Numeric Options
Numeric `Options` produce `BigInt` and `BigDecimal`, so they can represent numbers of arbitrary size and precision. Note that the CLI checks that the input really represents a number.
- Produces `Options[BigDecimal]`.
```scala
Options.decimal(name)
```

- Produces `Options[BigInt]`.
```scala
Options.integer(name)
```

### Date/Time Options
The following methods produce `Options` whose type parameter is a Date/Time type of the `java.time` library.
- Produces `Options[java.time.Duration]`. The input must be a time-based amount of time in the ISO-8601 format, such as 'P1DT2H3M'.
```scala
Options.duration(name)
```
- Produces `Options[java.time.Instant]`. The input must be an instant in time in UTC format, such as 2007-12-03T10:15:30.00Z.
```scala
Options.instant(name)
```
- Produces `Options[java.time.LocalDate]`. The input must be a date in ISO_LOCAL_DATE format, such as 2007-12-03.
```scala
Options.localDate(name)
```
- Produces `Options[java.time.LocalDateTime]`. The input must be a date-time without a time-zone in the ISO-8601 format, such as 2007-12-03T10:15:30.
```scala
Options.localDateTime(name)
```
- Produces `Options[java.time.LocalTime]`. The input must be a time without a time-zone in the ISO-8601 format, such as 10:15:30.
```scala
Options.localTime(name)
```
- Produces `Options[java.time.MonthDay]`. The input must be a month-day in the ISO-8601 format such as 12-03.
```scala
Options.monthDay(name)
```
- Produces `Options[java.time.OffsetDateTime]`. The input must be a date-time with an offset from UTC/Greenwich in the ISO-8601 format, such as 2007-12-03T10:15:30+01:00.
```scala
Options.offsetDateTime(name)
```
- Produces `Options[java.time.OffsetTime]`. The input must be a time with an offset from UTC/Greenwich in the ISO-8601 format, such as 10:15:30+01:00.
```scala
Options.offsetTime(name)
```
- Produces `Options[java.time.Period]`. The input must be a date-based amount of time in the ISO-8601 format, such as 'P1Y2M3D'.
```scala
Options.period(name)
```
- Produces `Options[java.time.Year]`. The input must be a year in the ISO-8601 format, such as 2007.
```scala
Options.year(name)
```
- Produces `Options[java.time.YearMonth]`. The input must be a year-month in the ISO-8601 format, such as 2007-12.
```scala
Options.yearMonth(name)
```
- Produces `Options[java.time.ZonedDateTime]`. The input must be a date-time with a time-zone in the ISO-8601 format, such as 2007-12-03T10:15:30+01:00 Europe/Paris.
```scala
Options.zonedDateTime(name)
```
- Produces `Options[java.time.ZoneId]`. The input must be a time-zone ID, such as Europe/Paris.
```scala
Options.zoneId(name)
```
- Produces `Options[java.time.ZoneOffset]`. The input must be a time-zone offset from Greenwich/UTC, such as +02:00.
```scala
Options.zoneOffset(name)
```

### Combining and transforming options
We can use the following methods to create more complex Options:
```scala
trait Options[A] {
  def ++[A1 >: A, B](that: Options[B]): Options[(A1, B)]
  def |[A1 >: A](that: Options[A1]): Options[A1]
  def orElse[A1 >: A](that: Options[A1]): Options[A1] // same as |
  def orElseEither[B](that: Options[B]): Options[Either[A, B]] 
  def map[B](f: A => B): Options[B] 
  def optional: Options[Option[A]] 

  // Creates options with different aliases
  def alias(name: String, names: String*): Options[A]

  // Creates options that have a default value
  def withDefault[A1 >: A](value: A1): Options[A1]
}
```

### Adding Options
Operator `++` can be used to zip two options. It can be used to chain two options in a tuple. For example, `git checkout` command has options `--quiet` and `--force`. The options for this command can be created in the following manner:
```scala
val checkoutOptions = Options.boolean("quiet") ++ Options.boolean("force")
```

The output of the CLI help will be:
```
COMMANDS

  checkout [--quiet] [--force] 
```

### Choosing between Options
If we have more than one option and we need only one, we can create a new `Options` that allows to choose between two options using methods `|`, `orElse` and `orElseEither`. The difference between these three methods lies in the type parameter of the new `Options`. The user will be able to enter only the `Options` that he wants, triggering the desired functionality.
- Method `orElse`

`|` is just an alias for `orElse`. In the Git CLI, it is possible to choose between different options. This can be realized using `orElse`.
```scala

val all = Options.boolean("all")
val interactive = Options.boolean("all")
val patch = Options.boolean("all")

val orElseOption: Options[Boolean] = all | interactive | patch
```

- Method `orElseEither`

This method wraps the types in an `Either` class. This will be preferred if the types are very different.

```scala
val orElseEitherOption: Options[Either[Either[Boolean, Boolean], Boolean]] = all orElseEither interactive orElseEither patch
```
This is another way to implement the `[--all | --interactive | --patch]` option of command `git commit`.

### Transforming Options
- Method `map`

It allows to transform the type parameter of `Options[A]`. It takes a function `f: A => B` as parameter that is applied when processing the input of a user in a CLI app. This makes easier to implement the business logic of a CLI app. This allows to implement `[--all | --interactive | --patch]` option of command `git commit` using `orElse`. This is not possible without method `map`, because the result of using `orElse` is an option that returns true if some of the options are employed.
```scala

sealed trait Mode
case class All() extends Mode
case class Interactive() extends Mode
case class Patch() extends Mode

val all = Options.boolean("all").map(_ => All())
val interactive = Options.boolean("all").map(_ => Interactive())
val patch = Options.boolean("all").map(_ => Patch())

val alternative: Options[Mode] = all | interactive | patch
```

- Method `optional`

It wraps the option in an `Option` class. The resulting `Options` is now optional, so the user can choose to specify it or not. If it is specified, it will be wrapped in `Some()`. Without input from the user, it will produce `None`. For example, if we are designing a command-line application to manage databases, we might desire to retrieve data from a particular database. The user could specify an additional backup database. If there were an error connecting with the main database, the CLI app would try to retrieve data from the backup database.
```scala
val database = Options.file("database")
val backup = Options.file("backup").optional
val retrieve = Command("retrieve", database ++ backup)
```
The HelpDoc of `optionsDatabase` will be 
```
COMMANDS

  retrieve --database file [--backup file]  
```
The `[]` denotes that the parameter is optional.

- Method `withDefault`

It creates an option that will have a default parameter in case that the user does not input the option. It can be used, for example, to specify a default directory in which a CLI application may perform some task if the user does not specify another one.
```scala
val directory = Options.directory("directory").withDefault("C:\\Users\\YourUser\\Documents")
```
The output produced by the HelpDoc is
```
OPTIONS

  --directory directory
    A directory.

    This setting is optional. Default: 'C:\Users\YourUser\Documents'.
```

### Making an alias
Using method `alias` it is possible to give a shorter form of the name of the `Options`. This is used to make easier the input of options for the user. We will make an alias for option `--directory`. 
```scala
val directoryWithAlias = directory.alias("d")
```
The output produced by the HelpDoc in this case is
```
OPTIONS

  (-d, --directory directory)
    A directory.

    This setting is optional. Default: 'C:\Users\YourUser\Documents'.
```

### Adding help
Method `??` allows to add information about an options. The string is added after the current `HelpDoc` of the `Options`. We are going to recreate the `--quiet` option of `git checkout` to observe the effect of using `??`.
```scala
val quiet = Options.boolean("quiet")
  
/* HelpDoc of quiet:
 *
 * --quiet
 *   A true or false value.
 * 
 *   This setting is optional. Default: 'false'.
 */

```
Now we add a description of the option:

```scala
val quietWithHelp = quiet ?? "Suppress feedback messages."

/* HelpDoc of quietWithHelp:
 *
 * --quiet
 *   A true or false value.
 * 
 *   Suppress feedback messages.
 * 
 *   This setting is optional. Default: 'false'.
 */
```

### Example
As an example, we are going to construct an instance of `Options[Git]` that describes the options of the command `git commit`. We are going to implement only an option `--msg` that includes a commit message and alternatives between `-a`, `--interactive` and `--patch`. First, we create the basic options. Observe how we add an alias to `all`and `msg`.
```scala

val all = Options.boolean("all").alias("a")
val interactive = Options.boolean("interactive")
val patch = Options.boolean("patch")

val msg = Options.text("msg").alias("m")
```
Then we combine options:
- using `orElse` to combine `all`, `interactive` and `patch`,
- adding it to `msg`.

```scala

val alternative = all | interactive | patch

val options = alternative ++ msg
```
The output of the HelpDoc of `options` is:
```
USAGE

  $ commit [(-a, -all)]|[--interactive]|[--patch] (-m, --msg text)

OPTIONS

  (-a, --all)
    A true or false value.

    This setting is optional. Default: 'false'.

  --interactive
    A true or false value.

    This setting is optional. Default: 'false'.

  --patch
    A true or false value.

    This setting is optional. Default: 'false'.

  (-m, --msg text)
    A user-defined piece of text.
```
