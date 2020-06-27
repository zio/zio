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

import com.github.ghik.silencer.silent
import izumi.reflect.Tag

import zio.stream.{ ZSink, ZStream }
import zio.test.mock.{ Mock, Proxy }
import zio.{ Has, UIO, URLayer, ZLayer }

/**
 * Example module used for testing ZIO Mock framework.
 */
object StreamModuleMock extends Mock[StreamModule] {

  object Sink   extends Sink[Any, String, Int, Nothing, List[Int]]
  object Stream extends Stream[Any, String, Int]

  object PolyInputStream            extends Poly.Stream.Input[String, String]
  object PolyErrorStream            extends Poly.Stream.Error[String, String]
  object PolyOutputStream           extends Poly.Stream.Output[String, String]
  object PolyInputErrorStream       extends Poly.Stream.InputError[String]
  object PolyInputOutputStream      extends Poly.Stream.InputOutput[String]
  object PolyErrorOutputStream      extends Poly.Stream.ErrorOutput[String]
  object PolyInputErrorOutputStream extends Poly.Stream.InputErrorOutput
  object PolyMixedStream            extends Poly.Stream.Output[Unit, String]
  object PolyBoundedStream          extends Poly.Stream.Output[Unit, String]

  @silent("is never used")
  val compose: URLayer[Has[Proxy], StreamModule] =
    ZLayer.fromServiceM { proxy =>
      withRuntime.map { rts =>
        new StreamModule.Service {
          def sink(a: Int): ZSink[Any, String, Int, Nothing, List[Int]] =
            rts.unsafeRun(proxy(Sink, a).catchAll(error => UIO(ZSink.fail[String, Int](error).dropLeftover)))
          def stream(a: Int): ZStream[Any, String, Int] = rts.unsafeRun(proxy(Stream, a))

          def polyInputStream[I: Tag](v: I): ZStream[Any, String, String] =
            rts.unsafeRun(proxy(PolyInputStream.of[I], v))
          def polyErrorStream[E: Tag](v: String): ZStream[Any, E, String] =
            rts.unsafeRun(proxy(PolyErrorStream.of[E], v))
          def polyOutputStream[A: Tag](v: String): ZStream[Any, String, A] =
            rts.unsafeRun(proxy(PolyOutputStream.of[A], v))
          def polyInputErrorStream[I: Tag, E: Tag](v: I): ZStream[Any, E, String] =
            rts.unsafeRun(proxy(PolyInputErrorStream.of[I, E], v))
          def polyInputOutputStream[I: Tag, A: Tag](v: I): ZStream[Any, String, A] =
            rts.unsafeRun(proxy(PolyInputOutputStream.of[I, A], v))
          def polyErrorOutputStream[E: Tag, A: Tag](v: String): ZStream[Any, E, A] =
            rts.unsafeRun(proxy(PolyErrorOutputStream.of[E, A], v))
          def polyInputErrorOutputStream[I: Tag, E: Tag, A: Tag](v: I): ZStream[Any, E, A] =
            rts.unsafeRun(proxy(PolyInputErrorOutputStream.of[I, E, A], v))
          def polyMixedStream[A: Tag]: ZStream[Any, String, (A, String)] =
            rts.unsafeRun(proxy(PolyMixedStream.of[(A, String)]))
          def polyBoundedStream[A <: AnyVal: Tag]: ZStream[Any, String, A] =
            rts.unsafeRun(proxy(PolyBoundedStream.of[A]))
        }
      }
    }
}
