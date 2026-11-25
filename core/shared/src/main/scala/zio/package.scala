/*
 * Copyright 2017-2024 John A. De Goes and the ZIO Contributors
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

import zio.TagVersionSpecific
import zio.internal.stacktracer.Tracer
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.reflect.ClassTag

package object zio
    extends BuildFromCompat
    with EitherCompat
    with IntersectionTypeCompat
    with VersionSpecific
    with DurationModule {

  /**
   * The runtime flags of a fiber, such as interruption and op logging, encoded
   * as the bits of an `Int`. The `RuntimeFlags` object reads and changes them.
   */
  type RuntimeFlags = Int

  /** An abstract type whose lower bound is `Any`, so `Any` conforms to it. */
  type ZAny >: Any

  /**
   * An abstract type whose upper bound is `Nothing`, so it conforms to every
   * type.
   */
  type ZNothing <: Nothing

  /**
   * An effect that needs no environment, may fail with `E`, and succeeds with
   * `A`.
   */
  type IO[+E, +A] = ZIO[Any, E, A] // Succeed with an `A`, may fail with `E`        , no requirements.

  /**
   * An effect that needs no environment, may fail with a `Throwable`, and
   * succeeds with `A`.
   */
  type Task[+A] = ZIO[Any, Throwable, A] // Succeed with an `A`, may fail with `Throwable`, no requirements.

  /**
   * An effect that needs `R`, may fail with a `Throwable`, and succeeds with
   * `A`.
   */
  type RIO[-R, +A] = ZIO[R, Throwable, A] // Succeed with an `A`, may fail with `Throwable`, requires an `R`.

  /**
   * An effect that needs no environment, cannot fail, and succeeds with `A`.
   */
  type UIO[+A] = ZIO[Any, Nothing, A] // Succeed with an `A`, cannot fail              , no requirements.

  /** An effect that needs `R`, cannot fail, and succeeds with `A`. */
  type URIO[-R, +A] = ZIO[R, Nothing, A] // Succeed with an `A`, cannot fail              , requires an `R`.

  /**
   * A layer that needs `RIn`, may fail with a `Throwable`, and builds `ROut`.
   */
  type RLayer[-RIn, +ROut] = ZLayer[RIn, Throwable, ROut]

  /** A layer that needs `RIn`, cannot fail, and builds `ROut`. */
  type URLayer[-RIn, +ROut] = ZLayer[RIn, Nothing, ROut]

  /** A layer that needs no input, may fail with `E`, and builds `ROut`. */
  type Layer[+E, +ROut] = ZLayer[Any, E, ROut]

  /** A layer that needs no input, cannot fail, and builds `ROut`. */
  type ULayer[+ROut] = ZLayer[Any, Nothing, ROut]

  /**
   * A layer that needs no input, may fail with a `Throwable`, and builds
   * `ROut`.
   */
  type TaskLayer[+ROut] = ZLayer[Any, Throwable, ROut]

  /**
   * A source location (`location(file:line)`) that ZIO records in execution
   * traces. Methods take it as an implicit parameter, and a macro creates one
   * at each call site.
   */
  type Trace = Tracer.instance.Type with Tracer.Traced

  /**
   * A type tag for `A`: the runtime description of the type that ZIO uses to
   * store and look up services in a `ZEnvironment`.
   */
  sealed trait Tag[A] extends EnvironmentTag[A] {

    /** Returns the `LightTypeTag` that describes `A`. */
    def tag: LightTypeTag
  }

  /** Summons and derives [[Tag]] instances. */
  object Tag extends TagVersionSpecific {

    /** Returns a `Tag[A]` built from the implicit `EnvironmentTag[A]`. */
    def apply[A](implicit tag0: EnvironmentTag[A]): Tag[A] =
      new Tag[A] {
        def tag: zio.LightTypeTag           = tag0.tag
        override def closestClass: Class[_] = tag0.closestClass
      }
  }

  private[zio] type Callback[E, A] = (Exit[E, A], FiberRefs) => Any

  private[zio] def nonFatal(t: Throwable): Boolean = !t.isInstanceOf[VirtualMachineError]
}
