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

package zio.test.mock.internal

import zio.test.mock.Expectation
import zio.{Has, Ref, UIO, ZIO}

/**
 * A `MockState[R]` represents the state of a mock.
 */
private[mock] final case class MockState[R <: Has[_]](
  expectationRef: Ref[Expectation[R]],
  callsCountRef: Ref[Int]
)

private[mock] object MockState {

  def make[R <: Has[_]](trunk: Expectation[R]): UIO[MockState[R]] =
    for {
      expectationRef <- Ref.make[Expectation[R]](trunk)
      callsCountRef  <- Ref.make[Int](0)
    } yield MockState[R](expectationRef, callsCountRef)

  def checkUnmetExpectations[R <: Has[_]](state: MockState[R]): ZIO[Any, Nothing, Any] =
    state.expectationRef.get
      .filterOrElse[Any, Nothing, Any](_.state >= ExpectationState.Satisfied) { expectation =>
        ZIO.die(MockException.UnsatisfiedExpectationsException(expectation))
      }
}
