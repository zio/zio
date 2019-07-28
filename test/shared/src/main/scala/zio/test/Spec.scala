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

/**
 * A `Spec[T, L]` is the backbone of _ZIO Test_. ZSpecs require an environment
 * of type `R` (which could be `Any`), may fail with errors of type `E`, and
 * are annotated with labels of type `L` (typically `String`).
 */
sealed trait Spec[+T, +L] { self =>

  /**
   * Returns a new spec with the suite labels distinguished by `Left`, and the
   * test labels distinguished by `Right`.
   */
  final def distinguish: Spec[T, Either[L, L]] = map(Left(_), (l, t) => (Right(l), t))

  /**
   * Determines if there exists a label or test satisfying the predicate.
   */
  final def exists(ifSuite: L => Boolean, ifTest: (T, L) => Boolean): Boolean =
    fold(false)((acc, l) => acc || ifSuite(l), (acc, l, t) => acc || ifTest(t, l))

  /**
   * Determines if there exists a label satisfying the predicate.
   */
  final def existsLabel(f: L => Boolean): Boolean = exists(f, (_, l) => f(l))

  /**
   * Determines if there exists a test satisfying the predicate.
   */
  final def existsTest(f: T => Boolean): Boolean = exists(_ => false, (t, _) => f(t))

  /**
   * Returns a filtered spec that removes any suite or test not satisfied by
   * the specified predicates.
   */
  final def filter(ifSuite: L => Boolean, ifTest: (L, T) => Boolean): Option[Spec[T, L]] = {
    def loop(spec: Spec[T, L]): Option[Spec[T, L]] = spec match {
      case Spec.Suite(label, specs) =>
        if (ifSuite(label)) Some(Spec.Suite(label, specs.map(loop).flatMap(_.toVector))) else None
      case Spec.Test(label, test) => if (ifTest(label, test)) Some(Spec.Test(label, test)) else None
    }

    loop(self)
  }

  /**
   * Returns a filtered spec that removes any spec not satisfied by the specified predicates.
   */
  final def filterLabel(f: L => Boolean): Option[Spec[T, L]] = filter(f, (l, _) => f(l))

  /**
   * Returns a filtered spec that removes any test not satisfied by the specified predicates.
   */
  final def filterTest(f: (L, T) => Boolean): Option[Spec[T, L]] = filter(_ => true, f)

  /**
   * Folds over the spec, accumulating a value over suites and tests.
   */
  final def fold[Z](z: Z)(ifSuite: (Z, L) => Z, ifTest: (Z, L, T) => Z): Z = {
    def fold0(z: Z)(spec: Spec[T, L]): Z = spec match {
      case Spec.Suite(label, specs) =>
        specs.foldLeft(ifSuite(z, label)) {
          case (acc, spec) => fold0(acc)(spec)
        }
      case Spec.Test(label, test) => ifTest(z, label, test)
    }

    fold0(z)(self)
  }

  /**
   * Folds over the labels in the spec spec, accumulating a value.
   */
  final def foldLabel[Z](z: Z)(f: (Z, L) => Z): Z = fold(z)(f, (z, l, _) => f(z, l))

  /**
   * Folds over the tests in the spec spec, accumulating a value.
   */
  final def foldTest[Z](z: Z)(f: (Z, L, T) => Z): Z = fold(z)((z, _) => z, (z, l, t) => f(z, l, t))

  /**
   * Determines if all labels and tests satisfy the specified predicates.
   */
  final def forall(ifSuite: L => Boolean, ifTest: (T, L) => Boolean): Boolean =
    fold(true)((acc, l) => acc && ifSuite(l), (acc, l, t) => acc && ifTest(t, l))

  /**
   * Determines if all labels satisfy the specified predicate.
   */
  final def forallLabel(f: L => Boolean): Boolean = forall(f, (_, l) => f(l))

  /**
   * Determines if all labels satisfy the specified predicate.
   */
  final def forallTest(f: T => Boolean): Boolean = forall(_ => true, (t, _) => f(t))

  /**
   * Returns a new spec with the labels and tests computed by stateful map
   * functions.
   */
  final def mapAccum[S, T1, L1](s: S)(ifSuite: (S, L) => (S, L1), ifTest: (S, L, T) => (S, L1, T1)): Spec[T1, L1] = {
    def fold(s0: S, specs0: Iterable[Spec[T, L]]): (S, Vector[Spec[T1, L1]]) =
      specs0.foldLeft(s0 -> Vector.empty[Spec[T1, L1]]) {
        case ((s0, acc), spec0) =>
          val (s, spec) = loop(spec0, s0)

          s -> (acc ++ Vector(spec))
      }

    def loop(spec: Spec[T, L], s0: S): (S, Spec[T1, L1]) = spec match {
      case Spec.Suite(label, specs0) =>
        val (s1, label2) = ifSuite(s0, label)

        val (s2, specs) = fold(s1, specs0)

        s2 -> Spec.Suite(label2, specs)

      case Spec.Test(label, test) =>
        val (s1, label2, test2) = ifTest(s0, label, test)

        s1 -> Spec.Test(label2, test2)

    }

    loop(self, s)._2
  }

  /**
   * Returns a new spec with a remapped label type.
   */
  final def mapLabel[L1](f: L => L1): Spec[T, L1] = map(f, (l, t) => (f(l), t))

  /**
   * Returns a new spec with a remapped label type.
   */
  final def mapTest[T1](f: T => T1): Spec[T1, L] = map(l => l, (l, t) => (l, f(t)))

  /**
   * Returns a new spec with remapped tests.
   */
  final def map[T1, L1](ifSuite: L => L1, ifTest: (L, T) => (L1, T1)): Spec[T1, L1] = {
    def loop(spec: Spec[T, L]): Spec[T1, L1] = spec match {
      case Spec.Suite(label, specs) => Spec.Suite(ifSuite(label), specs.map(loop))
      case Spec.Test(label, test)   => (Spec.Test[T1, L1](_, _)).tupled(ifTest(label, test))
    }

    loop(self)
  }

  /**
   * Returns the size of the spec, which is the number of tests that it contains.
   */
  final def size: Int = fold(0)((count, _) => count + 1, (count, _, _) => count + 1)
}
object Spec {
  final case class Suite[+T, +L](label: L, specs: Vector[Spec[T, L]]) extends Spec[T, L]
  final case class Test[+T, +L](label: L, test: T)                    extends Spec[T, L]
}
