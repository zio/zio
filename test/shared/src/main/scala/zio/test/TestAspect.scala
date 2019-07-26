/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

import zio.{ ZIO, ZSchedule }
import zio.duration.Duration
import zio.clock.Clock

/**
 * A `TestAspect` is an aspect that can be weaved into specs. You can think of
 * an aspect as a polymorphic function, capable of transforming one test into
 * another, possibly enlarging the environment or error type.
 */
trait TestAspect[+LowerR, -UpperR, +LowerE, -UpperE] { self =>
  def apply[R >: LowerR <: UpperR, E >: LowerE <: UpperE](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult]

  /**
   * Returns a new aspect that represents the sequential composition of this
   * aspect with the specified one.
   */
  final def >>>[LowerR1 >: LowerR, UpperR1 <: UpperR, LowerE1 >: LowerE, UpperE1 <: UpperE](
    that: TestAspect[LowerR1, UpperR1, LowerE1, UpperE1]
  ): TestAspect[LowerR1, UpperR1, LowerE1, UpperE1] =
    new TestAspect[LowerR1, UpperR1, LowerE1, UpperE1] {
      def apply[R >: LowerR1 <: UpperR1, E >: LowerE1 <: UpperE1](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult] =
        that(self(test))
    }

  final def andThen[LowerR1 >: LowerR, UpperR1 <: UpperR, LowerE1 >: LowerE, UpperE1 <: UpperE](
    that: TestAspect[LowerR1, UpperR1, LowerE1, UpperE1]
  ): TestAspect[LowerR1, UpperR1, LowerE1, UpperE1] = self >>> that
}
object TestAspect {

  /**
   * Constucts a simple monomorphic aspect that only works with the specified
   * environment and error type.
   */
  def aspect[R0, E0](f: ZIO[R0, E0, TestResult] => ZIO[R0, E0, TestResult]): TestAspect[R0, R0, E0, E0] =
    new TestAspect[R0, R0, E0, E0] {
      def apply[R >: R0 <: R0, E >: E0 <: E0](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult] = f(test)
    }

  /**
   * An aspect that retries a test until success, without limit.
   */
  val eventually: TestAspectPoly =
    new TestAspectPoly {
      def apply[R >: Nothing <: Any, E >: Nothing <: Any](test: ZIO[R, E, TestResult]): ZIO[R, Nothing, TestResult] = {
        lazy val untilSuccess: ZIO[R, Nothing, TestResult] =
          test.foldM(_ => untilSuccess, ZIO.succeed(_))

        untilSuccess
      }
    }

  /**
   * An aspect that marks tests as pending.
   */
  val pending: TestAspectPoly =
    new TestAspectPoly {
      def apply[R >: Nothing <: Any, E >: Nothing <: Any](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult] =
        ZIO.succeed(AssertResult.Pending)
    }

  /**
   * An aspect that retries failed tests according to a schedule.
   */
  def retry[R0, E0](schedule: ZSchedule[R0, E0, Any]): TestAspect[Nothing, R0 with Clock, Nothing, E0] =
    new TestAspect[Nothing, R0 with Clock, Nothing, E0] {
      def apply[R >: Nothing <: R0 with Clock, E >: Nothing <: E0](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult] =
        test.retry(schedule: ZSchedule[R0, E0, Any])
    }

  /**
   * An aspect that times out tests using the specified duration.
   */
  def timeout(duration: Duration): TestAspect[Nothing, Clock, Nothing, Any] =
    new TestAspect[Nothing, Clock, Nothing, Any] {
      def apply[R >: Nothing <: Clock, E >: Nothing <: Any](test: ZIO[R, E, TestResult]): ZIO[R, E, TestResult] =
        test.timeout(duration).map {
          case None    => AssertResult.failure(FailureDetails.Other(s"Timeout of ${duration} exceeded"))
          case Some(v) => v
        }
    }
}
