/*
 * Copyright 2021 John A. De Goes and the ZIO Contributors
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

import scala.annotation.implicitNotFound

/**
 * The `CombineEnv[L, R]` trait defines how to combine two environments of type `L` and `R`.
 * Currently, the trait can combine multiple environments using `Has` maps.
 */
@implicitNotFound(
  "The operator you are trying to use needs to combine multiple services, " +
    "which requires use of the Has data type. If you are writing " +
    "polymorphic code, then be sure to add a `: Tag` or `: TagK` constraint " +
    "on your type parameters, or manually wrap your services in Has."
)
sealed trait CombineEnv[L, R] {

  /**
   * The type which results from combining an environment of type `L` with an
   *    environment of type `R`.
   */
  type Out

  /**
   * Combines two environments into a type which "holds" both of them.
   */
  def combine(l: L, r: R): Out

  /**
   * Projects out the left part of the combined environment.
   */
  def projectLeft(out: Out): L

  /**
   * Projects out the right part of the combined environment.
   */
  def projectRight(out: Out): R

  /**
   * Updates the left part of a combined environment.
   */
  final def updateLeft(out: Out)(f: L => L): Out = {
    val l = projectLeft(out)
    val r = projectRight(out)

    combine(f(l), r)
  }

  /**
   * Updates the right part of a combined environment.
   */
  final def updateRight(out: Out)(f: R => R): Out = {
    val l = projectLeft(out)
    val r = projectRight(out)

    combine(l, f(r))
  }
}
object CombineEnv extends MediumPriorityUnionAllImplicits {

  /**
   * A type alias that refines `CombineEnv` to include a third type parameter,
   * which is the type of the combined environment (called `Out`).
   */
  type WithOut[L, R, Out0] = CombineEnv[L, R] { type Out = Out0 }

  implicit def hasHasTag[L <: Has[_], R <: Has[_]: Tag]: CombineEnvIntersection[L, R] =
    new CombineEnvIntersection[L, R] {
      def projectLeft(out: Out): L = out

      def projectRight(out: Out): R = out

      def combine(l: L, r: R): Out = l.union[R](r)
    }
}
private[zio] trait MediumPriorityUnionAllImplicits extends LowPriorityUnionAllImplicits {
  implicit def hasHas[L <: Has[_], R <: Has[_]]: CombineEnvIntersection[L, R] =
    new CombineEnvIntersection[L, R] {

      def projectLeft(out: Out): L = out

      def projectRight(out: Out): R = out

      def combine(l: L, r: R): Out = l.unionAll[R](r)
    }
}
private[zio] trait LowPriorityUnionAllImplicits extends LowerPriorityUnionAllImplicits {
  implicit def hasAny[L <: Has[_]]: CombineEnvIntersection[L, Any] =
    new CombineEnvIntersection[L, Any] {
      def projectLeft(out: Out): L = out

      def projectRight(out: Out): Any = ()

      def combine(l: L, r: Any): Out = l
    }

  implicit def anyHas[R <: Has[_]]: CombineEnvIntersection[Any, R] =
    new CombineEnvIntersection[Any, R] {
      def projectLeft(out: Out): Any = ()

      def projectRight(out: Out): R = out

      def combine(l: Any, r: R): Out = r
    }

  implicit val anyAny: CombineEnvIntersection[Any, Any] =
    new CombineEnvIntersection[Any, Any] {
      def projectLeft(out: Out): Any = ()

      def projectRight(out: Out): Any = ()

      def combine(r: Any, l: Any): Out = ()
    }
}
private[zio] trait LowerPriorityUnionAllImplicits extends EvenLowerPriorityUnionAllImplicits {
  implicit def hasTag[HasR1 <: Has[_], R: Tag]: CombineEnv.WithOut[HasR1, R, HasR1 with Has[R]] =
    new CombineEnv[HasR1, R] {
      type Out = HasR1 with Has[R]

      def projectLeft(out: Out): HasR1 = out

      def projectRight(out: Out): R = out.get[R]

      def combine(hasR1: HasR1, r: R): Out = hasR1.union[Has[R]](Has(r))
    }

  implicit def tagHasTag[L: Tag, HasR2 <: Has[_]: Tag]: CombineEnv.WithOut[L, HasR2, Has[L] with HasR2] =
    new CombineEnv[L, HasR2] {
      type Out = Has[L] with HasR2

      def projectLeft(out: Out): L = out.get[L]

      def projectRight(out: Out): HasR2 = out

      def combine(l: L, hasR2: HasR2): Out = Has(l).union[HasR2](hasR2)
    }
}
private[zio] trait EvenLowerPriorityUnionAllImplicits extends LowestPriorityUnionAllImplicits {
  implicit def tagHas[L: Tag, HasR2 <: Has[_]]: CombineEnv.WithOut[L, HasR2, Has[L] with HasR2] =
    new CombineEnv[L, HasR2] {
      type Out = Has[L] with HasR2

      def projectLeft(out: Out): L = out.get[L]

      def projectRight(out: Out): HasR2 = out

      def combine(l: L, hasR2: HasR2): Out = Has(l).unionAll[HasR2](hasR2)
    }
}
private[zio] trait LowestPriorityUnionAllImplicits {
  implicit def tagTag[L: Tag, R: Tag]: CombineEnv.WithOut[L, R, Has[L] with Has[R]] =
    new CombineEnv[L, R] {
      type Out = Has[L] with Has[R]

      def projectLeft(out: Out): L = out.get[L]

      def projectRight(out: Out): R = out.get[R]

      def combine(l: L, r: R): Out = Has(l) ++ Has(r)
    }
}

/**
 * A refinement on `CombineEnv` in which the output type is the intersection
 *  of left and right environments.
 */
sealed trait CombineEnvIntersection[L, R] extends CombineEnv[L, R] {
  final type Out = L with R
}
