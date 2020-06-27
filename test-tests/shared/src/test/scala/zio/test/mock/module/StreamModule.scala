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

package zio.test.mock.module

import izumi.reflect.Tag

import zio.stream.{ Sink, Stream }
import zio.test.mock.module.PureModule.NotAnyKind
import zio.{ URIO, ZIO }

/**
 * Example of ZIO Data Types module used for testing ZIO Mock framework.
 */
object StreamModule {

  trait Service {
    def sink(a: Int): Sink[String, Int, Nothing, List[Int]]
    def stream(a: Int): Stream[String, Int]

    def polyInputStream[I: Tag](v: I): Stream[String, String]
    def polyErrorStream[E: Tag](v: String): Stream[E, String]
    def polyOutputStream[A: Tag](v: String): Stream[String, A]
    def polyInputErrorStream[I: Tag, E: Tag](v: I): Stream[E, String]
    def polyInputOutputStream[I: Tag, A: Tag](v: I): Stream[String, A]
    def polyErrorOutputStream[E: Tag, A: Tag](v: String): Stream[E, A]
    def polyInputErrorOutputStream[I: Tag, E: Tag, A: Tag](v: I): Stream[E, A]
    def polyMixedStream[A: Tag]: Stream[String, (A, String)]
    def polyBoundedStream[A <: AnyVal: Tag]: Stream[String, A]
  }

  def sink(a: Int): URIO[StreamModule, Sink[String, Int, Nothing, List[Int]]] = ZIO.access[StreamModule](_.get.sink(a))
  def stream(a: Int): URIO[StreamModule, Stream[String, Int]]                 = ZIO.access[StreamModule](_.get.stream(a))

  def polyInputStream[I: NotAnyKind: Tag](v: I): URIO[StreamModule, Stream[String, String]] =
    ZIO.access[StreamModule](_.get.polyInputStream[I](v))
  def polyErrorStream[E: NotAnyKind: Tag](v: String): URIO[StreamModule, Stream[E, String]] =
    ZIO.access[StreamModule](_.get.polyErrorStream[E](v))
  def polyOutputStream[A: NotAnyKind: Tag](v: String): URIO[StreamModule, Stream[String, A]] =
    ZIO.access[StreamModule](_.get.polyOutputStream[A](v))
  def polyInputErrorStream[I: NotAnyKind: Tag, E: NotAnyKind: Tag](v: I): URIO[StreamModule, Stream[E, String]] =
    ZIO.access[StreamModule](_.get.polyInputErrorStream[I, E](v))
  def polyInputOutputStream[I: NotAnyKind: Tag, A: NotAnyKind: Tag](v: I): URIO[StreamModule, Stream[String, A]] =
    ZIO.access[StreamModule](_.get.polyInputOutputStream[I, A](v))
  def polyErrorOutputStream[E: NotAnyKind: Tag, A: NotAnyKind: Tag](v: String): URIO[StreamModule, Stream[E, A]] =
    ZIO.access[StreamModule](_.get.polyErrorOutputStream[E, A](v))
  def polyInputErrorOutputStream[I: NotAnyKind: Tag, E: NotAnyKind: Tag, A: NotAnyKind: Tag](
    v: I
  ): URIO[StreamModule, Stream[E, A]] =
    ZIO.access[StreamModule](_.get.polyInputErrorOutputStream[I, E, A](v))
  def polyMixedStream[A: NotAnyKind: Tag]: URIO[StreamModule, Stream[String, (A, String)]] =
    ZIO.access[StreamModule](_.get.polyMixedStream[A])
  def polyBoundedStream[A <: AnyVal: NotAnyKind: Tag]: URIO[StreamModule, Stream[String, A]] =
    ZIO.access[StreamModule](_.get.polyBoundedStream[A])
}
