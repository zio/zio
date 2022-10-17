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

import scala.util.control.NoStackTrace

/**
 * A [[zio.Config]] describes the structure of some configuration data.
 */
sealed trait Config[+A] { self =>

  /**
   * Returns a new config that is the composition of this config and the
   * specified config.
   */
  def ++[B](that: Config[B])(implicit zippable: Zippable[A, B]): Config[zippable.Out] =
    Config.Zipped[A, B, zippable.Out](self, that, zippable)

  /**
   * Returns a config whose structure is preferentially described by this
   * config, but which falls back to the specified config if there is an issue
   * reading from this config.
   */
  def ||[A1 >: A](that: Config[A1]): Config[A1] = Config.Fallback(self, that)

  /**
   * Adds a description to this configuration.
   */
  def ??(label: String): Config[A] = Config.Labelled(self, label)

  /**
   * Returns a new config whose structure is the same as this one, but which
   * produces a different Scala value, constructed using the specified function.
   */
  def map[B](f: A => B): Config[B] = self.mapOrFail(a => Right(f(a)))

  def mapOrFail[B](f: A => Either[Config.Error, B]): Config[B] = Config.MapOrFail(self, f)

  def optional: Config[Option[A]] = self.map(Some(_)) || Config.succeed(None)

  def orElse[A1 >: A](that: Config[A1]): Config[A1] = self || that

  def sequence: Config[Chunk[A]] = Config.Sequence(self)

  def validate[A1 >: A](f: A1 => Boolean): Config[A1] =
    self.mapOrFail(a =>
      if (!f(a)) Left(Config.Error.Generic(Chunk.empty, "Validation of configuration data failed")) else Right(a)
    )

  def zip[B](that: Config[B])(implicit z: Zippable[A, B]): Config[z.Out] = self ++ that
}
object Config {
  final case class Bool(name: String)                                                                  extends Config[Boolean]
  final case class Constant[A](value: A)                                                               extends Config[A]
  final case class Decimal(name: String)                                                               extends Config[BigDecimal]
  final case class Duration(name: String)                                                              extends Config[java.time.Duration]
  final case class Fail(message: String)                                                               extends Config[Nothing]
  final case class Fallback[A](first: Config[A], second: Config[A])                                    extends Config[A]
  final case class Integer(name: String)                                                               extends Config[BigInt]
  final case class Labelled[A](config: Config[A], label: String)                                       extends Config[A]
  final case class Lazy[A](thunk: () => Config[A])                                                     extends Config[A]
  final case class LocalDateTime(name: String)                                                         extends Config[java.time.LocalDateTime]
  final case class LocalDate(name: String)                                                             extends Config[java.time.LocalDate]
  final case class LocalTime(name: String)                                                             extends Config[java.time.LocalTime]
  final case class MapOrFail[A, B](original: Config[A], mapOrFail: A => Either[Config.Error, B])       extends Config[B]
  final case class OffsetDateTime(name: String)                                                        extends Config[java.time.OffsetDateTime]
  final case class Secret(name: String)                                                                extends Config[Chunk[Byte]]
  final case class Sequence[A](config: Config[A])                                                      extends Config[Chunk[A]]
  final case class Table[K, V](keyConfig: Config[K], valueConfig: Config[V])                           extends Config[Map[K, V]]
  final case class Text(name: String)                                                                  extends Config[String]
  final case class URL(name: String)                                                                   extends Config[java.net.URL]
  final case class Zipped[A, B, C](left: Config[A], right: Config[B], zippable: Zippable.Out[A, B, C]) extends Config[C]

  sealed trait Error extends Exception with NoStackTrace {
    def prefixed(prefix: Chunk[String]): Error
  }
  object Error {
    final case class And(left: Error, right: Error) extends Error {
      def prefixed(prefix: Chunk[String]): And =
        copy(left = left.prefixed(prefix), right = right.prefixed(prefix))
    }
    final case class Generic(path: Chunk[String], message: String) extends Error {
      def prefixed(prefix: Chunk[String]): Generic = copy(path = prefix ++ path)
    }
    final case class InvalidData(path: Chunk[String], message: String) extends Error {
      def prefixed(prefix: Chunk[String]): InvalidData = copy(path = prefix ++ path)
    }
    final case class MissingData(path: Chunk[String], message: String) extends Error {
      def prefixed(prefix: Chunk[String]): MissingData = copy(path = prefix ++ path)
    }
    final case class Or(left: Error, right: Error) extends Error {
      def prefixed(prefix: Chunk[String]): Or =
        copy(left = left.prefixed(prefix), right = right.prefixed(prefix))
    }
    final case class UnreachableSource(path: Chunk[String], message: String) extends Error {
      def prefixed(prefix: Chunk[String]): UnreachableSource = copy(path = prefix ++ path)
    }
  }

  def bigDecimal(name: String): Config[BigDecimal] = Decimal(name)

  def bigInteger(name: String): Config[BigInt] = Integer(name)

  def boolean(name: String): Config[Boolean] = Bool(name)

  def chunkOf[A](config: Config[A]): Config[Chunk[A]] = Sequence(config)

  def defer[A](config: => Config[A]): Config[A] =
    Lazy(() => config)

  def double(name: String): Config[Double] = bigDecimal(name).map(_.toDouble)

  def duration(name: String): Config[java.time.Duration] = Duration(name)

  def fail(error: => String): Config[Nothing] = defer(Fail(error))

  def float(name: String): Config[Float] = bigDecimal(name).map(_.toFloat)

  def int(name: String): Config[Int] = bigInteger(name).map(_.toInt)

  def listOf[A](config: Config[A]): Config[List[A]] = chunkOf(config).map(_.toList)

  def localDate(name: String): Config[java.time.LocalDate] = LocalDate(name)

  def localDateTime(name: String): Config[java.time.LocalDateTime] = LocalDateTime(name)

  def localTime(name: String): Config[java.time.LocalTime] = LocalTime(name)

  def offsetDateTime(name: String): Config[java.time.OffsetDateTime] = OffsetDateTime(name)

  def secret(name: String): Config[Chunk[Byte]] = Secret(name)

  def setOf[A](config: Config[A]): Config[Set[A]] = chunkOf(config).map(_.toSet)

  def simpleTable[V](value: Config[V]): Config[Map[String, V]] = table(string("key"), value)

  def string(name: String): Config[String] = Text(name)

  def succeed[A](value: => A): Config[A] = defer(Constant(value))

  def table[K, V](key: Config[K], value: Config[V]): Config[Map[K, V]] = Table(key, value)

  def url(name: String): Config[java.net.URL] = URL(name)

  def vectorOf[A](config: Config[A]): Config[Vector[A]] = chunkOf(config).map(_.toVector)
}
