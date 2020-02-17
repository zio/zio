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

import zio.{ Has, Ref, Tagged, UIO, ZIO, ZLayer }

/**
 * `Spyable[A]` models the capability to spy on a service of type `A`.
 * Implementations must define both a method `environment` to genereate a live
 * environment given a mock and a method `mock` to generate a mock given a live
 * environment. Given these definitions, it is possible to convert a service to
 * a mock, see and modify state based on method calls, inputs, and outputs, and
 * then convert the mock back to a live environment to spy on the service.
 */
trait Spyable[R <: Has[_]] extends Mockable[R] { self =>

  /**
   * Constructs a mock service from a live environment.
   */
  def mock(environment: R): Mock

  /**
   * Updates a live environment by spying on it using the specified partial
   * function, which has the ability to see method calls, inputs, and outputs
   * and update state based on them.
   */
  final def spy[F <: Invocation[R, _, _]](environment: R)(f: PartialFunction[F, UIO[Unit]]): R = {
    val mocked: Mock = mock(environment)
    val spied: Mock = new Mock {
      def invoke[RIn <: Has[_], ROut, Input, Error, Value](
        method: Method[RIn, Input, Value],
        input: Input
      ): ZIO[ROut, Error, Value] =
        mocked
          .invoke(method, input)
          .tap { output =>
            f.applyOrElse(
              Invocation(method, input, output).asInstanceOf[F],
              (_: Any) => UIO.unit
            )
          }
    }
    self.environment(spied)
  }
}

object Spyable {

  /**
   * Updates a live environment by spying on it. The spy has the ability to see
   * all method calls, inputs, and outputs and updates the returned `Ref` based
   * on them.
   */
  def spyWithRef[R, E, A <: Has[_]: Tagged](
    layer: ZLayer[R, E, A]
  )(implicit spyable: Spyable[A]): UIO[(Ref[Vector[Invocation[A, _, _]]], ZLayer[R, E, A])] =
    Ref.make(Vector.empty[Invocation[A, _, _]]).map { ref =>
      val spy = ZLayer.requires[A].map { environment =>
        spyable.spy[Invocation[A, _, _]](environment) {
          case invocation => ref.update(_ :+ invocation)
        }
      }
      (ref, layer >>> spy)
    }
}
