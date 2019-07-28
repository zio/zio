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

import zio.ZIO

/**
 * A `Spec[T, L]` is the backbone of _ZIO Test_. ZSpecs require an environment
 * of type `R` (which could be `Any`), may fail with errors of type `E`, and
 * are annotated with labels of type `L` (typically `String`).
 */
sealed trait Spec[+T, +L] { self =>

  /**
   * Concatenates this spec onto the specified spec.
   */
  final def ++[T1 >: T, L1 >: L](that: Spec[T1, L1]): Spec[T1, L1] =
    Spec.Concat(self, Vector(that))

  /**
   * Returns a new spec with the suite labels distinguished by `Left`, and the
   * test labels distinguished by `Right`.
   */
  final def distinguish: Spec[T, Either[L, L]] = {
    def loop(spec: Spec[T, L]): Spec[T, Either[L, L]] = spec match {
      case Spec.Suite(label, specs) => Spec.Suite(Left(label), specs.map(loop))
      case Spec.Test(label, assert) => Spec.Test(Right(label), assert)
      case Spec.Concat(head, tail)  => Spec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Determines if there exists a label satisfying the predicate.
   */
  final def exists(f: L => Boolean): Boolean =
    labels.map(_._2).exists(f)

  /**
   * Returns a filtered spec that replaces any test not satisfied by the
   * predicate by the given empty test.
   */
  final def filter[T1 >: T](empty: T1)(f: L => Boolean): Spec[T1, L] = {
    def loop(spec: Spec[T, L]): Spec[T1, L] = spec match {
      case Spec.Suite(label, specs) => Spec.Suite(label, specs.map(loop))
      case Spec.Test(label, assert) =>
        if (f(label)) Spec.Test(label, assert)
        else Spec.Test(label, empty)
      case Spec.Concat(head, tail) => Spec.Concat(loop(head), tail.map(loop))
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
    def loop(ancestors: Vector[L])(spec: Spec[T, L]): Vector[(Vector[L], L)] = spec match {
      case Spec.Suite(label, specs) => Vector((ancestors, label)) ++ specs.flatMap(loop(ancestors :+ label))
      case Spec.Test(label, _)      => Vector((ancestors, label))
      case Spec.Concat(head, tail)  => loop(ancestors)(head) ++ tail.flatMap(loop(ancestors))
    }

    loop(Vector())(self)
  }

  /**
   * Returns a new spec with a remapped label type.
   */
  final def mapLabel[L1](f: L => L1): Spec[T, L1] = {
    def loop(spec: Spec[T, L]): Spec[T, L1] = spec match {
      case Spec.Suite(label, specs) => Spec.Suite(f(label), specs.map(loop))
      case Spec.Test(label, assert) => Spec.Test(f(label), assert)
      case Spec.Concat(head, tail)  => Spec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Returns a new spec with the labels computed by an stateful map function.
   */
  final def mapLabelAccum[S, L1](s: S)(f: (S, L) => (S, L1)): Spec[T, L1] = {
    def fold(s0: S, specs0: Iterable[Spec[T, L]]): (S, Vector[Spec[T, L1]]) =
      specs0.foldLeft(s0 -> Vector.empty[Spec[T, L1]]) {
        case ((s0, acc), spec0) =>
          val (s, spec) = loop(spec0, s0)

          s -> (acc ++ Vector(spec))
      }

    def loop(spec: Spec[T, L], s0: S): (S, Spec[T, L1]) = spec match {
      case Spec.Suite(label, specs0) =>
        val (s1, label2) = f(s0, label)

        val (s2, specs) = fold(s1, specs0)

        s2 -> Spec.Suite(label2, specs)

      case Spec.Test(label, assert) =>
        val (s1, label2) = f(s0, label)

        s1 -> Spec.Test(label2, assert)

      case Spec.Concat(head0, tail0) =>
        val (s1, head) = loop(head0, s0)
        val (s2, tail) = fold(s1, tail0)

        s2 -> Spec.Concat(head, tail)

    }

    loop(self, s)._2
  }

  /**
   * Returns a new spec with remapped tests.
   */
  final def mapTest[T1](f: T => T1): Spec[T1, L] = {
    def loop(spec: Spec[T, L]): Spec[T1, L] = spec match {
      case Spec.Suite(label, specs) => Spec.Suite(label, specs.map(loop))
      case Spec.Test(label, assert) => Spec.Test(label, f(assert))
      case Spec.Concat(head, tail)  => Spec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Returns a new spec with remapped tests.
   */
  final def mapTestLabel[T1](f: (L, T) => T1): Spec[T1, L] = {
    def loop(spec: Spec[T, L]): Spec[T1, L] = spec match {
      case Spec.Suite(label, specs) => Spec.Suite(label, specs.map(loop))
      case Spec.Test(label, assert) => Spec.Test(label, f(label, assert))
      case Spec.Concat(head, tail)  => Spec.Concat(loop(head), tail.map(loop))
    }

    loop(self)
  }

  /**
   * Returns a new spec with effectfully remapped tests.
   */
  final def mapTestM[E, R, T1](
    f: T => ZIO[R, E, T1]
  ): ZIO[R, E, Spec[T1, L]] = {
    def loop(spec: Spec[T, L]): ZIO[R, E, Spec[T1, L]] = spec match {
      case Spec.Suite(label, specs) => ZIO.foreach(specs)(loop).map(specs => Spec.Suite(label, specs.toVector))
      case Spec.Test(label, assert) => f(assert).map(assert => Spec.Test(label, assert))
      case Spec.Concat(head, tail) =>
        loop(head).zipWith(ZIO.foreach(tail)(loop))((head, tail) => Spec.Concat(head, tail.toVector))
    }

    loop(self)
  }

  /**
   * Returns the size of the spec, which is the number of tests that it contains.
   */
  final def size: Int = {
    def loop(spec: Spec[T, L], acc: Int): Int = spec match {
      case Spec.Suite(_, specs)    => specs.foldLeft(acc)((acc, spec) => loop(spec, acc))
      case Spec.Test(_, _)         => acc + 1
      case Spec.Concat(head, tail) => (Vector(head) ++ tail).foldLeft(acc)((acc, spec) => loop(spec, acc))
    }

    loop(self, 0)
  }

  /**
   * Returnrs a new spec with each label replaced by a tuple containing the
   * label and the index of the label in the tree.
   */
  final def zipWithIndex: Spec[T, (L, Int)] =
    mapLabelAccum(0) { case (index, label) => (index + 1, label -> index) }
}
object Spec {
  final case class Suite[+T, +L](label: L, specs: Vector[Spec[T, L]])         extends Spec[T, L]
  final case class Test[+T, +L](label: L, test: T)                            extends Spec[T, L]
  final case class Concat[+T, +L](head: Spec[T, L], tail: Vector[Spec[T, L]]) extends Spec[T, L]
}
