/*
 * Copyright 2020-2024 John A. De Goes and the ZIO Contributors
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

package zio.test.magnolia

import zio.Chunk

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID
import scala.compiletime.{erasedValue, summonInline}
import scala.deriving._
import zio._
import zio.test.Gen

/**
 * A `DeriveGen[A]` derives a generator of `A` values. `DeriveGen` has instances
 * for `Boolean`, `Byte`, `Char`, `Short`, `Int`, `Long`, `Float`, `Double`,
 * `Unit`, `String`, `UUID`, `Instant`, `LocalDate`, `LocalDateTime`,
 * `LocalTime`, `BigDecimal`, `Option`, `Either`, functions, partial functions,
 * tuples, and the `List`, `Vector`, `Seq`, `Set`, `Iterable`, `Chunk` and `Map`
 * collections. It derives instances for case classes and sealed traits whose
 * fields and subtypes have instances. For example, here is how you can derive
 * generators for a case class and a sealed trait:
 *
 * {{{
 * import zio.test.Gen
 *
 * final case class Point(x: Double, y: Double)
 *
 * val genPoint: Gen[Any, Point] = DeriveGen[Point]
 *
 * sealed trait Color
 * case object Red   extends Color
 * case object Green extends Color
 * case object Blue  extends Color
 *
 * val genColor: Gen[Any, Color] = DeriveGen[Color]
 * }}}
 *
 * A derived generator for a case class generates each field with that field's
 * instance, and shrinks the fields with their own generators. A derived
 * generator for a sealed trait picks one of its subtypes with equal
 * probability: on Scala 2 among the case classes and objects at the leaves of
 * the hierarchy, and on Scala 3 among the direct subtypes, so a nested sealed
 * trait counts as one subtype. To use a type that is neither a case class nor a
 * sealed trait, give it an implicit `DeriveGen` with `DeriveGen.instance`.
 *
 * On Scala 2, derivation uses Magnolia; on Scala 3, it uses the type's
 * `scala.deriving.Mirror`. Scala 3 has no instances for `BigInt`,
 * `java.math.BigInteger` and `java.math.BigDecimal`, derives tuples through its
 * `EmptyTuple` and `*:` instances instead of one instance per tuple size, and
 * derives union types with `DeriveGen.unionType`.
 *
 * @see
 *   [[https://zio.dev/reference/test/property-testing/ Property Testing]]
 */
trait DeriveGen[A] {

  /**
   * Returns the generator of `A` values.
   */
  def derive: Gen[Any, A]
}

/**
 * Creates and derives `DeriveGen` instances. `DeriveGen[A]` returns the
 * generator of the `DeriveGen[A]` instance in scope, and `DeriveGen.instance`
 * creates an instance from a generator. The companion holds the instances for
 * standard types and the derivation for case classes and sealed traits, so the
 * compiler finds them without an import.
 */
object DeriveGen {

  /**
   * Returns the generator of the `DeriveGen[A]` instance in scope. For a case
   * class or sealed trait that has no instance in scope, the compiler derives
   * one.
   */
  def apply[A](using DeriveGen[A]): Gen[Any, A] =
    summon[DeriveGen[A]].derive

  /**
   * Creates a `DeriveGen` from a generator of `A` values. Use it to give an
   * implicit `DeriveGen` to a type that cannot be derived, so that derived
   * generators of case classes and sealed traits that contain the type can use
   * it. On Scala 3, `gen` is a by-name parameter that is evaluated once, when
   * the instance is created.
   *
   * {{{
   * import zio.test.Gen
   *
   * final class Email(val address: String)
   *
   * implicit val deriveEmail: DeriveGen[Email] =
   *   DeriveGen.instance(Gen.alphaNumericStringBounded(1, 8).map(name => new Email(name + "@example.com")))
   *
   * final case class Account(id: Long, email: Email)
   *
   * val genAccount: Gen[Any, Account] = DeriveGen[Account]
   * }}}
   */
  inline def instance[A](gen: => Gen[Any, A]): DeriveGen[A] =
    new DeriveGen[A] {
      val derive: Gen[Any, A] = gen
    }

  /**
   * Derives a `DeriveGen` for a union type. Pass the union as a type alias: an
   * inline union such as `unionType[String | Int]` does not compile. Each value
   * comes from one member type of the union, picked with equal probability.
   * Nested unions are flattened, including unions behind other type aliases, so
   * a union of `String` and an alias for `Color | Boolean` picks among
   * `String`, `Color` and `Boolean`. Each member type needs a `DeriveGen`
   * instance, or compilation fails.
   *
   * {{{
   * // scala-3-only
   * import zio.test.Gen
   *
   * type StringOrInt = String | Int
   *
   * given DeriveGen[StringOrInt] = DeriveGen.unionType[StringOrInt]
   *
   * val genStringOrInt: Gen[Any, StringOrInt] = DeriveGen[StringOrInt]
   * }}}
   */
  inline def unionType[T]: DeriveGen[T] = ${ TypeUnionDerivation.typeUnionDeriveGen[T] }

  /**
   * A `DeriveGen` for `Boolean` values, using `Gen.boolean`.
   */
  given DeriveGen[Boolean] = instance(Gen.boolean)

  /**
   * A `DeriveGen` for `Byte` values, using `Gen.byte`.
   */
  given DeriveGen[Byte] = instance(Gen.byte)

  /**
   * A `DeriveGen` for `Char` values, using `Gen.char`.
   */
  given DeriveGen[Char] = instance(Gen.char)

  /**
   * A `DeriveGen` for `Double` values, using `Gen.double`.
   */
  given DeriveGen[Double] = instance(Gen.double)

  /**
   * A `DeriveGen` for `Float` values, using `Gen.float`.
   */
  given DeriveGen[Float] = instance(Gen.float)

  /**
   * A `DeriveGen` for `Int` values, using `Gen.int`.
   */
  given DeriveGen[Int] = instance(Gen.int)

  /**
   * A `DeriveGen` for `Long` values, using `Gen.long`.
   */
  given DeriveGen[Long] = instance(Gen.long)

  /**
   * A `DeriveGen` for `Short` values, using `Gen.short`.
   */
  given DeriveGen[Short] = instance(Gen.short)

  /**
   * A `DeriveGen` for `String` values, using `Gen.string`.
   */
  given DeriveGen[String] = instance(Gen.string)

  /**
   * A `DeriveGen` for `Unit` values, using `Gen.unit`.
   */
  given DeriveGen[Unit] = instance(Gen.unit)

  /**
   * A `DeriveGen` for `UUID` values, using `Gen.uuid`.
   */
  given DeriveGen[UUID] = instance(Gen.uuid)

  /**
   * A `DeriveGen` for `Instant` values, using `Gen.instant`.
   */
  given DeriveGen[Instant] = instance(Gen.instant)

  /**
   * A `DeriveGen` for `LocalDateTime` values, using `Gen.localDateTime`.
   */
  given DeriveGen[LocalDateTime] = instance(Gen.localDateTime)

  /**
   * A `DeriveGen` for `LocalDate` values, using `Gen.localDate`.
   */
  given DeriveGen[LocalDate] = instance(Gen.localDate)

  /**
   * A `DeriveGen` for `LocalTime` values, using `Gen.localTime`.
   */
  given DeriveGen[LocalTime] = instance(Gen.localTime)

  /**
   * A `DeriveGen` for `BigDecimal` values between `BigDecimal(Double.MinValue)
   * * BigDecimal(Double.MaxValue)` and `BigDecimal(Double.MaxValue) *
   * BigDecimal(Double.MaxValue)`, using `Gen.bigDecimal`.
   */
  given DeriveGen[BigDecimal] = instance(
    Gen.bigDecimal(
      BigDecimal(Double.MinValue) * BigDecimal(Double.MaxValue),
      BigDecimal(Double.MaxValue) * BigDecimal(Double.MaxValue)
    )
  )

  /**
   * Derives a `DeriveGen` for `Either[A, B]` from the instances for `A` and
   * `B`, using `Gen.either`.
   */
  given [A, B](using a: DeriveGen[A], b: DeriveGen[B]): DeriveGen[Either[A, B]] =
    instance(Gen.either(a.derive, b.derive))

  /**
   * Derives a `DeriveGen` for functions `A => B` from the instance for `B`,
   * using `Gen.function`.
   */
  given [A, B](using b: DeriveGen[B]): DeriveGen[A => B] =
    instance(Gen.function(b.derive))

  /**
   * Derives a `DeriveGen` for `Iterable[A]` from the instance for `A`. Each
   * value comes from `Gen.listOf`, `Gen.vectorOf` or `Gen.setOf`, picked with
   * equal probability.
   */
  given [A](using a: DeriveGen[A]): DeriveGen[Iterable[A]] =
    instance(Gen.oneOf(Gen.listOf(a.derive), Gen.vectorOf(a.derive), Gen.setOf(a.derive)))

  /**
   * Derives a `DeriveGen` for `List[A]` from the instance for `A`, using
   * `Gen.listOf`.
   */
  given [A](using a: DeriveGen[A]): DeriveGen[List[A]] =
    instance(Gen.listOf(a.derive))

  /**
   * Derives a `DeriveGen` for `Chunk[A]` from the instance for `A`, using
   * `Gen.chunkOf`.
   */
  given [A](using a: DeriveGen[A]): DeriveGen[Chunk[A]] =
    instance(Gen.chunkOf(a.derive))

  /**
   * Derives a `DeriveGen` for `Map[A, B]` from the instances for the keys and
   * the values, using `Gen.mapOf`.
   */
  given [A, B](using a: DeriveGen[A], b: DeriveGen[B]): DeriveGen[Map[A, B]] =
    instance(Gen.mapOf(a.derive, b.derive))

  /**
   * Derives a `DeriveGen` for `Option[A]` from the instance for `A`, using
   * `Gen.option`.
   */
  given [A](using a: => DeriveGen[A]): DeriveGen[Option[A]] =
    instance(Gen.option(a.derive))

  /**
   * Derives a `DeriveGen` for `Seq[A]` from the instance for `A`. Each value
   * comes from `Gen.listOf` or `Gen.vectorOf`, picked with equal probability.
   */
  given [A](using a: DeriveGen[A]): DeriveGen[Seq[A]] =
    instance(Gen.oneOf(Gen.listOf(a.derive), Gen.vectorOf(a.derive)))

  /**
   * Derives a `DeriveGen` for partial functions `PartialFunction[A, B]` from
   * the instance for `B`, using `Gen.partialFunction`.
   */
  given [A, B](using b: DeriveGen[B]): DeriveGen[PartialFunction[A, B]] =
    instance(Gen.partialFunction(b.derive))

  /**
   * Derives a `DeriveGen` for `Set[A]` from the instance for `A`, using
   * `Gen.setOf`.
   */
  given [A](using a: DeriveGen[A]): DeriveGen[Set[A]] =
    instance(Gen.setOf(a.derive))

  /**
   * Derives a `DeriveGen` for `Vector[A]` from the instance for `A`, using
   * `Gen.vectorOf`.
   */
  given [A](using a: DeriveGen[A]): DeriveGen[Vector[A]] =
    instance(Gen.vectorOf(a.derive))

  /**
   * A `DeriveGen` for `EmptyTuple` that always generates `EmptyTuple`. It is
   * the base case of tuple derivation.
   */
  given DeriveGen[EmptyTuple] = instance(Gen.const(EmptyTuple))

  /**
   * Derives a `DeriveGen` for the tuple `A *: T` from the instances for its
   * head `A` and its tail `T`. Together with the `EmptyTuple` instance, it
   * derives generators for tuples such as `(Int, String)`.
   */
  given [A, T <: Tuple](using a: DeriveGen[A], t: DeriveGen[T]): DeriveGen[A *: T] =
    instance((a.derive <*> t.derive).map(_ *: _))

  /**
   * Derives a `DeriveGen[T]` for a case class or sealed trait: with Magnolia on
   * Scala 2, and from the type's `Mirror` on Scala 3. On Scala 2 it is
   * implicit, so `DeriveGen[A]` derives instances without an import; on Scala 3
   * the `derived` given calls it.
   */
  inline def gen[T](using m: Mirror.Of[T]): DeriveGen[T] =
    new DeriveGen[T] {
      def derive: Gen[Any, T] = {
        val elemInstances = summonAll[m.MirroredElemTypes]
        inline m match {
          case s: Mirror.SumOf[T]     => genSum(s, elemInstances)
          case p: Mirror.ProductOf[T] => genProduct(p, elemInstances)
        }
      }
    }

  /**
   * Derives a `DeriveGen[T]` for a type with a `Mirror`, such as a case class
   * or a sealed trait, by calling `gen`. Because it is a given of the
   * `DeriveGen` companion, `DeriveGen[A]` derives instances without an import.
   */
  inline given derived[T](using m: Mirror.Of[T]): DeriveGen[T] =
    gen[T]

  /**
   * Builds the generator of a sum type: picks one generator of `instances` with
   * equal probability. `gen` calls it with one instance per direct subtype, so
   * a nested sealed trait counts as one subtype.
   */
  def genSum[T](s: Mirror.SumOf[T], instances: => List[DeriveGen[_]]): Gen[Any, T] =
    Gen.suspend(Gen.oneOf(instances.map(_.asInstanceOf[DeriveGen[T]].derive): _*))

  /**
   * Builds the generator of a product type: generates one value with each
   * instance of `instances`, in order, and constructs the product from them
   * with `p.fromProduct`. `gen` calls it with one instance per field.
   */
  def genProduct[T](p: Mirror.ProductOf[T], instances: => List[DeriveGen[_]]): Gen[Any, T] =
    Gen.suspend(Gen.collectAll(instances.map(_.derive)).map(lst => Tuple.fromArray(lst.toArray)).map(p.fromProduct))

  /**
   * Summons a `DeriveGen` for each element type of the tuple type `T`, in
   * order. Compilation fails when an element type has no instance.
   */
  inline def summonAll[T <: Tuple]: List[DeriveGen[_]] =
    inline erasedValue[T] match {
      case _: EmptyTuple => Nil
      case _: (t *: ts)  => summonInline[DeriveGen[t]] :: summonAll[ts]
    }
}
