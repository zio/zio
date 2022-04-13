/*
 * Copyright 2017-2022 John A. De Goes and the ZIO Contributors
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

import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.implicitNotFound

/**
 * A simple, macro-less means of creating accessors from Services. Extend the
 * companion object with `Accessible[ServiceName]`, then simply call
 * `Companion(_.someMethod)`, to return a ZIO effect that requires the Service
 * in its environment.
 *
 * Example:
 * {{{
 *   trait FooService {
 *     def magicNumber: UIO[Int]
 *     def castSpell(chant: String): UIO[Boolean]
 *   }
 *
 *   object FooService extends Accessible[FooService]
 *
 *   val example: ZIO[FooService, Nothing, Unit] =
 *     for {
 *       int  <- FooService(_.magicNumber)
 *       bool <- FooService(_.castSpell("Oogabooga!"))
 *     } yield ()
 * }}}
 */
trait Accessible[R] {
  def apply[R1 <: R, E, A](
    f: R => ZIO[R1, E, A]
  )(implicit tag: Tag[R], trace: Trace): ZIO[R1, E, A] =
    ZIO.serviceWithZIO[R](f)
}
