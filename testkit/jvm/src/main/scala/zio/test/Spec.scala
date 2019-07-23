/*
 * Copyright 2019 John A. De Goes and the ZIO Contributors
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

import zio.{ Managed, ZIO }
import zio.duration.Duration

/**
 * A `Spec[R, E, L]` is the backbone of _ZIO Test_. Specs require an environment
 * of type `R` (which could be `Any`), may fail with errors of type `E`, and 
 * are annotated with labels of type `L` (typically `String`).
 */
sealed trait Spec[-R, +E, +L] {

  /**
   * Returns a pruned Spec that contains only the specs whose labels match the
   * specified predicate.
   */
  final def filter(f: L => Boolean): Spec[R, E, L] = ???

  /**
   * Returns a new spec with a remapped error type.
   */
  final def mapError[E1](f: E => E1): Spec[R, E1, L] = ???

  /**
   * Returns a new spec with a remapped label type.
   */
  final def mapLabel[L1](f: L => L1): Spec[R, E, L1] = ???

  /**
   * Returns a new spec, where every test in this one is marked as pending.
   */
  final def pending: Spec[R, E, L] = ???

  /**
   * Provides a spec with the value it requires, eliminating its requirement.
   */
  final def provide(r: R): Spec[Any, E, L] = ???

  /**
   * Provides each test with its own managed resource, eliminating their requirements.
   */
  final def provideEach[E1 >: E](managed: Managed[E1, R]): Spec[Any, E1, L] = ???

  /**
   * Provides a spec with part of the value it requires, eliminating its requirement.
   */
  final def provideSome[R1](r: R1 => R): Spec[R1, E, L] = ???

  /**
   * Returns the size of the spec, which is the number of tests that it contains.
   */
  final def size: Int = ???

  /**
   * Returns a new spec that times out each test by the specified duration.
   * This is merely implemented for convenience atop [[weave]].
   */
  final def timeout(duration: Duration): Spec[R, E, L] = ???

  /**
   * Weaves an aspect into this spec by replacing every result with its
   * application using the specified function.
   */
  final def weaveAll[R1 <: R, E1 >: E](f: ZIO[R, E, AssertResult] => ZIO[R1, E1, AssertResult]): Spec[R1, E1, L] = ???

  /**
   * Weaves an aspect into this spec by replacing every result matching the
   * predicate with its application using the specified function.
   */
  final def weaveSome[R1 <: R, E1 >: E](pred: L => Boolean)(
    f: ZIO[R, E, AssertResult] => ZIO[R1, E1, AssertResult]
  ): Spec[R1, E1, L] = ???
}
object Spec {
  final case class Suite[-R, +E, +L](label: L, specs: Vector[Spec[R, E, L]])      extends Spec[R, E, L]
  final case class Test[-R, +E, +L](label: L, assertion: ZIO[R, E, AssertResult]) extends Spec[R, E, L]
}
