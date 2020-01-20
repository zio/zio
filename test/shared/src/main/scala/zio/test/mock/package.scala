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

package zio.test

import zio.{ Has, Ref, Tagged, UIO, ZLayer }

package object mock {
  type Spy = Has[Spy.Service]

  /**
   * A `Spy` wraps a live service, maintaining an internal state that is
   * updated based on calls to that service and allowing assertions to be made
   * based on that internal state and values returned by the live service.
   */
  object Spy {

    trait Service {
      def spy: UIO[TestResult]
    }

    /**
     * Constructs a spy that wraps an existing live service. The spy maintains
     * some internal state `S` with the specified initial value. The update
     * function `f` takes an existing live service and returns an updated
     * service that has access to the live service as well as a `Ref` with the
     * internal spy state and a `Ref` to hold the result of assertions made
     * about the internal state and values returned.
     */
    def live[S, A: Tagged](z: S)(
      f: PartialFunction[(Ref[S], Ref[TestResult], Invocation[A, _, _]), UIO[Unit]]
    )(implicit spyable: Spyable[A]): ZLayer[Has[A], Nothing, Has[A] with Spy] =
      ZLayer.fromServiceM { a =>
        Ref.make(z).flatMap { s =>
          Ref.make(assertCompletes).map { result =>
            val service = spyable.spy(Has(a)) { case invocation => f((s, result, invocation)) }.get
            val spy     = new Spy.Service { val spy = result.get }
            Has.allOf[A, Spy.Service](service, spy)
          }
        }
      }
  }
}
