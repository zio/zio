/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

import scala.compiletime.{constValueTuple, erasedValue, summonInline}
import scala.deriving.Mirror

private[zio] transparent trait ConfigCompanionVersionSpecific {

  /**
   * Derives a [[Config]] for any product type (case class or case object) whose
   * field types all have a [[Config]] instance in implicit scope.
   *
   * This allows the Scala 3 `derives` keyword to be used:
   *
   * {{{
   * case class AppConfig(host: String, port: Int) derives Config
   *
   * // is equivalent to:
   * object AppConfig {
   *   given Config[AppConfig] =
   *     Config.string("host").zip(Config.int("port")).map(AppConfig.apply)
   * }
   * }}}
   *
   * All field types must have a [[Config]] instance in implicit scope. The
   * field names in the configuration source are derived from the case-class
   * field names.
   */
  inline def derived[A](using m: Mirror.ProductOf[A]): Config[A] = {
    val labels: List[String]     = constValueTuple[m.MirroredElemLabels].toList.asInstanceOf[List[String]]
    val configs: List[Config[?]] = summonConfigList[m.MirroredElemTypes]

    // Build a Config[List[Any]] by folding right over all fields.
    // Each field is nested under its name, then prepended to the accumulator.
    val combined: Config[List[Any]] =
      labels
        .zip(configs)
        .foldRight[Config[List[Any]]](Config.succeed(List.empty[Any])) { case ((name, cfg), tail) =>
          cfg.nested(name).asInstanceOf[Config[Any]].zipWith(tail)(_ :: _)
        }

    // Map the flat List[Any] to the case-class by supplying it as a Product.
    combined.map { values =>
      m.fromProduct(new Product {
        private val elems: Array[Any]    = values.toArray
        def productArity: Int            = elems.length
        def productElement(i: Int): Any  = elems(i)
        def canEqual(that: Any): Boolean = false
      })
    }
  }

  /**
   * Recursively summons [[Config]] instances for each element of a [[Tuple]]
   * type.
   */
  private inline def summonConfigList[T <: Tuple]: List[Config[?]] =
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonInline[Config[t]] :: summonConfigList[ts]
    }

  /**
   * Given instances for primitive types so that `derives Config` works without
   * any extra boilerplate. Each instance delegates to the existing named
   * constructor on the companion and is then `nested` by [[derived]] under the
   * field name.
   *
   * Note: these reference `Config.string`, `Config.int`, etc. (fully-qualified)
   * because this trait is compiled before the companion object's methods are in
   * scope — bare `string` / `int` would be unresolved inside the trait body.
   */
  given Config[String]                               = Config.string
  given Config[Int]                                  = Config.int
  given Config[Long]                                 = Config.long
  given Config[Double]                               = Config.double
  given Config[Float]                                = Config.float
  given Config[Boolean]                              = Config.boolean
  given Config[BigInt]                               = Config.bigInt
  given Config[BigDecimal]                           = Config.bigDecimal
  given Config[zio.Duration]                         = Config.duration
  given Config[java.time.LocalDate]                  = Config.localDate
  given Config[java.time.LocalDateTime]              = Config.localDateTime
  given Config[java.time.LocalTime]                  = Config.localTime
  given Config[java.time.OffsetDateTime]             = Config.offsetDateTime
  given Config[java.net.URI]                         = Config.uri
  given [A](using cfg: Config[A]): Config[Option[A]] = cfg.optional
}
