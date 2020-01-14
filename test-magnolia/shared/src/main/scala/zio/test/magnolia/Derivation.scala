/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

import java.util.UUID

import zio.random.Random
import zio.test.Gen
import zio.test.Sized

import magnolia._

object Derivation {

  type Typeclass[T] = Gen[Random with Sized, T]

  implicit val genBoolean: Typeclass[Boolean] = Gen.boolean
  implicit val genByte: Typeclass[Byte]       = Gen.anyByte
  implicit val genChar: Typeclass[Char]       = Gen.anyChar
  implicit val genDouble: Typeclass[Double]   = Gen.anyDouble
  implicit val genFloat: Typeclass[Float]     = Gen.anyFloat
  implicit val genInt: Typeclass[Int]         = Gen.anyInt
  implicit val genLong: Typeclass[Long]       = Gen.anyLong
  implicit val genShort: Typeclass[Short]     = Gen.anyShort
  implicit val genString: Typeclass[String]   = Gen.anyString
  implicit val genUnit: Typeclass[Unit]       = Gen.unit
  implicit val genUUID: Typeclass[UUID]       = Gen.anyUUID

  implicit def genEither[A, B](implicit ev1: Typeclass[A], ev2: Typeclass[B]): Typeclass[Either[A, B]] =
    Gen.either(ev1, ev2)

  implicit def genFunction[A, B](implicit ev: Typeclass[B]): Typeclass[A => B] =
    Gen.function(ev)

  implicit def genList[A](implicit ev: Typeclass[A]): Typeclass[List[A]] =
    Gen.listOf(ev)

  implicit def genMap[A, B](implicit ev1: Typeclass[A], ev2: Typeclass[B]): Typeclass[Map[A, B]] =
    Gen.listOf(ev1 <&> ev2).map(_.toMap)

  implicit def genOption[A](implicit ev: Typeclass[A]): Typeclass[Option[A]] =
    Gen.option(ev)

  implicit def genPartialFunction[A, B](implicit ev: Typeclass[B]): Typeclass[PartialFunction[A, B]] =
    Gen.partialFunction(ev)

  implicit def genSeq[A](implicit ev: Typeclass[A]): Typeclass[Seq[A]] =
    Gen.listOf(ev)

  implicit def genSet[A](implicit ev: Typeclass[A]): Typeclass[Set[A]] =
    Gen.listOf(ev).map(_.toSet)

  implicit def genTuple2[A, B](implicit ev1: Typeclass[A], ev2: Typeclass[B]): Typeclass[(A, B)] =
    Gen.zipN(ev1, ev2)((_, _))

  implicit def genTuple3[A, B, C](
    implicit ev1: Typeclass[A],
    ev2: Typeclass[B],
    ev3: Typeclass[C]
  ): Typeclass[(A, B, C)] =
    Gen.zipN(ev1, ev2, ev3)((_, _, _))

  implicit def genTuple4[A, B, C, D](
    implicit ev1: Typeclass[A],
    ev2: Typeclass[B],
    ev3: Typeclass[C],
    ev4: Typeclass[D]
  ): Typeclass[(A, B, C, D)] =
    Gen.zipN(ev1, ev2, ev3, ev4)((_, _, _, _))

  def combine[T](caseClass: CaseClass[Typeclass, T]): Typeclass[T] =
    Gen.zipAll(caseClass.parameters.map(_.typeclass)).map(caseClass.rawConstruct)

  def dispatch[T](sealedTrait: SealedTrait[Typeclass, T]): Typeclass[T] =
    Gen.oneOf(sealedTrait.subtypes.map(_.typeclass): _*)

  implicit def gen[T]: Typeclass[T] = macro Magnolia.gen[T]
}
