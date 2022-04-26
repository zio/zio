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

import zio.{ZIO, ZTraceElement}
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.BoolAlgebraZIO._

final case class BoolAlgebraZIO[-R, +E, +A](run: ZIO[R, E, BoolAlgebra[A]]) { self =>

  def &&[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraZIO[R1, E1, A1])(implicit
    trace: ZTraceElement
  ): BoolAlgebraZIO[R1, E1, A1] =
    BoolAlgebraZIO(run.zipWith(that.run)(_ && _))

  def ||[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraZIO[R1, E1, A1])(implicit
    trace: ZTraceElement
  ): BoolAlgebraZIO[R1, E1, A1] =
    BoolAlgebraZIO(run.zipWith(that.run)(_ || _))

  def ==>[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraZIO[R1, E1, A1])(implicit
    trace: ZTraceElement
  ): BoolAlgebraZIO[R1, E1, A1] =
    BoolAlgebraZIO(run.zipWith(that.run)(_ ==> _))

  def <==>[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraZIO[R1, E1, A1])(implicit
    trace: ZTraceElement
  ): BoolAlgebraZIO[R1, E1, A1] =
    BoolAlgebraZIO(run.zipWith(that.run)(_ <==> _))

  def unary_!(implicit trace: ZTraceElement): BoolAlgebraZIO[R, E, A] =
    BoolAlgebraZIO(run.map(!_))

  def as[B](b: B)(implicit trace: ZTraceElement): BoolAlgebraZIO[R, E, B] =
    map(_ => b)

  def flatMap[R1 <: R, E1 >: E, B](f: A => BoolAlgebraZIO[R1, E1, B])(implicit
    trace: ZTraceElement
  ): BoolAlgebraZIO[R1, E1, B] =
    BoolAlgebraZIO(run.flatMap(_.flatMapZIO(f(_).run)))

  def implies[R1 <: R, E1 >: E, A1 >: A](that: BoolAlgebraZIO[R1, E1, A1])(implicit
    trace: ZTraceElement
  ): BoolAlgebraZIO[R1, E1, A1] =
    BoolAlgebraZIO(run.zipWith(that.run)(_ implies _))

  def isSuccess(implicit trace: ZTraceElement): ZIO[R, E, Boolean] =
    run.map(_.isSuccess)

  def map[B](f: A => B)(implicit trace: ZTraceElement): BoolAlgebraZIO[R, E, B] =
    flatMap(f andThen success)
}

object BoolAlgebraZIO {

  def failure[A](a: A)(implicit trace: ZTraceElement): BoolAlgebraZIO[Any, Nothing, A] =
    BoolAlgebraZIO(ZIO.succeedNow(BoolAlgebra.failure(a)))

  def fromZIO[R, E, A](effect: ZIO[R, E, A])(implicit trace: ZTraceElement): BoolAlgebraZIO[R, E, A] =
    BoolAlgebraZIO(effect.map(BoolAlgebra.success))

  def success[A](a: A)(implicit trace: ZTraceElement): BoolAlgebraZIO[Any, Nothing, A] =
    BoolAlgebraZIO(ZIO.succeedNow(BoolAlgebra.success(a)))
}
