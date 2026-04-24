# Args

> The `Args` data type models command-line arguments. Arguments are name-less, position-based parameters passed to a command-line program that modify its behavior. For example, the Git CLI has a command named `git clone` that creates a copy of an existing repository. It has an argument called "repository" that represents the existing repository's path. To clone the **ZIO CLI** repository, it can be called this way:
```
git clone https://github.com/zio/zio-cli.git
```
where `https://github.com/zio/zio-cli.git` is the path of the repository.

The `Args` data type models command-line arguments. Arguments are name-less, position-based parameters passed to a command-line program that modify its behavior. For example, the Git CLI has a command named `git clone` that creates a copy of an existing repository. It has an argument called "repository" that represents the existing repository's path. To clone the **ZIO CLI** repository, it can be called this way:
```
git clone https://github.com/zio/zio-cli.git
```
where `https://github.com/zio/zio-cli.git` is the path of the repository.

In **ZIO CLI**, Arguments are represented by instances of class `Args[_]`. `Args[A]` is a description of the process of constructing an instance of `A` from a valid input of the CLI. It is not yet a specified argument for the CLI. In other words, an `Args[A]` defines a collection of valid arguments for a command and a way to construct a value `A` from them.

## Construction of basic Args
**ZIO CLI** offers a variety of methods to create basic `Args` that takes a string from the user as input and transform it into a value of the corresponding type. The benefit of using these methods is that it is automatically checked if they describe a value of the given type and, in that case, are transformed into it. In case of an invalid input, a `ValidationError` will be thrown. Note that each type has its own validation function.

An `Args` instance of a basic type carries a name attribute. This can be specified when being created, but it can also be skipped. In this last case, the name will be that of the type. An example with Boolean type:
```scala

val name = "name"

Args.bool(name) // Boolean Args with name
Args.bool       // Boolean Args named "boolean"
```

### Boolean Args
Produces an `Args[Boolean]`. It accepts as input `true` or `false`, while other pieces of text are not valid.
```scala
Args.bool(name)
```
### Path Args
They are used to produce an `Args[Path]`.
- Produces `Args[java.nio.file.Path]`.
```scala
Args.file(name) // Used for a file
```
- Produces `Args[java.nio.file.Path]`.
```scala
Args.directory(name) // Used for a directory
```
By default, the `CliApp` accepts paths pointing to files or directories that might or not exist. If you need to work with one of them exclusively you can use the trait `Exists`:
```scala
// Path can point to both existing or non-existing files or directories
Args.file(name, exists = Exists.Either)
Args.directory(name, exists = Exists.Either)

// Path must point to existing files or directories
Args.file(name, exists = Exists.Yes)
Args.directory(name, exists = Exists.Yes)

// Path must point to non-existing files or directories
Args.file(name, exists = Exists.Yes)
Args.directory(name, exists = Exists.Yes)
```
### Text Args
Produces an `Args[String]`.
```scala
Args.text(name)
```
### Numeric Args
Numeric `Args` produce `BigInt` and `BigDecimal`, so they can represent numbers of arbitrary size and precision. Note that the CLI checks that the input really represents a number.
- Produces `Args[BigDecimal]`.
```scala
Args.decimal(name)
```

- Produces `Args[BigInt]`.
```scala
Args.integer(name)
```

### Date/Time Args
The following methods produce `Args` whose type parameter is a Date/Time type of the `java.time` library.
- Produces `Args[java.time.Duration]`. The input must be a time-based amount of time in the ISO-8601 format, such as 'P1DT2H3M'.
```scala
Args.duration(name)
```
- Produces `Args[java.time.Instant]`. The input must be an instant in time in UTC format, such as 2007-12-03T10:15:30.00Z.
```scala
Args.instant(name)
```
- Produces `Args[java.time.LocalDate]`. The input must be a date in ISO_LOCAL_DATE format, such as 2007-12-03.
```scala
Args.localDate(name)
```
- Produces `Args[java.time.LocalDateTime]`. The input must be a date-time without a time-zone in the ISO-8601 format, such as 2007-12-03T10:15:30.
```scala
Args.localDateTime(name)
```
- Produces `Args[java.time.LocalTime]`. The input must be a time without a time-zone in the ISO-8601 format, such as 10:15:30.
```scala
Args.localTime(name)
```
- Produces `Args[java.time.MonthDay]`. The input must be a month-day in the ISO-8601 format such as 12-03.
```scala
Args.monthDay(name)
```
- Produces `Args[java.time.OffsetDateTime]`. The input must be a date-time with an offset from UTC/Greenwich in the ISO-8601 format, such as 2007-12-03T10:15:30+01:00.
```scala
Args.offsetDateTime(name)
```
- Produces `Args[java.time.OffsetTime]`. The input must be a time with an offset from UTC/Greenwich in the ISO-8601 format, such as 10:15:30+01:00.
```scala
Args.offsetTime(name)
```
- Produces `Args[java.time.Period]`. The input must be a date-based amount of time in the ISO-8601 format, such as 'P1Y2M3D'.
```scala
Args.period(name)
```
- Produces `Args[java.time.Year]`. The input must be a year in the ISO-8601 format, such as 2007.
```scala
Args.year(name)
```
- Produces `Args[java.time.YearMonth]`. The input must be a year-month in the ISO-8601 format, such as 2007-12.
```scala
Args.yearMonth(name)
```
- Produces `Args[java.time.ZonedDateTime]`. The input must be a date-time with a time-zone in the ISO-8601 format, such as 2007-12-03T10:15:30+01:00 Europe/Paris.
```scala
Args.zonedDateTime(name)
```
- Produces `Args[java.time.ZoneId]`. The input must be a time-zone ID, such as Europe/Paris.
```scala
Args.zoneId(name)
```
- Produces `Args[java.time.ZoneOffset]`. The input must be a time-zone offset from Greenwich/UTC, such as +02:00.
```scala
Args.zoneOffset(name)
```

## Combining and transforming Args
When constructing a command, you can specify only one argument. Thus, to create more complex `Args` it is necessary to use the following methods of the trait `Args`:
```scala
trait Args[A] {
  def ++[B](that: Args[B]): Args[(A, B)]              // Zip two args
  def +[A1 >: A]: Args[::[A1]]                        // Requires a non-empty list of Args of type A1
  def * : Args[List[A]]                               // Requires a list of Args of same type
  def atLeast(min: Int): Args[List[A]]                // Requires a list of Args with at least min elements
  def atMost(max: Int): Args[List[A]]                 // Requires a list of Args with at most max elements
  def between(min: Int, max: Int): Args[List[A]]      // Requires a list of Args with a bound on the number of elements
  def map[B](f: A => B): Args[B]                      // Applies a function f to the result of the Args
  def ??(that: String): Args[A]                       // Adds a string to the HelpDoc of the Args
}
```

### Adding Args
Operator `++` can be used to zip two arguments. It can be use to chain two arguments in a tuple. For example, `git clone` command has arguments `<repository>` and `<directory>`. The argument of this command can be created in the following manner:
```scala
val cloneArgs = Args.text("repository") ++ Args.text("directory")
```

The output of the CLI help will be:
```
COMMANDS

  clone <repository> <directory>
```

### Repeating Args
If we need an argument a repeated number of times, we can use the following operators:
- Method `*`

It creates a new `Args` that accepts a list of arguments of the same type. There are no restrictions on length. Take into account that there is no limit, so if there are arguments or options after using this methods, the CLI app will not read them unless they are not valid!
```scala
// Accepts a list, possibly empty, of texts.
Args.text.*
```
- Method `+`

It works as method `*` but the list of arguments cannot be empty. Do not mistake with method `++`!

- Method `between`

It creates an argument accepting a list of arguments of the type before whose length must be between `min` and `max` parameters.
```scala
Args.text.between(2,5) // Creates an arguments accepting a list of String of length between 2 and 5.
```
- Method `atLeast`

It creates an argument accepting a list of arguments of the type before whose length must be more than `min` parameter.
```scala
Args.text.atLeast(2) // Creates an arguments accepting a list of String of length more than 2.
```

- Method `atMost`

It creates an argument accepting a list of arguments of the type before whose length must be less than `max`parameter
```scala
Args.text.atMost(5) // Creates an arguments accepting a list of String of length less than 5.
```

### Transforming Args
Method `map` allows to transform the type parameter of `Args[A]`. It takes a function `f: A => B` as parameter that is applied when processing a user's input in a CLI app and returns `Args[B]`. This makes it easier to implement the business logic of a CLI app. For example, we will construct an `Args` that asks for a list of 12 decimals (one for each month) and a year. Then, it will create a new `Args` that store the year and the mean value in a custom type.
```scala

val data: Args[BigDecimal] = Args.decimal
val args: Args[(List[BigDecimal], BigInt)] = data.between(12, 12) ++ Args.integer

case class YearAndMean(year: BigInt, mean: BigDecimal)

val mappedArgs: Args[YearAndMean] = args.map {
  case (months, year) => YearAndMean(year, months.sum/12)
}
```

### Adding help
Method `??` allows adding information about an argument. The string is added after the current `HelpDoc` of the `Args`. We are going to create the `<repository>` argument of `git clone` to observe the effect of using `??`.

```scala
val repository = Args.text("repository")
  
/* HelpDoc of repository:
 *
 * <repository>
 *   A user-defined piece of text.
 * 
 */

```
Now we add a description of the argument:

```scala
val repositoryWithHelp = repository ?? "Path of the repository to be cloned."

/* HelpDoc of repositoryWithHelp:
 *
 * <repository>
 *   A user-defined piece of text.
 * 
 *   Path of the repository to be cloned.
 */
```
