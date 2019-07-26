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
   * Returns a new spec with the suite labels distinguished by `Left`, and the
   * test labels distinguished by `Right`.
   */
  final def distinguish: ZSpec[R, E, Either[L, L]] = {
    def loop(spec: ZSpec[R, E, L]): ZSpec[R, E, Either[L, L]] = spec match {
      case ZSpec.Suite(label, specs) => ZSpec.Suite(Left(label), specs.map(loop))
      case ZSpec.Test(label, assert) => ZSpec.Test(Right(label), assert)
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
   * Returns a filtered spec that marks any test not satisfying the predicate
   * as pending.
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
   * Returns a new spec with a remapped label type.
   */
  final def map[L1](f: L => L1): ZSpec[R, E, L1] = {
    def loop(spec: ZSpec[R, E, L]): ZSpec[R, E, L1] = spec match {
      case ZSpec.Suite(label, specs) => ZSpec.Suite(f(label), specs.map(loop))
      case ZSpec.Test(label, assert) => ZSpec.Test(f(label), assert)
      case ZSpec.Concat(head, tail)  => ZSpec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Returns a new spec with the labels computed by an stateful map function.
   */
  final def mapAccum[S, L1](s: S)(f: (S, L) => (S, L1)): ZSpec[R, E, L1] = {
    def fold(s0: S, specs0: Iterable[ZSpec[R, E, L]]): (S, Vector[ZSpec[R, E, L1]]) =
      specs0.foldLeft(s0 -> Vector.empty[ZSpec[R, E, L1]]) {
        case ((s0, acc), spec0) =>
          val (s, spec) = loop(spec0, s0)

          s -> (acc ++ Vector(spec))
      }

    def loop(spec: ZSpec[R, E, L], s0: S): (S, ZSpec[R, E, L1]) = spec match {
      case ZSpec.Suite(label, specs0) =>
        val (s1, label2) = f(s0, label)

        val (s2, specs) = fold(s1, specs0)

        s2 -> ZSpec.Suite(label2, specs)

      case ZSpec.Test(label, assert) =>
        val (s1, label2) = f(s0, label)

        s1 -> ZSpec.Test(label2, assert)

      case ZSpec.Concat(head0, tail0) =>
        val (s1, head) = loop(head0, s0)
        val (s2, tail) = fold(s1, tail0)

        s2 -> ZSpec.Concat(head, tail)

    }

    loop(self, s)._2
  }

  /**
   * Returns a new spec with a remapped error type.
   */
  final def mapError[E1](f: E => E1): ZSpec[R, E1, L] = mapTest(_.mapError(f))

  /**
   * Returns a new spec with remapped tests.
   */
  final def mapTest[R1, E1](f: ZIO[R, E, TestResult] => ZIO[R1, E1, TestResult]): ZSpec[R1, E1, L] = {
    def loop(spec: ZSpec[R, E, L]): ZSpec[R1, E1, L] = spec match {
      case ZSpec.Suite(label, specs) => ZSpec.Suite(label, specs.map(loop))
      case ZSpec.Test(label, assert) => ZSpec.Test(label, f(assert))
      case ZSpec.Concat(head, tail)  => ZSpec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Returns a new spec with effectfully remapped tests.
   */
  final def mapTestM[R1 <: R, E1 >: E, R2, E2](
    f: ZIO[R, E, TestResult] => ZIO[R2, E2, ZIO[R1, E1, TestResult]]
  ): ZIO[R2, E2, ZSpec[R1, E1, L]] = {
    def loop(spec: ZSpec[R, E, L]): ZIO[R2, E2, ZSpec[R1, E1, L]] = spec match {
      case ZSpec.Suite(label, specs) => ZIO.foreach(specs)(loop).map(specs => ZSpec.Suite(label, specs.toVector))
      case ZSpec.Test(label, assert) => f(assert).map(assert => ZSpec.Test(label, assert))
      case ZSpec.Concat(head, tail) =>
        loop(head).zipWith(ZIO.foreach(tail)(loop))((head, tail) => ZSpec.Concat(head, tail.toVector))
    }

    loop(self)
  }

  /**
   * Provides a spec with the value it requires, eliminating its requirement.
   */
  final def provide(r: R): ZSpec[Any, E, L] = mapTest(_.provide(r))

  /**
   * Provides each test with its own managed resource, eliminating their requirements.
   */
  final def provideManaged[E1 >: E](managed: Managed[E1, R]): ZSpec[Any, E1, L] =
    mapTest(test => managed.use(test.provide(_)))

  /**
   * Provides a spec with part of the value it requires, eliminating its requirement.
   */
  final def provideSome[R1](f: R1 => R): ZSpec[R1, E, L] = mapTest(_.provideSome(f))

  /**
   * Returns a new spec that effectfully maps every assert result.
   */
  final def reassert(f: TestResult => TestResult): ZSpec[R, E, L] = reassertM(t => ZIO.succeed(f(t)))

  /**
   * Returns a new spec that effectfully maps every assert result.
   */
  final def reassertM[R1 <: R, E1 >: E](f: TestResult => ZIO[R1, E1, TestResult]): ZSpec[R1, E1, L] =
    mapTest(test => test.flatMap(f))

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
   * Returnrs a new spec with each label replaced by a tuple containing the
   * label and the index of the label in the tree.
   */
  final def zipWithIndex: ZSpec[R, E, (L, Int)] =
    mapAccum(0) { case (index, label) => (index + 1, label -> index) }
}
object ZSpec {
  implicit class ZSpecInvariantSyntax[R, E, L](self: ZSpec[R, E, L]) {

    /**
     * Returns a new spec with the specified aspect woven into all tests.
     */
    final def weave[R1 >: R, R2 <: R, E1 <: E, E2 >: E](aspect: TestAspect[R2, R1, E1, E2]): ZSpec[R, E, L] =
      weaveSome(_ => true)(aspect)

    /**
     * Returns a new spec with the specified aspect woven into the specified tests.
     */
    final def weaveSome[R1 >: R, R2 <: R, E1 <: E, E2 >: E](
      pred: L => Boolean
    )(aspect: TestAspect[R2, R1, E1, E2]): ZSpec[R, E, L] = {
      def loop(spec: ZSpec[R, E, L]): ZSpec[R, E, L] = spec match {
        case ZSpec.Suite(label, specs) => ZSpec.Suite(label, if (pred(label)) specs.map(loop) else specs)
        case ZSpec.Test(label, assert) => ZSpec.Test(label, if (pred(label)) aspect(assert) else assert)
        case ZSpec.Concat(head, tail)  => ZSpec.Concat(loop(head), tail.map(loop))
      }

      loop(self)
    }
  }

  final case class Suite[-R, +E, +L](label: L, specs: Vector[ZSpec[R, E, L]])             extends ZSpec[R, E, L]
  final case class Test[-R, +E, +L](label: L, assertion: ZIO[R, E, TestResult])           extends ZSpec[R, E, L]
  final case class Concat[-R, +E, +L](head: ZSpec[R, E, L], tail: Vector[ZSpec[R, E, L]]) extends ZSpec[R, E, L]
}
