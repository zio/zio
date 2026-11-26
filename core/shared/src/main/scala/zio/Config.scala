/*
 * Copyright 2022-2024 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio

import scala.annotation.tailrec
import scala.util.control.NoStackTrace

/**
 * A [[zio.Config]] describes the structure of some configuration data.
 *
 * A `Config[A]` holds no data: it describes how to read a value of type `A`,
 * and a [[ConfigProvider]] loads it. `ZIO.config` loads it with the provider of
 * the current fiber, which by default reads each value from the environment
 * variables and then from the system properties. Primitive descriptions such as
 * `Config.int("port")` or `Config.string("host")` read one value under a name.
 * `nested` puts a description under a parent name, `++` (or `zip`) combines two
 * descriptions into a tuple, `||` (or `orElse`) reads another description when
 * this one fails, and `optional` and `withDefault` handle missing data. `map`,
 * `mapOrFail` and `validate` convert or check the value, `Config.listOf` and
 * `Config.table` read sequences and maps, and `Config.secret` reads a value
 * whose `toString` hides it.
 *
 * {{{
 * final case class ServerConfig(host: String, port: Int, debug: Boolean)
 *
 * val serverConfig: Config[ServerConfig] =
 *   (Config.string("host") ++ Config.int("port") ++ Config.boolean("debug").withDefault(false)).map {
 *     case (host, port, debug) => ServerConfig(host, port, debug)
 *   }.nested("server")
 *
 * val provider: ConfigProvider =
 *   ConfigProvider.fromMap(Map("server.host" -> "api.internal", "server.port" -> "8443"))
 *
 * val loaded: IO[Config.Error, ServerConfig] = provider.load(serverConfig)
 * // succeeds with ServerConfig(api.internal,8443,false)
 * }}}
 *
 * @see
 *   [[https://zio.dev/reference/configuration/ Configuration]]
 */
sealed trait Config[+A] { self =>

  /**
   * Returns a new config that is the composition of this config and the
   * specified config.
   */
  def ++[B](that: => Config[B])(implicit zippable: Zippable[A, B]): Config[zippable.Out] =
    Config.Zipped[A, B, zippable.Out](self, Config.defer(that), zippable)

  /**
   * Returns a config whose structure is preferentially described by this
   * config, but which falls back to the specified config if there is an issue
   * reading from this config.
   */
  def ||[A1 >: A](that: => Config[A1]): Config[A1] = Config.Fallback(self, Config.defer(that))

  /**
   * Adds a description to this configuration, which is intended for humans.
   */
  def ??(label: => String): Config[A] = Config.defer(Config.Described(self, label))

  /**
   * Returns a new config whose structure is the same as this one, but which
   * produces a different Scala value, constructed using the specified function.
   */
  def map[B](f: A => B): Config[B] = self.mapOrFail(a => Right(f(a)))

  /**
   * Returns a new config whose structure is the same as this one, but which may
   * produce a different Scala value, constructed using the specified fallible
   * function.
   */
  def mapOrFail[B](f: A => Either[Config.Error, B]): Config[B] = Config.MapOrFail(self, f)

  /**
   * Returns a new config whose structure is the same as this one, but which may
   * produce a different Scala value, constructed using the specified function,
   * which may throw exceptions that will be translated into validation errors.
   */
  def mapAttempt[B](f: A => B): Config[B] =
    self.mapOrFail { a =>
      try Right(f(a))
      catch {
        case ex if nonFatal(ex) => Left(Config.Error.InvalidData(Chunk.empty, ex.getMessage))
      }
    }

  /**
   * Returns a new config that has this configuration nested as a property of
   * the specified name.
   */
  def nested(name: => String): Config[A] =
    Config.defer(Config.Nested(name, self))

  /**
   * Returns a new config that has this configuration nested under the path made
   * of `name` followed by `names`, with `name` outermost: `nested("app", "db")`
   * reads this configuration under `app.db`.
   */
  def nested(name: => String, names: String*): Config[A] =
    Config.defer(Config.Nested(name, names.foldRight(self)((name, config) => Config.Nested(name, config))))

  /**
   * Returns an optional version of this config, which is `Some` when reading
   * succeeds and `None` when reading fails with missing data only (see
   * `Config.Error#isMissingDataOnly`). This includes a composite config with
   * only part of its data missing: `(Config.string("host") ++
   * Config.int("port")).optional` is `None` when `host` is set and `port` is
   * not. When the data is present but invalid, loading fails.
   *
   * {{{
   * val cache: Config[Option[(String, Int)]] = (Config.string("host") ++ Config.int("port")).optional
   *
   * val partial: IO[Config.Error, Option[(String, Int)]] =
   *   ConfigProvider.fromMap(Map("host" -> "cache.local")).load(cache)
   * // succeeds with None
   * val invalid: IO[Config.Error, Option[(String, Int)]] =
   *   ConfigProvider.fromMap(Map("host" -> "cache.local", "port" -> "eighty")).load(cache)
   * // fails with a Config.Error.InvalidData error
   * }}}
   */
  def optional: Config[Option[A]] = Config.Optional(self)

  /**
   * A named version of `||`.
   */
  def orElse[A1 >: A](that: => Config[A1]): Config[A1] = self || that

  /**
   * Returns configuration which reads from this configuration, but which falls
   * back to the specified configuration if reading from this configuration
   * fails with an error satisfying the specified predicate.
   */
  def orElseIf(condition: Config.Error => Boolean): Config.OrElse[A] =
    new Config.OrElse(self, condition)

  /**
   * Returns a new config that describes a sequence of values, each of which has
   * the structure of this config.
   */
  def repeat: Config[Chunk[A]] = Config.Sequence(self)

  /**
   * Returns a new configuration which reads from this configuration and uses
   * the resulting value to determine the configuration to read from.
   */
  def switch[A1 >: A, B](f: (A1, Config[B])*): Config[B] =
    Config.Switch(self, f.toMap)

  /**
   * Returns a new config that describes the same structure as this one, but
   * which performs validation during loading.
   *
   * When `f` returns `false`, loading fails with `Config.Error.InvalidData` and
   * `message`. The error has the path at which `validate` is applied, so
   * `Config.int("workers").validate(...)` reports an empty path while
   * `Config.int.validate(...).nested("workers")` reports `workers`.
   */
  def validate(message: => String)(f: A => Boolean): Config[A] =
    self.mapOrFail(a => if (!f(a)) Left(Config.Error.InvalidData(Chunk.empty, message)) else Right(a))

  /**
   * Returns a new config whose structure is the same as this one, but which may
   * produce a different Scala value, constructed using the specified partial
   * function, failing with the specified validation error if the partial
   * function is not defined.
   */
  def validateWith[B](message: => String)(pf: PartialFunction[A, B]): Config[B] =
    self.mapOrFail(a => pf.lift(a).toRight(Config.Error.InvalidData(Chunk.empty, message)))

  /**
   * Returns a new config that describes the same structure as this one, but has
   * the specified default value in case the information cannot be found.
   */
  def withDefault[A1 >: A](default: => A1): Config[A1] =
    self.orElseIf(_.isMissingDataOnly)(Config.succeed(default))

  /**
   * A named version of `++`.
   */
  def zip[B](that: => Config[B])(implicit z: Zippable[A, B]): Config[z.Out] = self ++ that

  /**
   * Returns a new configuration that is the composition of this configuration
   * and the specified configuration, combining their values using the function
   * `f`.
   */
  def zipWith[B, C](that: => Config[B])(f: (A, B) => C): Config[C] =
    self.zip(that).map(f.tupled)
}

/**
 * Constructors of [[Config]] descriptions: primitives such as `int`, `string`,
 * `boolean` and `duration`, collections such as `listOf`, `setOf` and `table`,
 * and `succeed`, `fail` and `defer`. Each primitive comes in two forms: without
 * a name it reads the value at the current path (for use inside `listOf`,
 * `table` or `nested`), and with a name it reads the property of that name,
 * like `.nested(name)`.
 *
 * The object also holds the cases of a description (`Primitive`, `Nested`,
 * `Zipped`, `Fallback` and the others) that a [[ConfigProvider]] inspects to
 * load it, the `Error` type, and `Secret`.
 */
object Config {

  /**
   * A configuration value, such as a password or an API key, whose `toString`
   * does not show its characters. Two secrets are equal when they hold the same
   * characters in the same order. `Config.secret` reads a value as a `Secret`.
   *
   * {{{
   * val apiKey: Config.Secret = Config.Secret("sk-test-4417")
   *
   * val shown: String = apiKey.toString
   * // Secret(<redacted>)
   * val same: Boolean = apiKey == Config.Secret("sk-test-4417")
   * // true
   * }}}
   */
  final class Secret private (private val raw: Array[Char]) { self =>
    override def equals(that: Any): Boolean =
      that match {
        case that: Secret => {
          val selfLength = self.raw.length
          val thatLength = that.raw.length
          var isEqual    = if (selfLength == thatLength) 0 else 1
          var i          = 0
          while (i < selfLength) {
            val char = if (i >= thatLength) 'a' else that.raw(i)
            isEqual = isEqual | (self.raw(i) ^ char)
            i += 1
          }
          isEqual == 0
        }
        case _ => false
      }

    override def hashCode(): Int = value.hashCode

    override def toString(): String = "Secret(<redacted>)"

    /**
     * The operations of this secret that change it in place. Each one needs an
     * implicit `Unsafe`.
     */
    object unsafe {

      /**
       * Overwrites each character of this secret with the character whose code
       * is 0. Later calls to `value` and `stringValue` return only such
       * characters.
       */
      def wipe(implicit unsafe: Unsafe): Unit =
        (0 until raw.length).foreach(i => raw(i) = 0)
    }

    /** Returns the characters of this secret as a `Chunk`. */
    def value: Chunk[Char] = Chunk.fromArray(raw)

    /** Returns the characters of this secret as a new `String`. */
    def stringValue = new String(raw)
  }

  /**
   * Constructors and an extractor of `Config.Secret`. `Secret` is also a
   * function from `Chunk[Char]` to `Secret`.
   */
  object Secret extends (Chunk[Char] => Secret) {

    /** Creates a secret that holds a copy of the characters of `chunk`. */
    def apply(chunk: Chunk[Char]): Secret = new Secret(chunk.toArray)

    /** Creates a secret that holds the characters of `cs`. */
    def apply(cs: CharSequence): Secret = Secret(cs.toString)

    /** Creates a secret that holds the characters of `s`. */
    def apply(s: String): Secret = new Secret(s.toCharArray)

    /**
     * Extracts the characters of `secret` as a `Chunk`. It returns `Some`, so
     * the pattern `Secret(chars)` matches every `Secret`.
     */
    def unapply(secret: Secret): Some[Chunk[Char]] = Some(secret.value)
  }

  /**
   * A leaf of a config description: it turns one text value into an `A` with
   * `parse`. Providers built with `ConfigProvider.fromFlat` look up the text at
   * the primitive's path and call `parse`, except for `Constant` and `Fail`,
   * which read no text.
   */
  sealed trait Primitive[+A] extends Config[A] { self =>

    /**
     * Returns a short description of this primitive for messages and prompts,
     * such as `an integer property` for `Integer` or `a text property` for
     * `Text`.
     */
    final def description: String =
      (self: Primitive[_]) match {
        case Bool           => "a boolean property"
        case Constant(_)    => "a constant property"
        case Decimal        => "a decimal property"
        case Duration       => "a duration property"
        case Fail(_)        => "a static failure"
        case Integer        => "an integer property"
        case LocalDateTime  => "a local date-time property"
        case LocalDate      => "a local date property"
        case LocalTime      => "a local time property"
        case OffsetDateTime => "an offset date-time property"
        case SecretType     => "a secret property"
        case Text           => "a text property"
      }

    /**
     * Returns a `Config.Error.MissingData` error with an empty path saying that
     * this primitive is missing under `name`, such as `Expected an integer
     * property with name port` for `Integer`.
     */
    final def missingError(name: String): Config.Error =
      Config.Error.MissingData(Chunk.empty, s"Expected ${description} with name ${name}")

    /**
     * Parses `text` into an `A`, or returns the error that explains why it
     * can't. The error has an empty path; the provider adds the path of the
     * value.
     */
    def parse(text: String): Either[Config.Error, A]
  }

  /**
   * A config description built from other descriptions, such as `Nested`,
   * `Zipped` or `Fallback`.
   */
  sealed trait Composite[+A] extends Config[A]

  /**
   * The primitive behind `Config.boolean`. Ignoring case, it reads `true`,
   * `yes`, `on` and `1` as `true`, and `false`, `no`, `off` and `0` as `false`.
   * Other text, such as `y`, fails, and so does text with surrounding spaces
   * outside a sequence; the elements of a sequence are trimmed before they are
   * parsed.
   */
  case object Bool extends Primitive[Boolean] {

    /**
     * Parses `text` as described on `Bool`, or fails with an `InvalidData`
     * error.
     */
    final def parse(text: String): Either[Config.Error, Boolean] = text.toLowerCase match {
      case "true" | "yes" | "on" | "1"  => Right(true)
      case "false" | "no" | "off" | "0" => Right(false)
      case _                            => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a boolean value, but found ${text}"))
    }
  }

  /**
   * The primitive behind `Config.succeed`: it produces `value` without reading
   * any data.
   */
  final case class Constant[A](value: A) extends Primitive[A] {

    /** Returns `value` and ignores `text`. */
    final def parse(text: String): Either[Config.Error, A] = Right(value)
  }

  /**
   * The primitive behind `Config.bigDecimal`, `Config.double` and
   * `Config.float`. It parses the text with `BigDecimal(text)`, so it reads
   * forms such as `1.5`, `-0.25` and `1e3`, and rejects surrounding spaces,
   * `NaN` and `Infinity`.
   */
  case object Decimal extends Primitive[BigDecimal] {

    /**
     * Parses `text` as a `BigDecimal`, or fails with an `InvalidData` error.
     */
    final def parse(text: String): Either[Config.Error, BigDecimal] = try Right(BigDecimal(text))
    catch {
      case ex if nonFatal(ex) =>
        Left(Config.Error.InvalidData(Chunk.empty, s"Expected a decimal value, but found ${text}"))
    }
  }

  /**
   * The primitive behind `Config.duration`. It first parses the text as an
   * ISO-8601 duration with `java.time.Duration.parse`, such as `PT1H`, `P1D` or
   * `pt1h` (case is ignored), and when that fails, as a Scala duration, such as
   * `1 hour`, `5 seconds`, `500ms` or `1.5 hours`; `Inf` gives
   * `Duration.Infinity`. A negative duration such as `PT-5S` or `-5 seconds`
   * stays negative.
   */
  case object Duration extends Primitive[zio.Duration] {

    /**
     * Parses `text` as described on `Duration`. Text that neither parser reads,
     * such as `5` (no unit) or `1 fortnight`, fails with an `InvalidData`
     * error.
     */
    final def parse(text: String): Either[Config.Error, zio.Duration] =
      try {
        Right(java.time.Duration.parse(text))
      } catch {
        case _: java.time.format.DateTimeParseException =>
          try {
            Right(zio.Duration.fromScala(scala.concurrent.duration.Duration(text)))
          } catch {
            case _: NumberFormatException =>
              Left(Config.Error.InvalidData(Chunk.empty, s"Expected a duration value, but found ${text}"))
          }
      }
  }

  /**
   * The primitive behind `Config.fail`. Providers built with
   * `ConfigProvider.fromFlat` load it as a `MissingData` error with `message`
   * at the current path, without reading any data.
   */
  final case class Fail(message: String) extends Primitive[Nothing] {

    /**
     * Returns an `Unsupported` error with `message` and an empty path.
     * Providers built with `ConfigProvider.fromFlat` do not call it.
     */
    final def parse(text: String): Either[Config.Error, Nothing] = Left(Config.Error.Unsupported(Chunk.empty, message))
  }

  /**
   * The config built by `||` and `orElse`: it reads `first`, and when that
   * fails with an error for which `condition` returns `true`, it reads
   * `second`. When both fail, the error is the `Config.Error.Or` of the two
   * errors.
   */
  sealed class Fallback[A] protected (val first: Config[A], val second: Config[A])
      extends Composite[A]
      with Product
      with Serializable { self =>

    /**
     * Returns `true` when `that` is a `Fallback`, including the subclasses
     * `Optional` and `FallbackWith`.
     */
    def canEqual(that: Any): Boolean =
      that.isInstanceOf[Fallback[_]]

    /**
     * Returns whether a failure of `first` with `error` lets this config read
     * `second`. It returns `true` here; `Optional` and `FallbackWith` override
     * it.
     */
    def condition(error: Config.Error): Boolean =
      true
    override def equals(that: Any): Boolean =
      that match {
        case that: Fallback[_] => that.canEqual(self) && self.first == that.first && self.second == that.second
        case _                 => false
      }
    override def hashCode: Int =
      (first, second).##

    /** Returns 2, the number of elements: `first` and `second`. */
    def productArity: Int =
      2

    /**
     * Returns `first` for 0 and `second` for 1. Other indices throw a
     * `java.lang.IndexOutOfBoundsException`.
     */
    def productElement(n: Int): Any =
      n match {
        case 0 => first
        case 1 => second
        case _ => throw new IndexOutOfBoundsException(n.toString)
      }
  }

  /** Constructor and extractor of `Fallback`. */
  object Fallback {

    /**
     * Creates a `Fallback` that reads `first` and, when it fails with any
     * error, `second`.
     */
    def apply[A](first: Config[A], second: Config[A]): Fallback[A] = new Fallback(first, second)

    /**
     * Extracts `first` and `second`. It also matches `Optional` and
     * `FallbackWith`, which extend `Fallback`.
     */
    def unapply[A](fallback: Fallback[A]): Option[(Config[A], Config[A])] = Some((fallback.first, fallback.second))
  }

  /**
   * The config built by `optional`: it reads `config` as `Some`, and produces
   * `None` when reading fails with an error for which `isMissingDataOnly` is
   * `true`.
   */
  final case class Optional[A](config: Config[A])
      extends Fallback[Option[A]](config.map(Some(_)), Config.succeed(None)) {
    override def condition(error: Config.Error): Boolean = error.isMissingDataOnly
  }

  /**
   * The config built by `orElseIf`: a `Fallback` that reads `second` only when
   * `f` returns `true` for the error of `first`.
   */
  final case class FallbackWith[A](
    override val first: Config[A],
    override val second: Config[A],
    f: Config.Error => Boolean
  ) extends Fallback[A](first, second) {
    override def condition(error: Config.Error): Boolean =
      f(error)
  }

  /**
   * The primitive behind `Config.bigInt`, `Config.int` and `Config.long`. It
   * parses the text with `BigInt(text)`, so it reads decimal digits with an
   * optional sign (`42`, `+42`, `-42`), and rejects surrounding spaces, decimal
   * points, `0x` prefixes and underscores.
   */
  case object Integer extends Primitive[BigInt] {

    /**
     * Parses `text` as a `BigInt`, or fails with an `InvalidData` error.
     */
    final def parse(text: String): Either[Config.Error, BigInt] = try Right(BigInt(text))
    catch {
      case ex if nonFatal(ex) =>
        Left(Config.Error.InvalidData(Chunk.empty, s"Expected an integer value, but found ${text}"))
    }
  }

  /**
   * The config built by `??`: `config` with a `description` for people.
   * Providers built with `ConfigProvider.fromFlat` load `config` and ignore the
   * description.
   */
  final case class Described[A](config: Config[A], description: String) extends Composite[A]

  /**
   * The config built by `Config.defer`: a provider calls `thunk` to get the
   * config when it loads it.
   */
  final case class Lazy[A](thunk: () => Config[A]) extends Composite[A]

  /**
   * The primitive behind `Config.localDateTime`. It parses ISO-8601 text such
   * as `2024-03-15T10:15:30` or `2024-03-15T10:15` with
   * `java.time.LocalDateTime.parse`; a space in place of the `T` fails.
   */
  case object LocalDateTime extends Primitive[java.time.LocalDateTime] {

    /**
     * Parses `text` as described on `LocalDateTime`, or fails with an
     * `InvalidData` error.
     */
    final def parse(text: String): Either[Config.Error, java.time.LocalDateTime] =
      try
        Right(java.time.LocalDateTime.parse(text))
      catch {
        case ex if nonFatal(ex) =>
          Left(Config.Error.InvalidData(Chunk.empty, s"Expected a local date-time value, but found ${text}"))
      }
  }

  /**
   * The primitive behind `Config.localDate`. It parses ISO-8601 text such as
   * `2024-03-15` with `java.time.LocalDate.parse`; `2024-3-15` and `15/03/2024`
   * fail.
   */
  case object LocalDate extends Primitive[java.time.LocalDate] {

    /**
     * Parses `text` as described on `LocalDate`, or fails with an `InvalidData`
     * error.
     */
    final def parse(text: String): Either[Config.Error, java.time.LocalDate] =
      try
        Right(java.time.LocalDate.parse(text))
      catch {
        case ex if nonFatal(ex) =>
          Left(Config.Error.InvalidData(Chunk.empty, s"Expected a local date value, but found ${text}"))
      }
  }

  /**
   * The primitive behind `Config.localTime`. It parses ISO-8601 text such as
   * `10:15`, `10:15:30` or `10:15:30.123` with `java.time.LocalTime.parse`.
   */
  case object LocalTime extends Primitive[java.time.LocalTime] {

    /**
     * Parses `text` as described on `LocalTime`, or fails with an `InvalidData`
     * error.
     */
    final def parse(text: String): Either[Config.Error, java.time.LocalTime] =
      try
        Right(java.time.LocalTime.parse(text))
      catch {
        case ex if nonFatal(ex) =>
          Left(Config.Error.InvalidData(Chunk.empty, s"Expected a local time value, but found ${text}"))
      }
  }

  /**
   * The config built by `map`, `mapOrFail`, `mapAttempt`, `validate` and
   * `validateWith`: it reads `original` and converts the value with
   * `mapOrFail`. Providers built with `ConfigProvider.fromFlat` add the path at
   * which this config sits in front of the path of an error that `mapOrFail`
   * returns.
   */
  final case class MapOrFail[A, B](original: Config[A], mapOrFail: A => Either[Config.Error, B]) extends Composite[B]

  /**
   * The config built by `nested`: it reads `config` under the property `name`.
   */
  final case class Nested[A](name: String, config: Config[A]) extends Composite[A]

  /**
   * The primitive behind `Config.offsetDateTime`. It parses ISO-8601 text with
   * an offset, such as `2024-03-15T10:15:30+01:00` or `2024-03-15T10:15:30Z`,
   * with `java.time.OffsetDateTime.parse`; text without an offset fails.
   */
  case object OffsetDateTime extends Primitive[java.time.OffsetDateTime] {

    /**
     * Parses `text` as described on `OffsetDateTime`, or fails with an
     * `InvalidData` error.
     */
    final def parse(text: String): Either[Config.Error, java.time.OffsetDateTime] =
      try
        Right(java.time.OffsetDateTime.parse(text))
      catch {
        case ex if nonFatal(ex) =>
          Left(Config.Error.InvalidData(Chunk.empty, s"Expected an offset date-time value, but found ${text}"))
      }
  }

  /**
   * The primitive behind `Config.secret`: it reads the text unchanged as a
   * `Config.Secret`.
   *
   * As an element of a sequence, such as in `Config.listOf("tokens",
   * Config.secret)`, the text is split at the sequence delimiter and each part
   * is trimmed.
   */
  case object SecretType extends Primitive[Secret] {

    /** Returns `text` as a `Config.Secret`; it does not fail. */
    final def parse(text: String): Either[Config.Error, Secret] = Right(
      Secret(text)
    )
  }

  /**
   * The config built by `repeat` and `Config.chunkOf`: it reads a sequence of
   * values, each described by `config`.
   */
  final case class Sequence[A](config: Config[A]) extends Composite[Chunk[A]]

  /**
   * The config built by `switch`: it reads `config`, then reads the config that
   * `map` holds for the value read. A value with no entry in `map` fails with
   * an `InvalidData` error.
   */
  final case class Switch[A, B](config: Config[A], map: Map[A, Config[B]]) extends Composite[B]

  /**
   * The config built by `Config.table`: it reads a map from the name of each
   * property under the current path to its value, read with `valueConfig`.
   */
  final case class Table[V](valueConfig: Config[V]) extends Composite[Map[String, V]]

  /**
   * The primitive behind `Config.string`: it reads the text unchanged,
   * including empty text and surrounding spaces.
   *
   * As an element of a sequence, such as in `Config.listOf("hosts",
   * Config.string)`, the text is split at the sequence delimiter and each part
   * is trimmed.
   */
  case object Text extends Primitive[String] {

    /** Returns `text` unchanged. */
    final def parse(text: String): Either[Config.Error, String] = Right(text)
  }

  /**
   * The config built by `++` and `zip`: it reads `left` and `right` and
   * combines their values with `zippable`. When both fail, the error is the
   * `Config.Error.And` of the two errors.
   */
  final case class Zipped[A, B, C](left: Config[A], right: Config[B], zippable: Zippable.Out[A, B, C])
      extends Composite[C]

  /**
   * The possible ways that loading configuration data may fail.
   *
   * An error is an `Exception` without a stack trace, and `getMessage` returns
   * the same text as `toString`, such as `(Missing data at server.port:
   * Expected server.port to be set in properties)`. `&&` and `||` combine
   * errors, `prefixed` adds a path in front of the paths of the errors, and
   * `isMissingDataOnly` tells whether only data was missing.
   *
   * {{{
   * val missing: Config.Error = Config.Error.MissingData(Chunk("port"), "Expected port to be set in properties")
   * val invalid: Config.Error = Config.Error.InvalidData(Chunk("port"), "Expected an integer value")
   *
   * val bothMissing: Boolean = (missing && missing).isMissingDataOnly
   * // true
   * val oneInvalid: Boolean = (missing || invalid).isMissingDataOnly
   * // false
   * val message: String = missing.prefixed(Chunk("server")).getMessage
   * // (Missing data at server.port: Expected port to be set in properties)
   * }}}
   */
  sealed trait Error extends Exception with NoStackTrace { self =>
    import Error._

    /**
     * Returns an `And` error: this error and `that` both happened, for example
     * when both sides of a `zip` failed.
     */
    def &&(that: Error): Error = And(self, that)

    /**
     * Returns an `Or` error: this error happened and so did `that`, the error
     * of the alternative, for example when both sides of an `orElse` failed.
     */
    def ||(that: Error): Error = Or(self, that)

    /**
     * Folds this error into a `Z` from the leaves up. The first four functions
     * turn each `InvalidData`, `MissingData`, `SourceUnavailable` and
     * `Unsupported` error into a `Z` from its path and message (and cause), and
     * `andCase0` and `orCase0` combine the results for the left and right sides
     * of each `And` and `Or`.
     */
    final def fold[Z](
      invalidDataCase0: (Chunk[String], String) => Z,
      missingDataCase0: (Chunk[String], String) => Z,
      sourceUnavailableCase0: (Chunk[String], String, Cause[Throwable]) => Z,
      unsupportedCase0: (Chunk[String], String) => Z
    )(
      andCase0: (Z, Z) => Z,
      orCase0: (Z, Z) => Z
    ): Z =
      foldContext(()) {
        new Folder[Unit, Z] {
          def invalidDataCase(context: Unit, path: Chunk[String], message: String): Z =
            invalidDataCase0(path, message)
          def missingDataCase(context: Unit, path: Chunk[String], message: String): Z =
            missingDataCase0(path, message)
          def sourceUnavailableCase(context: Unit, path: Chunk[String], message: String, cause: Cause[Throwable]): Z =
            sourceUnavailableCase0(path, message, cause)
          def unsupportedCase(context: Unit, path: Chunk[String], message: String): Z =
            unsupportedCase0(path, message)
          def andCase(context: Unit, left: Z, right: Z): Z =
            andCase0(left, right)
          def orCase(context: Unit, left: Z, right: Z): Z =
            orCase0(left, right)
        }
      }

    /**
     * Folds this error into a `Z` with `folder`, passing `context` to each of
     * its methods. Like `fold`, it combines the results for the left and right
     * sides of each `And` and `Or` with `andCase` and `orCase`.
     */
    final def foldContext[C, Z](context: C)(folder: Folder[C, Z]): Z = {
      import folder._
      sealed trait ErrorCase

      case object AndCase extends ErrorCase
      case object OrCase  extends ErrorCase

      @tailrec
      def loop(in: List[Error], out: List[Either[ErrorCase, Z]]): List[Z] =
        in match {
          case InvalidData(path, message) :: errors =>
            loop(errors, Right(invalidDataCase(context, path, message)) :: out)
          case MissingData(path, message) :: errors =>
            loop(errors, Right(missingDataCase(context, path, message)) :: out)
          case SourceUnavailable(path, message, cause) :: errors =>
            loop(errors, Right(sourceUnavailableCase(context, path, message, cause)) :: out)
          case Unsupported(path, message) :: errors =>
            loop(errors, Right(unsupportedCase(context, path, message)) :: out)
          case And(left, right) :: errors =>
            loop(left :: right :: errors, Left(AndCase) :: out)
          case Or(left, right) :: errors =>
            loop(left :: right :: errors, Left(OrCase) :: out)

          case Nil =>
            out.foldLeft[List[Z]](List.empty) {
              case (acc, Right(errors)) => errors :: acc
              case (acc, Left(AndCase)) =>
                val left :: right :: errors = (acc: @unchecked)
                andCase(context, left, right) :: errors
              case (acc, Left(OrCase)) =>
                val left :: right :: errors = (acc: @unchecked)
                orCase(context, left, right) :: errors
            }
        }
      loop(List(self), List.empty).head
    }

    /**
     * Returns `true` when each leaf of this error, through `And` and `Or`, is a
     * `MissingData`. `withDefault` and `optional` recover only from such
     * errors.
     */
    final def isMissingDataOnly: Boolean =
      foldContext(())(Folder.IsMissingDataOnly)

    /**
     * Returns this error with `prefix` added in front of the path of each leaf
     * error, through `And` and `Or`.
     */
    def prefixed(prefix: Chunk[String]): Error

    override def getMessage(): String = toString()
  }

  /** The cases of `Config.Error`, and the `Folder` that folds them. */
  object Error {

    /**
     * Both `left` and `right` happened, for example when both sides of a `zip`
     * failed. `toString` joins the two errors with `and` inside parentheses.
     */
    final case class And(left: Error, right: Error) extends Error {

      /** Returns this error with `prefix` added to the paths of both sides. */
      def prefixed(prefix: Chunk[String]): And =
        copy(left = left.prefixed(prefix), right = right.prefixed(prefix))

      override def toString(): String = s"(${left.toString()} and ${right.toString()})"
    }

    /**
     * Data was found at `path` but could not be read, for example text that is
     * not a number for `Config.int`.
     */
    final case class InvalidData(path: Chunk[String] = Chunk.empty, message: String) extends Error {

      /** Returns this error with `prefix` added in front of `path`. */
      def prefixed(prefix: Chunk[String]): InvalidData = copy(path = prefix ++ path)

      override def toString(): String = s"(Invalid data at ${path.mkString(".")}: ${message})"
    }

    /**
     * No data was found at `path`. `message` names what the provider looked
     * for, such as `Expected PORT to be set in the environment`.
     */
    final case class MissingData(path: Chunk[String] = Chunk.empty, message: String) extends Error {

      /** Returns this error with `prefix` added in front of `path`. */
      def prefixed(prefix: Chunk[String]): MissingData = copy(path = prefix ++ path)

      override def toString(): String = s"(Missing data at ${path.mkString(".")}: ${message})"
    }

    /**
     * `left` happened, and `right` happened while trying the alternative, for
     * example when both sides of an `orElse` failed or when both providers of
     * `ConfigProvider#orElse` failed. `toString` joins the two errors with `or`
     * inside parentheses.
     */
    final case class Or(left: Error, right: Error) extends Error {

      /** Returns this error with `prefix` added to the paths of both sides. */
      def prefixed(prefix: Chunk[String]): Or =
        copy(left = left.prefixed(prefix), right = right.prefixed(prefix))

      override def toString(): String = s"(${left.toString()} or ${right.toString()})"
    }

    /**
     * The source of the data at `path` could not be read, for example when
     * reading the environment or the console failed. `cause` holds the
     * underlying failure.
     */
    final case class SourceUnavailable(path: Chunk[String] = Chunk.empty, message: String, cause: Cause[Throwable])
        extends Error {

      /** Returns this error with `prefix` added in front of `path`. */
      def prefixed(prefix: Chunk[String]): SourceUnavailable = copy(path = prefix ++ path)

      override def toString(): String = s"(Source unavailable at ${path.mkString(".")}: ${message})"
    }

    /**
     * The description at `path` asks for something that cannot be loaded;
     * `message` says what. `Config.Fail#parse` returns this error.
     */
    final case class Unsupported(path: Chunk[String] = Chunk.empty, message: String) extends Error {

      /** Returns this error with `prefix` added in front of `path`. */
      def prefixed(prefix: Chunk[String]): Unsupported = copy(path = prefix ++ path)

      override def toString(): String = s"(Unsupported operation at ${path.mkString(".")}: ${message})"
    }

    /**
     * A fold over a `Config.Error`, with a context value of type `Context`
     * passed to each method. `Error#foldContext` calls a leaf method for each
     * leaf error and combines the results with `andCase` and `orCase` from the
     * leaves up.
     */
    trait Folder[-Context, Z] {

      /**
       * Combines the results for the left and right sides of an `And` error.
       */
      def andCase(context: Context, left: Z, right: Z): Z

      /**
       * Returns the result for an `InvalidData` error with `path` and
       * `message`.
       */
      def invalidDataCase(context: Context, path: Chunk[String], message: String): Z

      /**
       * Returns the result for a `MissingData` error with `path` and `message`.
       */
      def missingDataCase(context: Context, path: Chunk[String], message: String): Z

      /** Combines the results for the left and right sides of an `Or` error. */
      def orCase(context: Context, left: Z, right: Z): Z

      /**
       * Returns the result for a `SourceUnavailable` error with `path`,
       * `message` and `cause`.
       */
      def sourceUnavailableCase(context: Context, path: Chunk[String], message: String, cause: Cause[Throwable]): Z

      /**
       * Returns the result for an `Unsupported` error with `path` and
       * `message`.
       */
      def unsupportedCase(context: Context, path: Chunk[String], message: String): Z
    }

    /** The folders that the methods of `Config.Error` use. */
    object Folder {

      /**
       * Returns `true` when each leaf error is a `MissingData`;
       * `Error#isMissingDataOnly` uses it.
       */
      case object IsMissingDataOnly extends Folder[Any, Boolean] {

        /** Returns `true` when both sides are `true`. */
        def andCase(context: Any, left: Boolean, right: Boolean): Boolean = left && right

        /** Returns `false`. */
        def invalidDataCase(context: Any, path: Chunk[String], message: String): Boolean = false

        /** Returns `true`. */
        def missingDataCase(context: Any, path: Chunk[String], message: String): Boolean = true

        /** Returns `true` when both sides are `true`. */
        def orCase(context: Any, left: Boolean, right: Boolean): Boolean = left && right

        /** Returns `false`. */
        def sourceUnavailableCase(
          context: Any,
          path: Chunk[String],
          message: String,
          cause: Cause[Throwable]
        ): Boolean = false

        /** Returns `false`. */
        def unsupportedCase(context: Any, path: Chunk[String], message: String): Boolean = false
      }
    }
  }

  /**
   * A config that reads a `BigDecimal` with `BigDecimal(text)`, such as `1.5`,
   * `-0.25` or `1e3`.
   */
  def bigDecimal: Config[BigDecimal] = Decimal

  /**
   * A config that reads a `BigDecimal` from the property `name`; the same as
   * `bigDecimal.nested(name)`.
   */
  def bigDecimal(name: String): Config[BigDecimal] = bigDecimal.nested(name)

  /**
   * A config that reads a `BigInt` from decimal digits with an optional sign,
   * such as `42` or `-42`.
   */
  def bigInt: Config[BigInt] = Integer

  /**
   * A config that reads a `BigInt` from the property `name`; the same as
   * `bigInt.nested(name)`.
   */
  def bigInt(name: String): Config[BigInt] = bigInt.nested(name)

  /**
   * A config that reads a `Boolean`. Ignoring case, `true`, `yes`, `on` and `1`
   * give `true`, and `false`, `no`, `off` and `0` give `false`.
   */
  def boolean: Config[Boolean] = Bool

  /**
   * A config that reads a `Boolean` from the property `name`; the same as
   * `boolean.nested(name)`.
   */
  def boolean(name: String): Config[Boolean] = boolean.nested(name)

  /**
   * A config that reads a sequence of values, each described by `config`, as a
   * `Chunk`. With `ConfigProvider.fromMap`, the sequence comes from one value
   * split at the sequence delimiter (`8080, 8081`) or from indexed keys
   * (`ports[0]`, `ports[1]`), and the value `<nil>` gives an empty sequence.
   *
   * {{{
   * val provider: ConfigProvider =
   *   ConfigProvider.fromMap(Map("ports" -> "8080, 8081", "hosts[0]" -> "edge-1", "hosts[1]" -> "edge-2", "tags" -> "<nil>"))
   *
   * val lists: IO[Config.Error, (Chunk[Int], Chunk[String], Chunk[String])] =
   *   provider.load(Config.chunkOf("ports", Config.int) ++ Config.chunkOf("hosts", Config.string) ++ Config.chunkOf("tags", Config.string))
   * // succeeds with (Chunk(8080,8081),Chunk(edge-1,edge-2),Chunk())
   * }}}
   */
  def chunkOf[A](config: Config[A]): Config[Chunk[A]] = Sequence(config)

  /** Like `chunkOf(config)`, for the property `name`. */
  def chunkOf[A](name: String, config: Config[A]): Config[Chunk[A]] = chunkOf(config).nested(name)

  /**
   * A config that evaluates `config` each time a provider loads it. A
   * description can use it to refer to itself, such as a tree whose branches
   * have the same structure as the whole.
   */
  def defer[A](config: => Config[A]): Config[A] =
    Lazy(() => config)

  /**
   * A config that reads a `Double` from decimal text such as `0.75` or `1e3`.
   */
  def double: Config[Double] = bigDecimal.map(_.toDouble)

  /**
   * A config that reads a `Double` from the property `name`; the same as
   * `double.nested(name)`.
   */
  def double(name: String): Config[Double] = double.nested(name)

  /**
   * A config that reads a `Duration` from ISO-8601 text such as `PT1H`, or from
   * Scala duration text such as `5 seconds` or `500ms`.
   */
  def duration: Config[zio.Duration] = Duration

  /**
   * A config that reads a `Duration` from the property `name`; the same as
   * `duration.nested(name)`.
   */
  def duration(name: String): Config[zio.Duration] = duration.nested(name)

  /**
   * A config that fails to load with a `MissingData` error whose message is
   * `error`, at the path where it sits. Because the error is missing data,
   * `withDefault` and `optional` recover from it.
   */
  def fail(error: => String): Config[Nothing] = defer(Fail(error))

  /**
   * A config that reads a `Float` from decimal text such as `0.75` or `1e3`.
   */
  def float: Config[Float] = bigDecimal.map(_.toFloat)

  /**
   * A config that reads a `Float` from the property `name`; the same as
   * `float.nested(name)`.
   */
  def float(name: String): Config[Float] = float.nested(name)

  /**
   * A config that reads an `Int` from decimal digits with an optional sign,
   * such as `8080` or `-1`.
   */
  def int: Config[Int] = bigInt.map(_.toInt)

  /**
   * A config that reads an `Int` from the property `name`; the same as
   * `int.nested(name)`.
   */
  def int(name: String): Config[Int] = int.nested(name)

  /** Like `chunkOf(config)`, as a `List`. */
  def listOf[A](config: Config[A]): Config[List[A]] = chunkOf(config).map(_.toList)

  /** Like `chunkOf(config)`, as a `List`, for the property `name`. */
  def listOf[A](name: String, config: Config[A]): Config[List[A]] = listOf(config).nested(name)

  /**
   * A config that reads a `java.time.LocalDate` from ISO-8601 text such as
   * `2024-03-15`.
   */
  def localDate: Config[java.time.LocalDate] = LocalDate

  /**
   * A config that reads a `java.time.LocalDate` from the property `name`; the
   * same as `localDate.nested(name)`.
   */
  def localDate(name: String): Config[java.time.LocalDate] = localDate.nested(name)

  /**
   * A config that reads a `java.time.LocalDateTime` from ISO-8601 text such as
   * `2024-03-15T10:15:30`.
   */
  def localDateTime: Config[java.time.LocalDateTime] = LocalDateTime

  /**
   * A config that reads a `java.time.LocalDateTime` from the property `name`;
   * the same as `localDateTime.nested(name)`.
   */
  def localDateTime(name: String): Config[java.time.LocalDateTime] = localDateTime.nested(name)

  /**
   * A config that reads a `java.time.LocalTime` from ISO-8601 text such as
   * `10:15` or `10:15:30`.
   */
  def localTime: Config[java.time.LocalTime] = LocalTime

  /**
   * A config that reads a `java.time.LocalTime` from the property `name`; the
   * same as `localTime.nested(name)`.
   */
  def localTime(name: String): Config[java.time.LocalTime] = localTime.nested(name)

  /**
   * A config that reads a `LogLevel` from its label, ignoring case: `ALL`,
   * `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL` or `OFF`. Other text,
   * such as `Warning`, fails with an `InvalidData` error.
   */
  def logLevel: Config[LogLevel] = Config.string.mapOrFail { value =>
    val label = value.toUpperCase
    LogLevel.levels.find(_.label == label) match {
      case Some(v) => Right(v)
      case None    => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a log level, but found ${value}"))
    }
  }

  /**
   * A config that reads a `LogLevel` from the property `name`; the same as
   * `logLevel.nested(name)`.
   */
  def logLevel(name: String): Config[LogLevel] = logLevel.nested(name)

  /**
   * A config that reads a `Long` from decimal digits with an optional sign,
   * such as `30000` or `-1`.
   */
  def long: Config[Long] = bigInt.map(_.toLong)

  /**
   * A config that reads a `Long` from the property `name`; the same as
   * `long.nested(name)`.
   */
  def long(name: String): Config[Long] = long.nested(name)

  /**
   * Like `chunkOf(config)`, as a `NonEmptyChunk`. An empty sequence, such as
   * the value `<nil>`, fails with an `InvalidData` error.
   */
  def nonEmptyChunkOf[A](config: Config[A]): Config[NonEmptyChunk[A]] =
    chunkOf(config).mapOrFail(
      NonEmptyChunk
        .fromChunk(_)
        .toRight(Config.Error.InvalidData(message = "Expected a NonEmptyChunk, but found Chunk.Empty"))
    )

  /** Like `nonEmptyChunkOf(config)`, for the property `name`. */
  def nonEmptyChunkOf[A](name: String, config: Config[A]): Config[NonEmptyChunk[A]] =
    nonEmptyChunkOf(config).nested(name)

  /**
   * A config that reads a `java.time.OffsetDateTime` from ISO-8601 text with an
   * offset, such as `2024-03-15T10:15:30+01:00` or `2024-03-15T10:15:30Z`.
   */
  def offsetDateTime: Config[java.time.OffsetDateTime] = OffsetDateTime

  /**
   * A config that reads a `java.time.OffsetDateTime` from the property `name`;
   * the same as `offsetDateTime.nested(name)`.
   */
  def offsetDateTime(name: String): Config[java.time.OffsetDateTime] = offsetDateTime.nested(name)

  /**
   * A config that reads the text unchanged as a `Config.Secret`, whose
   * `toString` does not show it.
   *
   * As an element of a sequence, such as in `Config.listOf("tokens",
   * Config.secret)`, the text is split at the sequence delimiter and each part
   * is trimmed.
   */
  def secret: Config[Secret] = SecretType

  /**
   * A config that reads a `Config.Secret` from the property `name`; the same as
   * `secret.nested(name)`.
   */
  def secret(name: String): Config[Secret] = secret.nested(name)

  /**
   * Like `chunkOf(config)`, as a `Set`, which keeps one copy of equal values.
   */
  def setOf[A](config: Config[A]): Config[Set[A]] = chunkOf(config).map(_.toSet)

  /** Like `chunkOf(config)`, as a `Set`, for the property `name`. */
  def setOf[A](name: String, config: Config[A]): Config[Set[A]] = setOf(config).nested(name)

  /**
   * A config that reads the text unchanged, including empty text and
   * surrounding spaces.
   *
   * As an element of a sequence, such as in `Config.listOf("hosts",
   * Config.string)`, the text is split at the sequence delimiter and each part
   * is trimmed.
   */
  def string: Config[String] = Text

  /**
   * A config that reads a `String` from the property `name`; the same as
   * `string.nested(name)`.
   */
  def string(name: String): Config[String] = string.nested(name)

  /**
   * A config that produces `value` without reading any data. `value` is
   * evaluated each time a provider loads the config.
   */
  def succeed[A](value: => A): Config[A] = defer(Constant(value))

  /**
   * A config that reads a map from the name of each property under the current
   * path to its value, read with `value`.
   *
   * {{{
   * val provider: ConfigProvider = ConfigProvider.fromMap(Map("quotas.alice" -> "10", "quotas.bob" -> "25"))
   *
   * val quotas: IO[Config.Error, Map[String, Int]] = provider.load(Config.table("quotas", Config.int))
   * // succeeds with Map(alice -> 10, bob -> 25)
   * }}}
   */
  def table[V](value: Config[V]): Config[Map[String, V]] = Table(value)

  /** Like `table(value)`, for the property `name`. */
  def table[V](name: String, value: Config[V]): Config[Map[String, V]] = table(value).nested(name)

  /**
   * A config that reads a `java.net.URI` with `java.net.URI.create`. Text that
   * it rejects fails with an `InvalidData` error.
   */
  def uri: Config[java.net.URI] = string.mapAttempt(java.net.URI.create(_))

  /**
   * A config that reads a `java.net.URI` from the property `name`; the same as
   * `uri.nested(name)`.
   */
  def uri(name: String): Config[java.net.URI] = uri.nested(name)

  /** Like `chunkOf(config)`, as a `Vector`. */
  def vectorOf[A](config: Config[A]): Config[Vector[A]] = chunkOf(config).map(_.toVector)

  /** Like `chunkOf(config)`, as a `Vector`, for the property `name`. */
  def vectorOf[A](name: String, config: Config[A]): Config[Vector[A]] = vectorOf(config).nested(name)

  /**
   * The result of `orElseIf`: it holds a config and a condition on its errors,
   * and `apply` takes the config to fall back to.
   */
  final class OrElse[+A](self: Config[A], condition: Error => Boolean) {

    /**
     * Returns a config that reads the first config and, when it fails with an
     * error that satisfies the condition, reads `that`.
     */
    def apply[A1 >: A](that: Config[A1]): Config[A1] =
      FallbackWith(self, that, condition)
  }
}
