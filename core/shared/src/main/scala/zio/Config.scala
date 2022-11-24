/*
 * Copyright 2022 John A. De Goes and the ZIO Contributors
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

import scala.util.control.{NonFatal, NoStackTrace}

/**
 * A [[zio.Config]] describes the structure of some configuration data.
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
   * Returns a new config whose structure is the samea as this one, but which
   * may produce a different Scala value, constructed using the specified
   * fallible function.
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
        case NonFatal(e) => Left(Config.Error.InvalidData(Chunk.empty, e.getMessage))
      }
    }

  /**
   * Returns a new config that has this configuration nested as a property of
   * the specified name.
   */
  def nested(name: => String): Config[A] = Config.defer(Config.Nested(name, self))

  /**
   * Returns an optional version of this config, which will be `None` if the
   * data is missing from configuration, and `Some` otherwise.
   */
  def optional: Config[Option[A]] = self.map(Some(_)) || Config.succeed(None)

  /**
   * A named version of `||`.
   */
  def orElse[A1 >: A](that: => Config[A1]): Config[A1] = self || that

  /**
   * Returns a new config that describes a sequence of values, each of which has
   * the structure of this config.
   */
  def repeat: Config[Chunk[A]] = Config.Sequence(self)

  /**
   * Returns a new config that describes the same structure as this one, but
   * which performs validation during loading.
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
    self || Config.succeed(default)

  /**
   * A named version of `++`.
   */
  def zip[B](that: => Config[B])(implicit z: Zippable[A, B]): Config[z.Out] = self ++ that
}
object Config {
  final class Secret private (private val raw: Array[Char]) { self =>
    override def equals(that: Any): Boolean =
      that match {
        case that: Secret => self.value == that.value
        case _            => false
      }

    override def hashCode(): Int = value.hashCode

    override def toString(): String = "Secret(<redacted>)"

    object unsafe {
      def wipe(implicit unsafe: Unsafe): Unit =
        (0 until raw.length).foreach(i => raw(i) = 0)
    }

    def value: Chunk[Char] = Chunk.fromArray(raw)
  }
  object Secret extends (Chunk[Char] => Secret) {
    def apply(chunk: Chunk[Char]): Secret = new Secret(chunk.toArray)

    def apply(cs: CharSequence): Secret = Secret(cs.toString())

    def apply(s: String): Secret = Secret(Chunk.fromArray(s.toCharArray))

    def unapply(secret: Secret): Some[Chunk[Char]] = Some(secret.value)
  }

  sealed trait Primitive[+A] extends Config[A] { self =>
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

    final def missingError(name: String): Config.Error =
      Config.Error.MissingData(Chunk.empty, s"Expected ${description} with name ${name}")

    def parse(text: String): Either[Config.Error, A]
  }
  sealed trait Composite[+A] extends Config[A]

  case object Bool extends Primitive[Boolean] {
    final def parse(text: String): Either[Config.Error, Boolean] = text.toLowerCase match {
      case "true" | "yes" | "on" | "1"  => Right(true)
      case "false" | "no" | "off" | "0" => Right(false)
      case _                            => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a boolean value, but found ${text}"))
    }
  }
  final case class Constant[A](value: A) extends Primitive[A] {
    final def parse(text: String): Either[Config.Error, A] = Right(value)
  }
  case object Decimal extends Primitive[BigDecimal] {
    final def parse(text: String): Either[Config.Error, BigDecimal] = try Right(BigDecimal(text))
    catch {
      case NonFatal(e) => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a decimal value, but found ${text}"))
    }
  }
  case object Duration extends Primitive[zio.Duration] {
    final def parse(text: String): Either[Config.Error, zio.Duration] = try Right(java.time.Duration.parse(text))
    catch {
      case NonFatal(e) => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a duration value, but found ${text}"))
    }
  }
  final case class Fail(message: String) extends Primitive[Nothing] {
    final def parse(text: String): Either[Config.Error, Nothing] = Left(Config.Error.Unsupported(Chunk.empty, message))
  }
  final case class Fallback[A](first: Config[A], second: Config[A]) extends Composite[A]
  case object Integer extends Primitive[BigInt] {
    final def parse(text: String): Either[Config.Error, BigInt] = try Right(BigInt(text))
    catch {
      case NonFatal(e) => Left(Config.Error.InvalidData(Chunk.empty, s"Expected an integer value, but found ${text}"))
    }
  }
  final case class Described[A](config: Config[A], description: String) extends Composite[A]
  final case class Lazy[A](thunk: () => Config[A])                      extends Composite[A]
  case object LocalDateTime extends Primitive[java.time.LocalDateTime] {
    final def parse(text: String): Either[Config.Error, java.time.LocalDateTime] = try Right(
      java.time.LocalDateTime.parse(text)
    )
    catch {
      case NonFatal(e) =>
        Left(Config.Error.InvalidData(Chunk.empty, s"Expected a local date-time value, but found ${text}"))
    }
  }
  case object LocalDate extends Primitive[java.time.LocalDate] {
    final def parse(text: String): Either[Config.Error, java.time.LocalDate] = try Right(
      java.time.LocalDate.parse(text)
    )
    catch {
      case NonFatal(e) => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a local date value, but found ${text}"))
    }
  }
  case object LocalTime extends Primitive[java.time.LocalTime] {
    final def parse(text: String): Either[Config.Error, java.time.LocalTime] = try Right(
      java.time.LocalTime.parse(text)
    )
    catch {
      case NonFatal(e) => Left(Config.Error.InvalidData(Chunk.empty, s"Expected a local time value, but found ${text}"))
    }
  }
  final case class MapOrFail[A, B](original: Config[A], mapOrFail: A => Either[Config.Error, B]) extends Composite[B]
  final case class Nested[A](name: String, config: Config[A])                                    extends Composite[A]
  case object OffsetDateTime extends Primitive[java.time.OffsetDateTime] {
    final def parse(text: String): Either[Config.Error, java.time.OffsetDateTime] = try Right(
      java.time.OffsetDateTime.parse(text)
    )
    catch {
      case NonFatal(e) =>
        Left(Config.Error.InvalidData(Chunk.empty, s"Expected an offset date-time value, but found ${text}"))
    }
  }
  case object SecretType extends Primitive[Secret] {
    final def parse(text: String): Either[Config.Error, Secret] = Right(
      Secret(text)
    )
  }
  final case class Sequence[A](config: Config[A])   extends Composite[Chunk[A]]
  final case class Table[V](valueConfig: Config[V]) extends Composite[Map[String, V]]
  case object Text extends Primitive[String] {
    final def parse(text: String): Either[Config.Error, String] = Right(text)
  }
  final case class Zipped[A, B, C](left: Config[A], right: Config[B], zippable: Zippable.Out[A, B, C])
      extends Composite[C]

  /**
   * The possible ways that loading configuration data may fail.
   */
  sealed trait Error extends Exception with NoStackTrace { self =>
    def &&(that: Error): Error = Error.And(self, that)
    def ||(that: Error): Error = Error.And(self, that)

    def prefixed(prefix: Chunk[String]): Error

    override def getMessage(): String = toString()
  }
  object Error {
    final case class And(left: Error, right: Error) extends Error {
      def prefixed(prefix: Chunk[String]): And =
        copy(left = left.prefixed(prefix), right = right.prefixed(prefix))

      override def toString(): String = s"(${left.toString()} and ${right.toString()})"
    }
    final case class InvalidData(path: Chunk[String] = Chunk.empty, message: String) extends Error {
      def prefixed(prefix: Chunk[String]): InvalidData = copy(path = prefix ++ path)

      override def toString(): String = s"(Invalid data at ${path.mkString(".")}: ${message})"
    }
    final case class MissingData(path: Chunk[String] = Chunk.empty, message: String) extends Error {
      def prefixed(prefix: Chunk[String]): MissingData = copy(path = prefix ++ path)

      override def toString(): String = s"(Missing data at ${path.mkString(".")}: ${message})"
    }
    final case class Or(left: Error, right: Error) extends Error {
      def prefixed(prefix: Chunk[String]): Or =
        copy(left = left.prefixed(prefix), right = right.prefixed(prefix))

      override def toString(): String = s"(${left.toString()} or ${right.toString()})"
    }
    final case class SourceUnavailable(path: Chunk[String] = Chunk.empty, message: String, cause: Cause[Throwable])
        extends Error {
      def prefixed(prefix: Chunk[String]): SourceUnavailable = copy(path = prefix ++ path)

      override def toString(): String = s"(Source unavailable at ${path.mkString(".")}: ${message})"
    }
    final case class Unsupported(path: Chunk[String] = Chunk.empty, message: String) extends Error {
      def prefixed(prefix: Chunk[String]): Unsupported = copy(path = prefix ++ path)

      override def toString(): String = s"(Unsupported operation at ${path.mkString(".")}: ${message})"
    }
  }

  def bigDecimal: Config[BigDecimal] = Decimal

  def bigDecimal(name: String): Config[BigDecimal] = bigDecimal.nested(name)

  def bigInt: Config[BigInt] = Integer

  def bigInt(name: String): Config[BigInt] = Integer.nested(name)

  def boolean: Config[Boolean] = Bool

  def boolean(name: String): Config[Boolean] = boolean.nested(name)

  def chunkOf[A](config: Config[A]): Config[Chunk[A]] = Sequence(config)

  def chunkOf[A](name: String, config: Config[A]): Config[Chunk[A]] = chunkOf(config).nested(name)

  def defer[A](config: => Config[A]): Config[A] =
    Lazy(() => config)

  def double: Config[Double] = bigDecimal.map(_.toDouble)

  def double(name: String): Config[Double] = double.nested(name)

  def duration: Config[zio.Duration] = Duration

  def duration(name: String): Config[zio.Duration] = duration.nested(name)

  def fail(error: => String): Config[Nothing] = defer(Fail(error))

  def float: Config[Float] = bigDecimal.map(_.toFloat)

  def float(name: String): Config[Float] = float.nested(name)

  def int: Config[Int] = bigInt.map(_.toInt)

  def int(name: String): Config[Int] = int.nested(name)

  def listOf[A](config: Config[A]): Config[List[A]] = chunkOf(config).map(_.toList)

  def listOf[A](name: String, config: Config[A]): Config[List[A]] = listOf(config).nested(name)

  def localDate: Config[java.time.LocalDate] = LocalDate

  def localDate(name: String): Config[java.time.LocalDate] = LocalDate.nested(name)

  def localDateTime: Config[java.time.LocalDateTime] = LocalDateTime

  def localDateTime(name: String): Config[java.time.LocalDateTime] = LocalDateTime.nested(name)

  def localTime: Config[java.time.LocalTime] = LocalTime

  def localTime(name: String): Config[java.time.LocalTime] = localTime.nested(name)

  def offsetDateTime: Config[java.time.OffsetDateTime] = OffsetDateTime

  def offsetDateTime(name: String): Config[java.time.OffsetDateTime] = offsetDateTime.nested(name)

  def secret: Config[Secret] = SecretType

  def secret(name: String): Config[Secret] = secret.nested(name)

  def setOf[A](config: Config[A]): Config[Set[A]] = chunkOf(config).map(_.toSet)

  def setOf[A](name: String, config: Config[A]): Config[Set[A]] = setOf(config).nested(name)

  def string: Config[String] = Text

  def string(name: String): Config[String] = string.nested(name)

  def succeed[A](value: => A): Config[A] = defer(Constant(value))

  def table[V](value: Config[V]): Config[Map[String, V]] = Table(value)

  def table[V](name: String, value: Config[V]): Config[Map[String, V]] = table(value).nested(name)

  def uri: Config[java.net.URI] = string.mapAttempt(java.net.URI.create(_))

  def uri(name: String): Config[java.net.URI] = uri.nested(name)

  def vectorOf[A](config: Config[A]): Config[Vector[A]] = chunkOf(config).map(_.toVector)

  def vectorOf[A](name: String, config: Config[A]): Config[Vector[A]] = vectorOf(config).nested(name)
}
