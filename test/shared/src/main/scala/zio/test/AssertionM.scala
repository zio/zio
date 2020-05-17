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

package zio.test

import scala.reflect.ClassTag
import scala.util.Try

import zio.ZIO

/**
 * An `AssertionM[A]` is capable of producing assertion results on an `A`. As a
 * proposition, assertions compose using logical conjunction and disjunction,
 * and can be negated.
 */
trait AssertionM[-A] { self =>
  import zio.test.AssertionM.Render._

  def render: AssertionM.Render
  def runM: (=> A) => AssertResultM

  /**
   * Returns a new assertion that succeeds only if both assertions succeed.
   */
  def &&[A1 <: A](that: => AssertionM[A1]): AssertionM[A1] =
    AssertionM(infix(param(self), "&&", param(that)), actual => self.runM(actual) && that.runM(actual))

  /**
   * A symbolic alias for `label`.
   */
  def ??(string: String): AssertionM[A] =
    label(string)

  /**
   * Returns a new assertion that succeeds if either assertion succeeds.
   */
  def ||[A1 <: A](that: => AssertionM[A1]): AssertionM[A1] =
    AssertionM(infix(param(self), "||", param(that)), actual => self.runM(actual) || that.runM(actual))

  override def equals(that: Any): Boolean = that match {
    case that: AssertionM[_] => this.toString == that.toString
  }

  override def hashCode: Int =
    toString.hashCode

  /**
   * Labels this assertion with the specified string.
   */
  def label(string: String): AssertionM[A] =
    AssertionM(infix(param(self), "??", param(quoted(string))), runM)

  /**
   * Returns the negation of this assertion.
   */
  def negate: AssertionM[A] =
    AssertionM.not(self)

  /**
   * Provides a meaningful string rendering of the assertion.
   */
  override def toString: String =
    render.toString
}

object AssertionM {
  import zio.test.AssertionM.Render._

  def apply[A](_render: Render, _runM: (=> A) => AssertResultM): AssertionM[A] = new AssertionM[A] {
    val render: Render                = _render
    val runM: (=> A) => AssertResultM = _runM
  }

  /**
   * `Render` captures both the name of an assertion as well as the parameters
   * to the assertion combinator for pretty-printing.
   */
  sealed trait Render {
    override final def toString: String = this match {
      case Render.Function(name, paramLists) =>
        name + paramLists.map(_.mkString("(", ", ", ")")).mkString
      case Render.Infix(left, op, right) =>
        "(" + left + " " + op + " " + right + ")"
    }
  }
  object Render {
    final case class Function(name: String, paramLists: List[List[RenderParam]]) extends Render
    final case class Infix(left: RenderParam, op: String, right: RenderParam)    extends Render

    /**
     * Creates a string representation of a class name.
     */
    def className[A](C: ClassTag[A]): String =
      try {
        C.runtimeClass.getSimpleName
      } catch {
        // See https://github.com/scala/bug/issues/2034.
        case t: InternalError if t.getMessage == "Malformed class name" =>
          C.runtimeClass.getName
      }

    /**
     * Creates a string representation of a field accessor.
     */
    def field(name: String): String =
      "_." + name

    /**
     * Create a `Render` from an assertion combinator that should be rendered
     * using standard function notation.
     */
    def function(name: String, paramLists: List[List[RenderParam]]): Render =
      Render.Function(name, paramLists)

    /**
     * Create a `Render` from an assertion combinator that should be rendered
     * using infix function notation.
     */
    def infix(left: RenderParam, op: String, right: RenderParam): Render =
      Render.Infix(left, op, right)

    /**
     * Construct a `RenderParam` from an `AssertionM`.
     */
    def param[A](assertion: AssertionM[A]): RenderParam =
      RenderParam.AssertionM(assertion)

    /**
     * Construct a `RenderParam` from a value.
     */
    def param[A](value: A): RenderParam =
      RenderParam.Value(value)

    /**
     * Quote a string so it renders as a valid Scala string when rendered.
     */
    def quoted(string: String): String =
      "\"" + string + "\""

    /**
     * Creates a string representation of an unapply method for a term.
     */
    def unapply(termName: String): String =
      termName + ".unapply"
  }

  sealed trait RenderParam {
    override final def toString: String = this match {
      case RenderParam.AssertionM(assertion) => assertion.toString
      case RenderParam.Value(value)          => value.toString
    }
  }
  object RenderParam {
    final case class AssertionM[A](assertion: zio.test.AssertionM[A]) extends RenderParam
    final case class Value(value: Any)                                extends RenderParam
  }

  /**
   * Makes a new `AssertionM` from a pretty-printing and a function.
   */
  def assertionM[R, E, A](
    name: String
  )(params: RenderParam*)(run: (=> A) => ZIO[Any, Nothing, Boolean]): AssertionM[A] = {
    lazy val assertion: AssertionM[A] = assertionDirect(name)(params: _*) { actual =>
      lazy val tryActual = Try(actual)
      BoolAlgebraM.fromEffect(run(tryActual.get)).flatMap { p =>
        lazy val result: AssertResult =
          if (p) BoolAlgebra.success(AssertionValue(assertion, tryActual.get, result))
          else BoolAlgebra.failure(AssertionValue(assertion, tryActual.get, result))
        BoolAlgebraM(ZIO.succeed(result))
      }
    }
    assertion
  }

  /**
   * Makes a new `AssertionM` from a pretty-printing and a function.
   */
  def assertionDirect[A](
    name: String
  )(params: RenderParam*)(run: (=> A) => AssertResultM): AssertionM[A] =
    AssertionM(function(name, List(params.toList)), run)

  def assertionRecM[R, E, A, B](
    name: String
  )(params: RenderParam*)(
    assertion: AssertionM[B]
  )(
    get: (=> A) => ZIO[Any, Nothing, Option[B]],
    orElse: AssertionMData => AssertResultM = _.asFailureM
  ): AssertionM[A] = {
    lazy val resultAssertion: AssertionM[A] = assertionDirect(name)(params: _*) { a =>
      lazy val tryA = Try(a)
      BoolAlgebraM.fromEffect(get(tryA.get)).flatMap {
        case Some(b) =>
          BoolAlgebraM(assertion.runM(b).run.map { p =>
            lazy val result: AssertResult =
              if (p.isSuccess) BoolAlgebra.success(AssertionValue(resultAssertion, tryA.get, result))
              else BoolAlgebra.failure(AssertionValue(assertion, b, p))
            result
          })
        case None =>
          orElse(AssertionMData(resultAssertion, tryA.get))
      }
    }
    resultAssertion
  }

  /**
   * Makes a new assertion that negates the specified assertion.
   */
  def not[A](assertion: AssertionM[A]): AssertionM[A] =
    AssertionM.assertionDirect("not")(param(assertion))(!assertion.runM(_))

}
