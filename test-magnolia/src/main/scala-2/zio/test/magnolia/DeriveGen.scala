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

import magnolia1._
import zio.Chunk
import zio.test.Gen

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}
import java.util.UUID

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
 * probability. To use a type that is neither a case class nor a sealed trait,
 * give it an implicit `DeriveGen` with `DeriveGen.instance`.
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
  def apply[A](implicit ev: DeriveGen[A]): Gen[Any, A] = ev.derive

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
  def instance[A](gen: Gen[Any, A]): DeriveGen[A] =
    new DeriveGen[A] {
      val derive: Gen[Any, A] = gen
    }

  /**
   * A `DeriveGen` for `Boolean` values, using `Gen.boolean`.
   */
  implicit val genBoolean: DeriveGen[Boolean] = instance(Gen.boolean)

  /**
   * A `DeriveGen` for `Byte` values, using `Gen.byte`.
   */
  implicit val genByte: DeriveGen[Byte] = instance(Gen.byte)

  /**
   * A `DeriveGen` for `Char` values, using `Gen.char`.
   */
  implicit val genChar: DeriveGen[Char] = instance(Gen.char)

  /**
   * A `DeriveGen` for `Double` values, using `Gen.double`.
   */
  implicit val genDouble: DeriveGen[Double] = instance(Gen.double)

  /**
   * A `DeriveGen` for `Float` values, using `Gen.float`.
   */
  implicit val genFloat: DeriveGen[Float] = instance(Gen.float)

  /**
   * A `DeriveGen` for `Int` values, using `Gen.int`.
   */
  implicit val genInt: DeriveGen[Int] = instance(Gen.int)

  /**
   * A `DeriveGen` for `Long` values, using `Gen.long`.
   */
  implicit val genLong: DeriveGen[Long] = instance(Gen.long)

  /**
   * A `DeriveGen` for `Short` values, using `Gen.short`.
   */
  implicit val genShort: DeriveGen[Short] = instance(Gen.short)

  /**
   * A `DeriveGen` for `String` values, using `Gen.string`.
   */
  implicit val genString: DeriveGen[String] = instance(Gen.string)

  /**
   * A `DeriveGen` for `Unit` values, using `Gen.unit`.
   */
  implicit val genUnit: DeriveGen[Unit] = instance(Gen.unit)

  /**
   * A `DeriveGen` for `UUID` values, using `Gen.uuid`.
   */
  implicit val genUUID: DeriveGen[UUID] = instance(Gen.uuid)

  /**
   * A `DeriveGen` for `Instant` values, using `Gen.instant`.
   */
  implicit val genInstant: DeriveGen[Instant] = instance(Gen.instant)

  /**
   * A `DeriveGen` for `LocalDateTime` values, using `Gen.localDateTime`.
   */
  implicit val genLocalDateTime: DeriveGen[LocalDateTime] = instance(Gen.localDateTime)

  /**
   * A `DeriveGen` for `LocalDate` values, using `Gen.localDate`.
   */
  implicit val genLocalDate: DeriveGen[LocalDate] = instance(Gen.localDate)

  /**
   * A `DeriveGen` for `LocalTime` values, using `Gen.localTime`.
   */
  implicit val genLocalTime: DeriveGen[LocalTime] = instance(Gen.localTime)

  /**
   * A `DeriveGen` for `BigDecimal` values between `BigDecimal(Double.MinValue)
   * * BigDecimal(Double.MaxValue)` and `BigDecimal(Double.MaxValue) *
   * BigDecimal(Double.MaxValue)`, using `Gen.bigDecimal`.
   */
  implicit val genBigDecimal: DeriveGen[BigDecimal] = instance(
    Gen.bigDecimal(
      BigDecimal(Double.MinValue) * BigDecimal(Double.MaxValue),
      BigDecimal(Double.MaxValue) * BigDecimal(Double.MaxValue)
    )
  )

  /**
   * A `DeriveGen` for `BigInt` values between `BigInt(Int.MinValue) *
   * BigInt(Int.MaxValue)` and `BigInt(Int.MaxValue) * BigInt(Int.MaxValue)`,
   * using `Gen.bigInt`. Scala 3 has no such instance.
   */
  implicit val genBigInt: DeriveGen[BigInt] = instance(
    Gen.bigInt(
      BigInt(Int.MinValue) * BigInt(Int.MaxValue),
      BigInt(Int.MaxValue) * BigInt(Int.MaxValue)
    )
  )

  /**
   * A `DeriveGen` for `java.math.BigInteger` values between
   * `BigInt(Int.MinValue) * BigInt(Int.MaxValue)` and `BigInt(Int.MaxValue) *
   * BigInt(Int.MaxValue)`, using `Gen.bigIntegerJava`. Scala 3 has no such
   * instance.
   */
  implicit val genBigIntegerJava: DeriveGen[java.math.BigInteger] = instance(
    Gen
      .bigIntegerJava(
        BigInt(Int.MinValue) * BigInt(Int.MaxValue),
        BigInt(Int.MaxValue) * BigInt(Int.MaxValue)
      )
  )

  /**
   * A `DeriveGen` for `java.math.BigDecimal` values between
   * `BigDecimal(Double.MinValue) * BigDecimal(Double.MaxValue)` and
   * `BigDecimal(Double.MaxValue) * BigDecimal(Double.MaxValue)`, using
   * `Gen.bigDecimalJava`. Scala 3 has no such instance.
   */
  implicit val genJavaBigDecimalGen: DeriveGen[java.math.BigDecimal] = instance(
    Gen
      .bigDecimalJava(
        BigDecimal(Double.MinValue) * BigDecimal(Double.MaxValue),
        BigDecimal(Double.MaxValue) * BigDecimal(Double.MaxValue)
      )
  )

  /**
   * Derives a `DeriveGen` for `Either[A, B]` from the instances for `A` and
   * `B`, using `Gen.either`.
   */
  implicit def genEither[A, B](implicit ev1: DeriveGen[A], ev2: DeriveGen[B]): DeriveGen[Either[A, B]] =
    instance(Gen.either(ev1.derive, ev2.derive))

  /**
   * Derives a `DeriveGen` for functions `A => B` from the instance for `B`,
   * using `Gen.function`.
   */
  implicit def genFunction[A, B](implicit ev: DeriveGen[B]): DeriveGen[A => B] =
    instance(Gen.function(ev.derive))

  /**
   * Derives a `DeriveGen` for `Iterable[A]` from the instance for `A`. Each
   * value comes from `Gen.listOf`, `Gen.vectorOf` or `Gen.setOf`, picked with
   * equal probability.
   */
  implicit def genIterable[A](implicit ev: DeriveGen[A]): DeriveGen[Iterable[A]] =
    instance(Gen.oneOf(Gen.listOf(ev.derive), Gen.vectorOf(ev.derive), Gen.setOf(ev.derive)))

  /**
   * Derives a `DeriveGen` for `List[A]` from the instance for `A`, using
   * `Gen.listOf`.
   */
  implicit def genList[A](implicit ev: DeriveGen[A]): DeriveGen[List[A]] =
    instance(Gen.listOf(ev.derive))

  /**
   * Derives a `DeriveGen` for `Chunk[A]` from the instance for `A`, using
   * `Gen.chunkOf`.
   */
  implicit def genChunk[A](implicit ev: DeriveGen[A]): DeriveGen[Chunk[A]] =
    instance(Gen.chunkOf(ev.derive))

  /**
   * Derives a `DeriveGen` for `Map[A, B]` from the instances for the keys and
   * the values, using `Gen.mapOf`.
   */
  implicit def genMap[A, B](implicit ev1: DeriveGen[A], ev2: DeriveGen[B]): DeriveGen[Map[A, B]] =
    instance(Gen.mapOf(ev1.derive, ev2.derive))

  /**
   * Derives a `DeriveGen` for `Option[A]` from the instance for `A`, using
   * `Gen.option`.
   */
  implicit def genOption[A](implicit ev: DeriveGen[A]): DeriveGen[Option[A]] =
    instance(Gen.option(ev.derive))

  /**
   * Derives a `DeriveGen` for `Seq[A]` from the instance for `A`. Each value
   * comes from `Gen.listOf` or `Gen.vectorOf`, picked with equal probability.
   */
  implicit def genSeq[A](implicit ev: DeriveGen[A]): DeriveGen[Seq[A]] =
    instance(Gen.oneOf(Gen.listOf(ev.derive), Gen.vectorOf(ev.derive)))

  /**
   * Derives a `DeriveGen` for partial functions `PartialFunction[A, B]` from
   * the instance for `B`, using `Gen.partialFunction`.
   */
  implicit def genPartialFunction[A, B](implicit ev: DeriveGen[B]): DeriveGen[PartialFunction[A, B]] =
    instance(Gen.partialFunction(ev.derive))

  /**
   * Derives a `DeriveGen` for `Set[A]` from the instance for `A`, using
   * `Gen.setOf`.
   */
  implicit def genSet[A](implicit ev: DeriveGen[A]): DeriveGen[Set[A]] =
    instance(Gen.setOf(ev.derive))

  /**
   * Derives a `DeriveGen` for pairs from the instances for each element. On
   * Scala 3, tuples are derived through the `EmptyTuple` and `*:` instances
   * instead.
   */
  implicit def genTuple2[A, B](implicit ev1: DeriveGen[A], ev2: DeriveGen[B]): DeriveGen[(A, B)] =
    instance(ev1.derive <*> ev2.derive)

  /**
   * Derives a `DeriveGen` for 3-tuples from the instances for each element. On
   * Scala 3, tuples are derived through the `EmptyTuple` and `*:` instances
   * instead.
   */
  implicit def genTuple3[A, B, C](implicit
    ev1: DeriveGen[A],
    ev2: DeriveGen[B],
    ev3: DeriveGen[C]
  ): DeriveGen[(A, B, C)] =
    instance(ev1.derive <*> ev2.derive <*> ev3.derive)

  /**
   * Derives a `DeriveGen` for 4-tuples from the instances for each element. On
   * Scala 3, tuples are derived through the `EmptyTuple` and `*:` instances
   * instead.
   */
  implicit def genTuple4[A, B, C, D](implicit
    ev1: DeriveGen[A],
    ev2: DeriveGen[B],
    ev3: DeriveGen[C],
    ev4: DeriveGen[D]
  ): DeriveGen[(A, B, C, D)] =
    instance(ev1.derive <*> ev2.derive <*> ev3.derive <*> ev4.derive)

  /**
   * Derives a `DeriveGen` for 5-tuples from the instances for each element. On
   * Scala 3, tuples are derived through the `EmptyTuple` and `*:` instances
   * instead.
   */
  implicit def genTuple5[A, B, C, D, F](implicit
    ev1: DeriveGen[A],
    ev2: DeriveGen[B],
    ev3: DeriveGen[C],
    ev4: DeriveGen[D],
    ev5: DeriveGen[F]
  ): DeriveGen[(A, B, C, D, F)] =
    instance(ev1.derive <*> ev2.derive <*> ev3.derive <*> ev4.derive <*> ev5.derive)

  /**
   * Derives a `DeriveGen` for 6-tuples from the instances for each element. On
   * Scala 3, tuples are derived through the `EmptyTuple` and `*:` instances
   * instead.
   */
  implicit def genTuple6[A, B, C, D, F, G](implicit
    ev1: DeriveGen[A],
    ev2: DeriveGen[B],
    ev3: DeriveGen[C],
    ev4: DeriveGen[D],
    ev5: DeriveGen[F],
    ev6: DeriveGen[G]
  ): DeriveGen[(A, B, C, D, F, G)] =
    instance(ev1.derive <*> ev2.derive <*> ev3.derive <*> ev4.derive <*> ev5.derive <*> ev6.derive)

  /**
   * Derives a `DeriveGen` for 7-tuples from the instances for each element. On
   * Scala 3, tuples are derived through the `EmptyTuple` and `*:` instances
   * instead.
   */
  implicit def genTuple7[A, B, C, D, F, G, H](implicit
    ev1: DeriveGen[A],
    ev2: DeriveGen[B],
    ev3: DeriveGen[C],
    ev4: DeriveGen[D],
    ev5: DeriveGen[F],
    ev6: DeriveGen[G],
    ev7: DeriveGen[H]
  ): DeriveGen[(A, B, C, D, F, G, H)] =
    instance(ev1.derive <*> ev2.derive <*> ev3.derive <*> ev4.derive <*> ev5.derive <*> ev6.derive <*> ev7.derive)

  /**
   * Derives a `DeriveGen` for 8-tuples from the instances for each element. On
   * Scala 3, tuples are derived through the `EmptyTuple` and `*:` instances
   * instead.
   */
  implicit def genTuple8[A, B, C, D, F, G, H, I](implicit
    ev1: DeriveGen[A],
    ev2: DeriveGen[B],
    ev3: DeriveGen[C],
    ev4: DeriveGen[D],
    ev5: DeriveGen[F],
    ev6: DeriveGen[G],
    ev7: DeriveGen[H],
    ev8: DeriveGen[I]
  ): DeriveGen[(A, B, C, D, F, G, H, I)] =
    instance(
      ev1.derive <*> ev2.derive <*> ev3.derive <*> ev4.derive <*> ev5.derive <*> ev6.derive <*> ev7.derive <*> ev8.derive
    )

  /**
   * Derives a `DeriveGen` for 9-tuples from the instances for each element. On
   * Scala 3, tuples are derived through the `EmptyTuple` and `*:` instances
   * instead.
   */
  implicit def genTuple9[A, B, C, D, F, G, H, I, J](implicit
    ev1: DeriveGen[A],
    ev2: DeriveGen[B],
    ev3: DeriveGen[C],
    ev4: DeriveGen[D],
    ev5: DeriveGen[F],
    ev6: DeriveGen[G],
    ev7: DeriveGen[H],
    ev8: DeriveGen[I],
    ev9: DeriveGen[J]
  ): DeriveGen[(A, B, C, D, F, G, H, I, J)] =
    instance(
      ev1.derive <*> ev2.derive <*> ev3.derive <*> ev4.derive <*> ev5.derive <*> ev6.derive <*> ev7.derive <*> ev8.derive <*> ev9.derive
    )

  /**
   * Derives a `DeriveGen` for 10-tuples from the instances for each element. On
   * Scala 3, tuples are derived through the `EmptyTuple` and `*:` instances
   * instead.
   */
  implicit def genTuple10[A, B, C, D, F, G, H, I, J, K](implicit
    ev1: DeriveGen[A],
    ev2: DeriveGen[B],
    ev3: DeriveGen[C],
    ev4: DeriveGen[D],
    ev5: DeriveGen[F],
    ev6: DeriveGen[G],
    ev7: DeriveGen[H],
    ev8: DeriveGen[I],
    ev9: DeriveGen[J],
    ev10: DeriveGen[K]
  ): DeriveGen[(A, B, C, D, F, G, H, I, J, K)] =
    instance(
      ev1.derive <*> ev2.derive <*> ev3.derive <*> ev4.derive <*> ev5.derive <*> ev6.derive <*> ev7.derive <*> ev8.derive <*> ev9.derive <*> ev10.derive
    )

  /**
   * Derives a `DeriveGen` for `Vector[A]` from the instance for `A`, using
   * `Gen.vectorOf`.
   */
  implicit def genVector[A](implicit ev: DeriveGen[A]): DeriveGen[Vector[A]] =
    instance(Gen.vectorOf(ev.derive))

  /**
   * The type class that Magnolia derives, `DeriveGen`.
   */
  type Typeclass[T] = DeriveGen[T]

  /**
   * Derives the `DeriveGen` of a case class: generates each parameter with its
   * instance and constructs the case class from the results. Magnolia calls it
   * during derivation.
   */
  def join[T](caseClass: CaseClass[Typeclass, T]): Typeclass[T] =
    instance(Gen.suspend(Gen.collectAll(caseClass.parameters.map(_.typeclass.derive)).map(caseClass.rawConstruct)))

  /**
   * Derives the `DeriveGen` of a sealed trait: picks one of its subtypes with
   * equal probability and generates a value with that subtype's instance.
   * Magnolia calls it during derivation.
   */
  def split[T](sealedTrait: SealedTrait[Typeclass, T]): Typeclass[T] =
    instance(Gen.suspend(Gen.oneOf(sealedTrait.subtypes.map(_.typeclass.derive): _*)))

  /**
   * Derives a `DeriveGen[T]` for a case class or sealed trait: with Magnolia on
   * Scala 2, and from the type's `Mirror` on Scala 3. On Scala 2 it is
   * implicit, so `DeriveGen[A]` derives instances without an import; on Scala 3
   * the `derived` given calls it.
   */
  implicit def gen[T]: Typeclass[T] = macro Magnolia.gen[T]
}
