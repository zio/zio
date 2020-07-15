/*
 * Copyright 2017-2020 John A. De Goes and the ZIO Contributors
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

package zio.test.mock

import java.util.UUID

import com.github.ghik.silencer.silent

import zio.test.Assertion
import zio.{ =!=, taggedIsSubtype, taggedTagType, Has, IO, LightTypeTag, Tag }

/**
 * A `Capability[R, I, E, A]` represents a capability of environment `R` that takes an input `I`
 * and returns an effect that may fail with an error `E` or produce a single `A`.
 *
 * To represent polymorphic capabilities you must use one of lazy `Capability.Poly` types which
 * allow you to delay the declaration of some types to call site.
 *
 * To construct capability tags you should start by creating a `Mock[R]` and extend publicly
 * available `Effect`, `Method`, `Sink` or `Stream` type members.
 */
protected[mock] abstract class Capability[R <: Has[_]: Tag, I: Tag, E: Tag, A: Tag](val mock: Mock[R])
    extends Capability.Base[R] { self =>

  val inputTag: LightTypeTag  = taggedTagType(implicitly[Tag[I]])
  val errorTag: LightTypeTag  = taggedTagType(implicitly[Tag[E]])
  val outputTag: LightTypeTag = taggedTagType(implicitly[Tag[A]])

  @silent("is never used")
  def apply()(implicit ev1: I =:= Unit, ev2: A <:< Unit): Expectation[R] =
    Expectation.Call[R, I, E, A](
      self,
      Assertion.isUnit.asInstanceOf[Assertion[I]],
      ((_: I) => IO.unit).asInstanceOf[I => IO[E, A]]
    )

  @silent("is never used")
  def apply(assertion: Assertion[I])(implicit ev1: I =!= Unit, ev2: A <:< Unit): Expectation[R] =
    Expectation.Call[R, I, E, A](self, assertion, ((_: I) => IO.unit).asInstanceOf[I => IO[E, A]])

  @silent("is never used")
  def apply(assertion: Assertion[I], result: Result[I, E, A])(implicit ev: I =!= Unit): Expectation[R] =
    Expectation.Call[R, I, E, A](self, assertion, result.io)

  @silent("is never used")
  def apply(returns: Result[I, E, A])(implicit ev: I <:< Unit): Expectation[R] =
    Expectation.Call[R, I, E, A](self, Assertion.isUnit.asInstanceOf[Assertion[I]], returns.io)

  def isEqual[R0 <: Has[_], I0, E0, A0](that: Capability[R0, I0, E0, A0]): Boolean =
    self.id == that.id &&
      taggedIsSubtype(self.inputTag, that.inputTag) &&
      taggedIsSubtype(self.errorTag, that.errorTag) &&
      taggedIsSubtype(self.outputTag, that.outputTag)
}

object Capability {

  protected abstract class Base[R <: Has[_]] {

    val id: UUID = UUID.randomUUID
    val mock: Mock[R]

    /**
     * Render method fully qualified name.
     */
    override val toString: String = {
      val fragments = getClass.getName.replaceAll("\\$", ".").split("\\.")
      fragments.toList.splitAt(fragments.size - 3) match {
        case (namespace, module :: service :: method :: Nil) =>
          s"""${namespace.mkString(".")}.$module.$service.$method"""
        case _ => fragments.mkString(".")
      }
    }

  }

  sealed abstract class Unknown

  protected[mock] abstract class Poly[R <: Has[_]: Tag, I, E, A] extends Base[R]

  object Poly {

    /**
     * Represents capability of environment `R` polymorphic in its input type.
     */
    protected[mock] abstract class Input[R <: Has[_]: Tag, E: Tag, A: Tag](val mock: Mock[R])
        extends Poly[R, Unknown, E, A] { self =>

      def of[I: Tag]: Capability[R, I, E, A] =
        toMethod[R, I, E, A](self)

      @silent("is never used")
      def of[I: Tag](assertion: Assertion[I])(implicit ev1: I =!= Unit, ev2: A <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion)

      @silent("is never used")
      def of[I: Tag](assertion: Assertion[I], result: Result[I, E, A])(implicit ev: I =!= Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion, result)

      @silent("is never used")
      def of[I: Tag](returns: Result[I, E, A])(implicit ev: I <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, returns)
    }

    /**
     * Represents capability of environment `R` polymorphic in its error type.
     */
    protected[mock] abstract class Error[R <: Has[_]: Tag, I: Tag, A: Tag, E1](val mock: Mock[R])
        extends Poly[R, I, Unknown, A] { self =>

      def of[E <: E1: Tag]: Capability[R, I, E, A] =
        toMethod[R, I, E, A](self)

      @silent("is never used")
      def of[E <: E1: Tag](assertion: Assertion[I])(implicit ev1: I =!= Unit, ev2: A <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion)

      @silent("is never used")
      def of[E <: E1: Tag](assertion: Assertion[I], result: Result[I, E, A])(
        implicit ev: I =!= Unit
      ): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion, result)

      @silent("is never used")
      def of[E <: E1: Tag](returns: Result[I, E, A])(implicit ev: I <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, returns)
    }

    /**
     * Represents capability of environment `R` polymorphic in its output type.
     */
    protected[mock] abstract class Output[R <: Has[_]: Tag, I: Tag, E: Tag, A1](val mock: Mock[R])
        extends Poly[R, I, E, Unknown] { self =>

      def of[A <: A1: Tag]: Capability[R, I, E, A] =
        toMethod[R, I, E, A](self)

      @silent("is never used")
      def of[A <: A1: Tag](assertion: Assertion[I])(implicit ev1: I =!= Unit, ev2: A <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion)

      @silent("is never used")
      def of[A <: A1: Tag](assertion: Assertion[I], result: Result[I, E, A])(
        implicit ev: I =!= Unit
      ): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion, result)

      @silent("is never used")
      def of[A <: A1: Tag](returns: Result[I, E, A])(implicit ev: I <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, returns)
    }

    /**
     * Represents capability of environment `R` polymorphic in its input and error types.
     */
    protected[mock] abstract class InputError[R <: Has[_]: Tag, A: Tag, E1](val mock: Mock[R])
        extends Poly[R, Unknown, Unknown, A] { self =>

      def of[I: Tag, E <: E1: Tag]: Capability[R, I, E, A] =
        toMethod[R, I, E, A](self)

      @silent("is never used")
      def of[I: Tag, E <: E1: Tag](
        assertion: Assertion[I]
      )(implicit ev1: I =!= Unit, ev2: A <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion)

      @silent("is never used")
      def of[I: Tag, E <: E1: Tag](assertion: Assertion[I], result: Result[I, E, A])(
        implicit ev: I =!= Unit
      ): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion, result)

      @silent("is never used")
      def of[I: Tag, E <: E1: Tag](returns: Result[I, E, A])(implicit ev: I <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, returns)
    }

    /**
     * Represents capability of environment `R` polymorphic in its input and output types.
     */
    protected[mock] abstract class InputOutput[R <: Has[_]: Tag, E: Tag, A1](val mock: Mock[R])
        extends Poly[R, Unknown, E, Unknown] { self =>

      def of[I: Tag, A <: A1: Tag]: Capability[R, I, E, A] =
        toMethod[R, I, E, A](self)

      @silent("is never used")
      def of[I: Tag, A <: A1: Tag](
        assertion: Assertion[I]
      )(implicit ev1: I =!= Unit, ev2: A <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion)

      @silent("is never used")
      def of[I: Tag, A <: A1: Tag](assertion: Assertion[I], result: Result[I, E, A])(
        implicit ev: I =!= Unit
      ): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion, result)

      @silent("is never used")
      def of[I: Tag, A <: A1: Tag](returns: Result[I, E, A])(implicit ev: I <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, returns)
    }

    /**
     * Represents capability of environment `R` polymorphic in its error and output types.
     */
    protected[mock] abstract class ErrorOutput[R <: Has[_]: Tag, I: Tag, E1, A1](val mock: Mock[R])
        extends Poly[R, I, Unknown, Unknown] { self =>

      def of[E <: E1: Tag, A <: A1: Tag]: Capability[R, I, E, A] =
        toMethod[R, I, E, A](self)

      @silent("is never used")
      def of[E <: E1: Tag, A <: A1: Tag](
        assertion: Assertion[I]
      )(implicit ev1: I =!= Unit, ev2: A <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion)

      @silent("is never used")
      def of[E <: E1: Tag, A <: A1: Tag](assertion: Assertion[I], result: Result[I, E, A])(
        implicit ev: I =!= Unit
      ): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion, result)

      @silent("is never used")
      def of[E <: E1: Tag, A <: A1: Tag](returns: Result[I, E, A])(implicit ev: I <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, returns)
    }

    /**
     * Represents capability of environment `R` polymorphic in its input, error and output types.
     */
    protected[mock] abstract class InputErrorOutput[R <: Has[_]: Tag, E1, A1](val mock: Mock[R])
        extends Poly[R, Unknown, Unknown, Unknown] { self =>

      def of[I: Tag, E <: E1: Tag, A <: A1: Tag]: Capability[R, I, E, A] =
        toMethod[R, I, E, A](self)

      @silent("is never used")
      def of[I: Tag, E <: E1: Tag, A <: A1: Tag](
        assertion: Assertion[I]
      )(implicit ev1: I =!= Unit, ev2: A <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion)

      @silent("is never used")
      def of[I: Tag, E <: E1: Tag, A <: A1: Tag](assertion: Assertion[I], result: Result[I, E, A])(
        implicit ev: I =!= Unit
      ): Expectation[R] =
        toExpectation[R, I, E, A](self, assertion, result)

      @silent("is never used")
      def of[I: Tag, E <: E1: Tag, A <: A1: Tag](
        returns: Result[I, E, A]
      )(implicit ev: I <:< Unit): Expectation[R] =
        toExpectation[R, I, E, A](self, returns)
    }

    @silent("is never used")
    private def toExpectation[R <: Has[_]: Tag, I: Tag, E: Tag, A: Tag](
      poly: Poly[R, _, _, _],
      assertion: Assertion[I]
    )(implicit ev1: I =!= Unit, ev2: A <:< Unit): Expectation[R] =
      Expectation.Call[R, I, E, A](
        toMethod[R, I, E, A](poly),
        assertion,
        ((_: I) => IO.unit).asInstanceOf[I => IO[E, A]]
      )

    @silent("is never used")
    private def toExpectation[R <: Has[_]: Tag, I: Tag, E: Tag, A: Tag](
      poly: Poly[R, _, _, _],
      assertion: Assertion[I],
      result: Result[I, E, A]
    )(implicit ev: I =!= Unit): Expectation[R] =
      Expectation.Call[R, I, E, A](toMethod[R, I, E, A](poly), assertion, result.io)

    @silent("is never used")
    private def toExpectation[R <: Has[_]: Tag, I: Tag, E: Tag, A: Tag](
      poly: Poly[R, _, _, _],
      returns: Result[I, E, A]
    )(implicit ev: I <:< Unit): Expectation[R] =
      Expectation.Call[R, I, E, A](toMethod[R, I, E, A](poly), Assertion.isUnit.asInstanceOf[Assertion[I]], returns.io)

    private def toMethod[R <: Has[_]: Tag, I: Tag, E: Tag, A: Tag](
      poly: Poly[R, _, _, _]
    ): Capability[R, I, E, A] = new Capability[R, I, E, A](poly.mock) {
      override val id: UUID         = poly.id
      override val toString: String = poly.toString
    }
  }
}
