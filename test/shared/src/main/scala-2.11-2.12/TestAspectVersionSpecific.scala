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

package zio.test

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.unchecked.uncheckedVariance

trait TestAspectVersionSpecific[+LowerEnv, -UpperEnv, +LowerErr, -UpperErr] {
  self: TestAspect[LowerEnv, UpperEnv, LowerErr, UpperErr] =>

  /**
   * Composes two test aspects into one test aspect, by first applying the
   * transformation of the specified test aspect, and then applying the
   * transformation of this test aspect.
   */
  def <<<[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2](that: TestAspect[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2])(
    implicit
    composeEnv: ZCompose[LowerEnv2, UpperEnv2, that.OutEnv, LowerEnv, UpperEnv, OutEnv] @uncheckedVariance,
    composeErr: ZCompose[LowerErr2, UpperErr2, that.OutErr, LowerErr, UpperErr, OutErr] @uncheckedVariance
  ): TestAspect.WithOut[
    composeEnv.Lower,
    composeEnv.Upper,
    composeErr.Lower,
    composeErr.Upper,
    composeEnv.Out,
    composeErr.Out
  ] =
    new TestAspect[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper
    ] {
      type OutEnv[Env] = composeEnv.Out[Env]
      type OutErr[Err] = composeErr.Out[Err]
      def apply[Env >: composeEnv.Lower <: composeEnv.Upper, Err >: composeErr.Lower <: composeErr.Upper](
        spec: Spec[Env, TestFailure[Err], TestSuccess]
      )(implicit trace: ZTraceElement): Spec[OutEnv[Env], TestFailure[OutErr[Err]], TestSuccess] = {
        val right = that.asInstanceOf[TestAspect[Nothing, Any, Nothing, Any]](spec)
        val left  = self.asInstanceOf[TestAspect[Nothing, Any, Nothing, Any]](right)
        left.asInstanceOf[Spec[OutEnv[Env], TestFailure[OutErr[Err]], TestSuccess]]
      }
    }

  /**
   * Composes two test aspects into one test aspect, by first applying the
   * transformation of this test aspect, and then applying the transformation
   * of the specified test aspect.
   */
  def >>>[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2](that: TestAspect[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2])(
    implicit
    composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, that.OutEnv] @uncheckedVariance,
    composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, that.OutErr] @uncheckedVariance
  ): TestAspect.WithOut[
    composeEnv.Lower,
    composeEnv.Upper,
    composeErr.Lower,
    composeErr.Upper,
    composeEnv.Out,
    composeErr.Out
  ] =
    new TestAspect[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper
    ] {
      type OutEnv[Env] = composeEnv.Out[Env]
      type OutErr[Err] = composeErr.Out[Err]
      def apply[Env >: composeEnv.Lower <: composeEnv.Upper, Err >: composeErr.Lower <: composeErr.Upper](
        spec: Spec[Env, TestFailure[Err], TestSuccess]
      )(implicit trace: ZTraceElement): Spec[OutEnv[Env], TestFailure[OutErr[Err]], TestSuccess] = {
        val left  = self.asInstanceOf[TestAspect[Nothing, Any, Nothing, Any]](spec)
        val right = that.asInstanceOf[TestAspect[Nothing, Any, Nothing, Any]](left)
        right.asInstanceOf[Spec[OutEnv[Env], TestFailure[OutErr[Err]], TestSuccess]]
      }
    }

  /**
   * Composes two test aspects into one test aspect, by first applying the
   * transformation of this test aspect, and then applying the transformation
   * of the specified test aspect.
   */
  def @@[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2](that: TestAspect[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2])(
    implicit
    composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, that.OutEnv] @uncheckedVariance,
    composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, that.OutErr] @uncheckedVariance
  ): TestAspect.WithOut[
    composeEnv.Lower,
    composeEnv.Upper,
    composeErr.Lower,
    composeErr.Upper,
    composeEnv.Out,
    composeErr.Out
  ] =
    new TestAspect[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper
    ] {
      type OutEnv[Env] = composeEnv.Out[Env]
      type OutErr[Err] = composeErr.Out[Err]
      def apply[Env >: composeEnv.Lower <: composeEnv.Upper, Err >: composeErr.Lower <: composeErr.Upper](
        spec: Spec[Env, TestFailure[Err], TestSuccess]
      )(implicit trace: ZTraceElement): Spec[OutEnv[Env], TestFailure[OutErr[Err]], TestSuccess] = {
        val left  = self.asInstanceOf[TestAspect[Nothing, Any, Nothing, Any]](spec)
        val right = that.asInstanceOf[TestAspect[Nothing, Any, Nothing, Any]](left)
        right.asInstanceOf[Spec[OutEnv[Env], TestFailure[OutErr[Err]], TestSuccess]]
      }
    }

  /**
   * A named version of the `>>>` operator.
   */
  def andThen[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2](that: TestAspect[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2])(
    implicit
    composeEnv: ZCompose[LowerEnv, UpperEnv, OutEnv, LowerEnv2, UpperEnv2, that.OutEnv] @uncheckedVariance,
    composeErr: ZCompose[LowerErr, UpperErr, OutErr, LowerErr2, UpperErr2, that.OutErr] @uncheckedVariance
  ): TestAspect.WithOut[
    composeEnv.Lower,
    composeEnv.Upper,
    composeErr.Lower,
    composeErr.Upper,
    composeEnv.Out,
    composeErr.Out
  ] =
    new TestAspect[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper
    ] {
      type OutEnv[Env] = composeEnv.Out[Env]
      type OutErr[Err] = composeErr.Out[Err]
      def apply[Env >: composeEnv.Lower <: composeEnv.Upper, Err >: composeErr.Lower <: composeErr.Upper](
        spec: Spec[Env, TestFailure[Err], TestSuccess]
      )(implicit trace: ZTraceElement): Spec[OutEnv[Env], TestFailure[OutErr[Err]], TestSuccess] = {
        val left  = self.asInstanceOf[TestAspect[Nothing, Any, Nothing, Any]](spec)
        val right = that.asInstanceOf[TestAspect[Nothing, Any, Nothing, Any]](left)
        right.asInstanceOf[Spec[OutEnv[Env], TestFailure[OutErr[Err]], TestSuccess]]
      }
    }

  /**
   * A named version of the `<<<` operator.
   */
  def compose[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2](that: TestAspect[LowerEnv2, UpperEnv2, LowerErr2, UpperErr2])(
    implicit
    composeEnv: ZCompose[LowerEnv2, UpperEnv2, that.OutEnv, LowerEnv, UpperEnv, OutEnv] @uncheckedVariance,
    composeErr: ZCompose[LowerErr2, UpperErr2, that.OutErr, LowerErr, UpperErr, OutErr] @uncheckedVariance
  ): TestAspect.WithOut[
    composeEnv.Lower,
    composeEnv.Upper,
    composeErr.Lower,
    composeErr.Upper,
    composeEnv.Out,
    composeErr.Out
  ] =
    new TestAspect[
      composeEnv.Lower,
      composeEnv.Upper,
      composeErr.Lower,
      composeErr.Upper
    ] {
      type OutEnv[Env] = composeEnv.Out[Env]
      type OutErr[Err] = composeErr.Out[Err]
      def apply[Env >: composeEnv.Lower <: composeEnv.Upper, Err >: composeErr.Lower <: composeErr.Upper](
        spec: Spec[Env, TestFailure[Err], TestSuccess]
      )(implicit trace: ZTraceElement): ZSpec[OutEnv[Env], OutErr[Err]] = {
        val right = that.asInstanceOf[TestAspect[Nothing, Any, Nothing, Any]](spec)
        val left  = self.asInstanceOf[TestAspect[Nothing, Any, Nothing, Any]](right)
        left.asInstanceOf[Spec[OutEnv[Env], TestFailure[OutErr[Err]], TestSuccess]]
      }
    }
}
