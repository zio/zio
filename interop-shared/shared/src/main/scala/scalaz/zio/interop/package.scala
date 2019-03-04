/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

package scalaz.zio

package object interop {

  type ParIO[-R, +E, +A] = Par.T[R, E, A]

  implicit final class AutoCloseableOps(private val a: AutoCloseable) extends AnyVal {

    /**
     * Returns an `IO` action which closes this `AutoCloseable` resource.
     */
    def closeIO(): UIO[Unit] = ZIO.effectTotal(a.close())
  }

  implicit final class IOAutocloseableOps[R, E, A <: AutoCloseable](private val io: ZIO[R, E, A]) extends AnyVal {

    /**
     * Like `bracket`, safely wraps a use and release of a resource.
     * This resource will get automatically closed, because it implements `AutoCloseable`.
     */
    def bracketAuto[R1 <: R, E1 >: E, B](use: A => ZIO[R1, E1, B]): ZIO[R1, E1, B] =
      io.bracket[R1, E1, B](_.closeIO())(use)
  }
}
