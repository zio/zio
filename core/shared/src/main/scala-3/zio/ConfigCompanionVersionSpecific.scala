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

import scala.compiletime._
import scala.deriving._

/**
 * Version-specific additions to the [[Config]] companion object for Scala 3.
 *
 * Provides the `derived` method that enables the `derives` keyword for
 * automatic [[Config]] derivation from case classes and sealed traits.
 */
private[zio] transparent trait ConfigCompanionVersionSpecific {

  /**
   * Derives a [[Config]] instance for a product type (case class) or sum type
   * (sealed trait / enum) using Scala 3's `Mirror` mechanism.
   *
   * This enables the `derives` keyword syntax:
   *
   * {{{
   * case class MyConfig(host: String, port: Int) derives Config
   *
   * // Equivalent to manually writing:
   * given Config[MyConfig] =
   *   (Config.string("host") zip Config.int("port")).map(MyConfig.apply)
   * }}}
   *
   * For sum types, each subtype is tried in order using [[Config.orElse]]:
   *
   * {{{
   * enum DbConfig derives Config:
   *   case Postgres(url: String, port: Int)
   *   case InMemory(name: String)
   * }}}
   *
   * Each field of a product type must have a [[Config]] instance available in
   * the implicit scope. Instances are provided for all primitive types
   * supported by [[Config]] directly in the [[Config]] companion object.
   */
  inline def derived[A](using m: Mirror.Of[A]): Config[A] =
    inline m match {
      case p: Mirror.ProductOf[A] => deriveProduct(p)
      case s: Mirror.SumOf[A]     => deriveSum(s)
    }

  private inline def deriveProduct[A](m: Mirror.ProductOf[A]): Config[A] = {
    val labels  = constValueTuple[m.MirroredElemLabels].toList.asInstanceOf[List[String]]
    val configs = summonConfigList[m.MirroredElemTypes]
    buildProductConfig(labels, configs, m)
  }

  private def buildProductConfig[A](
    labels: List[String],
    configs: List[Config[?]],
    m: Mirror.ProductOf[A]
  ): Config[A] = {
    val nestedConfigs: List[Config[Any]] =
      (labels zip configs).map { case (label, cfg) => cfg.nested(label).asInstanceOf[Config[Any]] }

    nestedConfigs match {
      case Nil =>
        // Zero-field case class (singleton-like)
        Config.succeed(m.fromProduct(EmptyTuple))

      case head :: tail =>
        // Fold all configs into Config[List[Any]], then map to A
        val listConfig: Config[List[Any]] =
          tail.foldLeft(head.map(v => List(v))) { (acc, next) =>
            acc.zipWith(next) { (list, v) => list :+ v }
          }
        listConfig.map { values =>
          val tuple = listToTuple(values)
          m.fromProduct(tuple)
        }
    }
  }

  private inline def deriveSum[A](m: Mirror.SumOf[A]): Config[A] = {
    val subtypeConfigs = summonSubtypeConfigs[A, m.MirroredElemTypes]
    subtypeConfigs.reduce(_ orElse _)
  }

  private inline def summonConfigList[T <: Tuple]: List[Config[?]] =
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (h *: t)   => summonInline[Config[h]] :: summonConfigList[t]
    }

  private inline def summonSubtypeConfigs[A, T <: Tuple]: List[Config[A]] =
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (h *: t) =>
        summonInline[Config[h]].asInstanceOf[Config[A]] :: summonSubtypeConfigs[A, t]
    }

  private def listToTuple(list: List[Any]): Product = list match {
    case Nil            => EmptyTuple
    case a :: Nil       => Tuple1(a)
    case a :: b :: Nil  => (a, b)
    case elems          => tupleFromSeq(elems)
  }

  private def tupleFromSeq(elems: Seq[Any]): Product =
    elems.size match {
      case 3  => (elems(0), elems(1), elems(2))
      case 4  => (elems(0), elems(1), elems(2), elems(3))
      case 5  => (elems(0), elems(1), elems(2), elems(3), elems(4))
      case 6  => (elems(0), elems(1), elems(2), elems(3), elems(4), elems(5))
      case 7  => (elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6))
      case 8  => (elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6), elems(7))
      case 9  => (elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6), elems(7), elems(8))
      case 10 => (elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6), elems(7), elems(8), elems(9))
      case 11 =>
        (elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6), elems(7), elems(8), elems(9), elems(10))
      case 12 =>
        (
          elems(0), elems(1), elems(2), elems(3), elems(4), elems(5),
          elems(6), elems(7), elems(8), elems(9), elems(10), elems(11)
        )
      case 13 =>
        (
          elems(0), elems(1), elems(2), elems(3), elems(4), elems(5),
          elems(6), elems(7), elems(8), elems(9), elems(10), elems(11), elems(12)
        )
      case 14 =>
        (
          elems(0), elems(1), elems(2), elems(3), elems(4), elems(5),
          elems(6), elems(7), elems(8), elems(9), elems(10), elems(11), elems(12), elems(13)
        )
      case 15 =>
        (
          elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6),
          elems(7), elems(8), elems(9), elems(10), elems(11), elems(12), elems(13), elems(14)
        )
      case 16 =>
        (
          elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6), elems(7),
          elems(8), elems(9), elems(10), elems(11), elems(12), elems(13), elems(14), elems(15)
        )
      case 17 =>
        (
          elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6), elems(7), elems(8),
          elems(9), elems(10), elems(11), elems(12), elems(13), elems(14), elems(15), elems(16)
        )
      case 18 =>
        (
          elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6), elems(7), elems(8), elems(9),
          elems(10), elems(11), elems(12), elems(13), elems(14), elems(15), elems(16), elems(17)
        )
      case 19 =>
        (
          elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6), elems(7), elems(8), elems(9),
          elems(10), elems(11), elems(12), elems(13), elems(14), elems(15), elems(16), elems(17), elems(18)
        )
      case 20 =>
        (
          elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6), elems(7), elems(8), elems(9),
          elems(10), elems(11), elems(12), elems(13), elems(14), elems(15), elems(16), elems(17), elems(18), elems(19)
        )
      case 21 =>
        (
          elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6), elems(7), elems(8), elems(9), elems(10),
          elems(11), elems(12), elems(13), elems(14), elems(15), elems(16), elems(17), elems(18), elems(19), elems(20)
        )
      case 22 =>
        (
          elems(0), elems(1), elems(2), elems(3), elems(4), elems(5), elems(6), elems(7), elems(8), elems(9), elems(10),
          elems(11), elems(12), elems(13), elems(14), elems(15), elems(16), elems(17), elems(18), elems(19), elems(20),
          elems(21)
        )
      case n =>
        throw new UnsupportedOperationException(
          s"Config derivation supports case classes with up to 22 fields, but got $n fields. " +
            "Consider splitting your configuration into smaller structures."
        )
    }
}
