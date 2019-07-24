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

import zio.{ Managed, ZIO, ZManaged }
import zio.clock.Clock
import zio.duration.Duration

/**
 * A `ZSpec[R, E, L]` is the backbone of _ZIO Test_. ZSpecs require an environment
 * of type `R` (which could be `Any`), may fail with errors of type `E`, and
 * are annotated with labels of type `L` (typically `String`).
 */
sealed trait ZSpec[-R, +E, +L] { self =>

  /**
   * Concatenates this spec onto the specified spec.
   */
  final def ++[R1 <: R, E1 >: E, L1 >: L](that: ZSpec[R1, E1, L1]): ZSpec[R1, E1, L1] =
    ZSpec.Concat(self, Vector(that))

  /**
   * Returns a new spec that decorates every test with the specified transformation function.
   */
  final def around[R1 <: R, E1 >: E](
    managed: ZManaged[R1, E1, TestResult => ZIO[R1, E1, TestResult]]
  ): ZSpec[R1, E1, L] = {
    def loop(spec: ZSpec[R, E, L]): ZSpec[R1, E1, L] = spec match {
      case ZSpec.Suite(label, specs) => ZSpec.Suite(label, specs.map(loop))
      case ZSpec.Test(label, assert) => ZSpec.Test(label, managed.use(f => assert.flatMap(f)))
      case ZSpec.Concat(head, tail)  => ZSpec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Determines if there exists a label satisfying the predicate.
   */
  final def exists(f: L => Boolean): Boolean =
    labels.map(_._2).exists(f)

  /**
   * Returns a pruned ZSpec that contains only the specs whose labels match the
   * specified predicate.
   */
  final def filter(f: L => Boolean): ZSpec[R, E, L] = {
    def loop(spec: ZSpec[R, E, L]): ZSpec[R, E, L] = spec match {
      case ZSpec.Suite(label, specs) => ZSpec.Suite(label, specs.map(loop))
      case ZSpec.Test(label, assert) =>
        if (f(label)) ZSpec.Test(label, assert)
        else ZSpec.Test(label, ZIO.succeed(AssertResult.Pending))
      case ZSpec.Concat(head, tail) => ZSpec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Determines if all labels satisfy the specified predicate.
   */
  final def forall(f: L => Boolean): Boolean =
    labels.map(_._2).forall(f)

  /**
   * Returns all the labels, in a flattened form.
   */
  final def labels: Vector[(Vector[L], L)] = {
    def loop(ancestors: Vector[L])(spec: ZSpec[R, E, L]): Vector[(Vector[L], L)] = spec match {
      case ZSpec.Suite(label, specs) => Vector((ancestors, label)) ++ specs.flatMap(loop(ancestors :+ label))
      case ZSpec.Test(label, _)      => Vector((ancestors, label))
      case ZSpec.Concat(head, tail)  => loop(ancestors)(head) ++ tail.flatMap(loop(ancestors))
    }

    loop(Vector())(self)
  }

  /**
   * Returns a new spec with a remapped error type.
   */
  final def mapError[E1](f: E => E1): ZSpec[R, E1, L] = {
    def loop(spec: ZSpec[R, E, L]): ZSpec[R, E1, L] = spec match {
      case ZSpec.Suite(label, specs) => ZSpec.Suite(label, specs.map(loop))
      case ZSpec.Test(label, assert) => ZSpec.Test(label, assert.mapError(f))
      case ZSpec.Concat(head, tail)  => ZSpec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Returns a new spec with a remapped label type.
   */
  final def mapLabel[L1](f: L => L1): ZSpec[R, E, L1] = {
    def loop(spec: ZSpec[R, E, L]): ZSpec[R, E, L1] = spec match {
      case ZSpec.Suite(label, specs) => ZSpec.Suite(f(label), specs.map(loop))
      case ZSpec.Test(label, assert) => ZSpec.Test(f(label), assert)
      case ZSpec.Concat(head, tail)  => ZSpec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Returns a new spec, where every test in this one is marked as pending.
   */
  final def pending: ZSpec[R, E, L] = weaveAll(_ => ZIO.succeed(AssertResult.Pending))

  /**
   * Provides a spec with the value it requires, eliminating its requirement.
   */
  final def provide(r: R): ZSpec[Any, E, L] = {
    def loop(spec: ZSpec[R, E, L]): ZSpec[Any, E, L] = spec match {
      case ZSpec.Suite(label, specs) => ZSpec.Suite(label, specs.map(loop))
      case ZSpec.Test(label, assert) => ZSpec.Test(label, assert.provide(r))
      case ZSpec.Concat(head, tail)  => ZSpec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Provides each test with its own managed resource, eliminating their requirements.
   */
  final def provideEach[E1 >: E](managed: Managed[E1, R]): ZSpec[Any, E1, L] = {
    def loop(spec: ZSpec[R, E, L]): ZSpec[Any, E1, L] = spec match {
      case ZSpec.Suite(label, specs) => ZSpec.Suite(label, specs.map(loop))
      case ZSpec.Test(label, assert) => ZSpec.Test(label, managed.use(r => assert.provide(r)))
      case ZSpec.Concat(head, tail)  => ZSpec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Provides a spec with part of the value it requires, eliminating its requirement.
   */
  final def provideSome[R1](f: R1 => R): ZSpec[R1, E, L] = {
    def loop(spec: ZSpec[R, E, L]): ZSpec[R1, E, L] = spec match {
      case ZSpec.Suite(label, specs) => ZSpec.Suite(label, specs.map(loop))
      case ZSpec.Test(label, assert) => ZSpec.Test(label, assert.provideSome(f))
      case ZSpec.Concat(head, tail)  => ZSpec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Returns a new spec that effectfully maps every assert result.
   */
  final def reassert[R1 <: R, E1 >: E](f: TestResult => ZIO[R1, E1, TestResult]): ZSpec[R1, E1, L] = {
    def loop(spec: ZSpec[R, E, L]): ZSpec[R1, E1, L] = spec match {
      case ZSpec.Suite(label, specs) => ZSpec.Suite(label, specs.map(loop))
      case ZSpec.Test(label, assert) => ZSpec.Test(label, assert.flatMap(f))
      case ZSpec.Concat(head, tail)  => ZSpec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Returns the size of the spec, which is the number of tests that it contains.
   */
  final def size: Int = {
    def loop(spec: ZSpec[R, E, L], acc: Int): Int = spec match {
      case ZSpec.Suite(_, specs)    => specs.foldLeft(acc)((acc, spec) => loop(spec, acc))
      case ZSpec.Test(_, _)         => acc + 1
      case ZSpec.Concat(head, tail) => (Vector(head) ++ tail).foldLeft(acc)((acc, spec) => loop(spec, acc))
    }

    loop(self, 0)
  }

  /**
   * Returns a new spec that times out each test by the specified duration.
   * This is merely implemented for convenience atop [[weave]].
   */
  final def timeout(duration: Duration): ZSpec[R with Clock, E, L] =
    weaveAll(_.timeout(duration).map {
      case None    => AssertResult.failure(FailureDetails.Other(s"Timeout of ${duration} exceeded"))
      case Some(v) => v
    })

  /**
   * Weaves an aspect into this spec by replacing every result with its
   * application using the specified function.
   */
  final def weaveAll[R1 <: R, E1 >: E](f: ZIO[R, E, TestResult] => ZIO[R1, E1, TestResult]): ZSpec[R1, E1, L] =
    weaveSome[R1, E1](_ => true)(f)

  /**
   * Weaves an aspect into this spec by replacing every test matching the
   * predicate with its application using the specified function.
   */
  final def weaveSome[R1 <: R, E1 >: E](predTest: L => Boolean)(
    f: ZIO[R, E, TestResult] => ZIO[R1, E1, TestResult]
  ): ZSpec[R1, E1, L] = weaveSomeSuite[R1, E1](predTest, _ => true)(f)

  /**
   * Weaves an aspect into this spec by replacing every test matching the
   * predicate with its application using the specified function, but
   * descending only into suites that match the specified suite predicate.
   */
  final def weaveSomeSuite[R1 <: R, E1 >: E](predTest: L => Boolean, predSuite: L => Boolean)(
    f: ZIO[R, E, TestResult] => ZIO[R1, E1, TestResult]
  ): ZSpec[R1, E1, L] = {
    def loop(spec: ZSpec[R, E, L]): ZSpec[R1, E1, L] = spec match {
      case ZSpec.Suite(label, specs) => ZSpec.Suite(label, if (predSuite(label)) specs.map(loop) else specs)
      case ZSpec.Test(label, assert) => ZSpec.Test(label, if (predTest(label)) f(assert) else assert)
      case ZSpec.Concat(head, tail)  => ZSpec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }
}
object ZSpec {
  final case class Suite[-R, +E, +L](label: L, specs: Vector[ZSpec[R, E, L]])             extends ZSpec[R, E, L]
  final case class Test[-R, +E, +L](label: L, assertion: ZIO[R, E, TestResult])           extends ZSpec[R, E, L]
  final case class Concat[-R, +E, +L](head: ZSpec[R, E, L], tail: Vector[ZSpec[R, E, L]]) extends ZSpec[R, E, L]
}
