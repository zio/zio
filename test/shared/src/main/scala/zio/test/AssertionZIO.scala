/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

import zio.{UIO, ZIO, ZTraceElement}
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.reflect.ClassTag
import scala.util.Try

/**
 * An `AssertionZIO[A]` is capable of producing assertion results on an `A`. As
 * a proposition, assertions compose using logical conjunction and disjunction,
 * and can be negated.
 */
abstract class AssertionZIO[-A] { self =>
  import zio.test.AssertionZIO.Render._

  def render: AssertionZIO.Render
  def runZIO: (=> A) => AssertResultZIO

  /**
   * Returns a new assertion that succeeds only if both assertions succeed.
   */
  def &&[A1 <: A](that: => AssertionZIO[A1])(implicit trace: ZTraceElement): AssertionZIO[A1] =
    AssertionZIO(infix(param(self), "&&", param(that)), actual => self.runZIO(actual) && that.runZIO(actual))

  /**
   * A symbolic alias for `label`.
   */
  def ??(string: String): AssertionZIO[A] =
    label(string)

  /**
   * Returns a new assertion that succeeds if either assertion succeeds.
   */
  def ||[A1 <: A](that: => AssertionZIO[A1])(implicit trace: ZTraceElement): AssertionZIO[A1] =
    AssertionZIO(infix(param(self), "||", param(that)), actual => self.runZIO(actual) || that.runZIO(actual))

  def canEqual(that: AssertionZIO[_]): Boolean = that != null

  override def equals(that: Any): Boolean = that match {
    case that: AssertionZIO[_] if that.canEqual(this) => this.toString == that.toString
    case _                                            => false
  }

  override def hashCode: Int =
    toString.hashCode

  /**
   * Labels this assertion with the specified string.
   */
  def label(string: String): AssertionZIO[A] =
    AssertionZIO(infix(param(self), "??", param(quoted(string))), runZIO)

  /**
   * Returns the negation of this assertion.
   */
  def negate(implicit trace: ZTraceElement): AssertionZIO[A] =
    AssertionZIO.not(self)

  /**
   * Provides a meaningful string rendering of the assertion.
   */
  override def toString: String =
    render.toString
}

object AssertionZIO {
  import zio.test.AssertionZIO.Render._

  def apply[A](_render: Render, _runZIO: (=> A) => AssertResultZIO): AssertionZIO[A] = new AssertionZIO[A] {
    val render: Render                    = _render
    val runZIO: (=> A) => AssertResultZIO = _runZIO
  }

  /**
   * `Render` captures both the name of an assertion as well as the parameters
   * to the assertion combinator for pretty-printing.
   */
  sealed abstract class Render {
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
     * Construct a `RenderParam` from an `AssertionZIO`.
     */
    def param[A](assertion: AssertionZIO[A]): RenderParam =
      RenderParam.AssertionZIO(assertion)

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

  sealed abstract class RenderParam {
    override final def toString: String = this match {
      case RenderParam.AssertionZIO(assertion) => assertion.toString
      case RenderParam.Value(value)            => value.toString
    }
  }
  object RenderParam {
    final case class AssertionZIO[A](assertion: zio.test.AssertionZIO[A]) extends RenderParam
    final case class Value(value: Any)                                    extends RenderParam
  }

  /**
   * Makes a new `AssertionZIO` from a pretty-printing and a function.
   */
  def assertionZIO[R, E, A](
    name: String
  )(params: RenderParam*)(run: (=> A) => UIO[Boolean])(implicit trace: ZTraceElement): AssertionZIO[A] = {
    lazy val assertion: AssertionZIO[A] = assertionDirect(name)(params: _*) { actual =>
      lazy val tryActual = Try(actual)
      BoolAlgebraZIO.fromZIO(run(tryActual.get)).flatMap { p =>
        lazy val result: AssertResult =
          if (p) BoolAlgebra.success(AssertionValue(assertion, tryActual.get, result))
          else BoolAlgebra.failure(AssertionValue(assertion, tryActual.get, result))
        BoolAlgebraZIO(ZIO.succeed(result))
      }
    }
    assertion
  }

  /**
   * Makes a new `AssertionZIO` from a pretty-printing and a function.
   */
  def assertionDirect[A](
    name: String
  )(params: RenderParam*)(run: (=> A) => AssertResultZIO): AssertionZIO[A] =
    AssertionZIO(function(name, List(params.toList)), run)

  def assertionRecZIO[R, E, A, B](
    name: String
  )(params: RenderParam*)(
    assertion: AssertionZIO[B]
  )(
    get: (=> A) => ZIO[Any, Nothing, Option[B]],
    orElse: AssertionZIOData => AssertResultZIO = _.asFailureZIO(ZTraceElement.empty)
  )(implicit trace: ZTraceElement): AssertionZIO[A] = {
    lazy val resultAssertion: AssertionZIO[A] = assertionDirect(name)(params: _*) { a =>
      lazy val tryA = Try(a)
      BoolAlgebraZIO.fromZIO(get(tryA.get)).flatMap {
        case Some(b) =>
          BoolAlgebraZIO(assertion.runZIO(b).run.map { p =>
            lazy val result: AssertResult =
              if (p.isSuccess) BoolAlgebra.success(AssertionValue(resultAssertion, tryA.get, result))
              else BoolAlgebra.failure(AssertionValue(assertion, b, p))
            result
          })
        case None =>
          orElse(AssertionZIOData(resultAssertion, tryA.get))
      }
    }
    resultAssertion
  }

  /**
   * Makes a new assertion that negates the specified assertion.
   */
  def not[A](assertion: AssertionZIO[A])(implicit trace: ZTraceElement): AssertionZIO[A] =
    AssertionZIO.assertionDirect("not")(param(assertion))(!assertion.runZIO(_))

}
