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

import zio.duration._
import zio.test.environment.Live
import zio.{ Has, Layer, Tagged, ZIO }

package object mock {

  type Module = Has[Module.Service]

  type T22[A] = (A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A, A)

  lazy val intTuple22: T22[Int] =
    (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22)

  private[mock] def testSpec[E, A](name: String)(
    mock: Layer[Nothing, Module],
    app: ZIO[Module, E, A],
    check: Assertion[A]
  ) = testM(name) {
    val result = mock.build.use[Any, E, A](app.provide _)
    assertM(result)(check)
  }

  private[mock] def testSpecTimeboxed[E, A](name: String)(duration: Duration)(
    mock: Layer[Nothing, Module],
    app: ZIO[Module, E, A],
    check: Assertion[Option[A]]
  ) = testM(name) {
    val result =
      Live.live {
        mock.build
          .use(app.provide _)
          .timeout(duration)
      }

    assertM(result)(check)
  }

  private[mock] def testSpecDied[E, A](name: String)(
    mock: Layer[Nothing, Module],
    app: ZIO[Module, E, A],
    check: Assertion[Throwable]
  ) = testM(name) {
    val result =
      mock.build
        .use(app.provide _)
        .orElse(ZIO.unit)
        .absorb
        .flip

    assertM(result)(check)
  }

  private[mock] def testSpecComposed[R <: Has[_]: Tagged, E, A](name: String)(
    mock: Layer[Nothing, R],
    app: ZIO[R, E, A],
    check: Assertion[A]
  ) = testM(name) {
    val result = mock.build.use[R, E, A](app.provide _)
    assertM(result)(check)
  }
}
