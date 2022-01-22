/*
 * Copyright 2019-2022 John A. De Goes and the ZIO Contributors
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

package zio.mock

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.{ZSink, ZStream}
import zio.test.TestPlatform
import zio.{Executor, Runtime, EnvironmentTag, ULayer, URIO, URLayer, ZIO, ZTraceElement}

/**
 * A `Mock[R]` represents a mockable environment `R`.
 */
abstract class Mock[R: EnvironmentTag] { self =>

  protected[mock] val compose: URLayer[Proxy, R]

  def empty(implicit trace: ZTraceElement): ULayer[R] = Expectation.NoCalls(self)

  /**
   * Replaces Runtime on JS platform to one with unyielding executor.
   */
  protected def withRuntime[R](implicit trace: ZTraceElement): URIO[R, Runtime[R]] =
    ZIO.runtime[R].map { runtime =>
      if (!TestPlatform.isJS) runtime
      else
        runtime.withExecutor {
          val ec = runtime.runtimeConfig.executor.asExecutionContext
          Executor.fromExecutionContext(Int.MaxValue)(ec)
        }
    }

  abstract class Effect[I: EnvironmentTag, E: EnvironmentTag, A: EnvironmentTag] extends Capability[R, I, E, A](self)
  abstract class Method[I: EnvironmentTag, E <: Throwable: EnvironmentTag, A: EnvironmentTag]
      extends Capability[R, I, E, A](self)
  abstract class Sink[I: EnvironmentTag, E: EnvironmentTag, A: EnvironmentTag, L: EnvironmentTag, B: EnvironmentTag]
      extends Capability[R, I, E, ZSink[Any, E, A, L, B]](self)
  abstract class Stream[I: EnvironmentTag, E: EnvironmentTag, A: EnvironmentTag]
      extends Capability[R, I, Nothing, ZStream[Any, E, A]](self)

  object Poly {

    object Effect {
      abstract class Input[E: EnvironmentTag, A: EnvironmentTag]  extends Capability.Poly.Input[R, E, A](self)
      abstract class Error[I: EnvironmentTag, A: EnvironmentTag]  extends Capability.Poly.Error[R, I, A, Any](self)
      abstract class Output[I: EnvironmentTag, E: EnvironmentTag] extends Capability.Poly.Output[R, I, E, Any](self)
      abstract class InputError[A: EnvironmentTag]                extends Capability.Poly.InputError[R, A, Any](self)
      abstract class InputOutput[E: EnvironmentTag]               extends Capability.Poly.InputOutput[R, E, Any](self)
      abstract class ErrorOutput[I: EnvironmentTag]               extends Capability.Poly.ErrorOutput[R, I, Any, Any](self)
      abstract class InputErrorOutput                             extends Capability.Poly.InputErrorOutput[R, Any, Any](self)
    }

    object Method {
      abstract class Input[E <: Throwable: EnvironmentTag, A: EnvironmentTag]
          extends Capability.Poly.Input[R, E, A](self)
      abstract class Error[I: EnvironmentTag, A: EnvironmentTag] extends Capability.Poly.Error[R, I, A, Throwable](self)
      abstract class Output[I: EnvironmentTag, E <: Throwable: EnvironmentTag]
          extends Capability.Poly.Output[R, I, E, Any](self)
      abstract class InputError[A: EnvironmentTag]               extends Capability.Poly.InputError[R, A, Throwable](self)
      abstract class InputOutput[E <: Throwable: EnvironmentTag] extends Capability.Poly.InputOutput[R, E, Any](self)
      abstract class ErrorOutput[I: EnvironmentTag]              extends Capability.Poly.ErrorOutput[R, I, Throwable, Any](self)
      abstract class InputErrorOutput                            extends Capability.Poly.InputErrorOutput[R, Throwable, Any](self)
    }
  }
}

object Mock {

  private[mock] case class Composed[R: EnvironmentTag](compose: URLayer[Proxy, R]) extends Mock[R]
}
