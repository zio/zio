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
import zio.{ Has, Ref, RefM, UIO, ZIO }

/**
 * A `State[R]` represents the state of a mock.
 */
private[mock] final case class State[R <: Has[_]](
  expectationRef: RefM[Expectation[R]],
  callsCountRef: Ref[Int],
  failedMatchesRef: Ref[List[InvalidCall]]
)

private[mock] object State {

  def make[R <: Has[_]](trunk: Expectation[R]): UIO[State[R]] =
    for {
      expectationRef   <- RefM.make[Expectation[R]](trunk)
      callsCountRef    <- Ref.make[Int](0)
      failedMatchesRef <- Ref.make[List[InvalidCall]](List.empty)
    } yield State[R](expectationRef, callsCountRef, failedMatchesRef)

  def checkUnmetExpectations[R <: Has[_]](state: State[R]) =
    state.expectationRef.get
      .filterOrElse[Any, Nothing, Any](_.satisfied) { expectation =>
        ZIO.die(MockException.UnsatisfiedExpectationsException(expectation))
      }
}
