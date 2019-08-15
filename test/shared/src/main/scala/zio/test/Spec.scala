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

import Spec._

/**
 * A `Spec[L, T]` is the backbone of _ZIO Test_. Every spec is either a suite,
 * which contains other specs, or a test of type `T`. All specs are annotated
 * with labels of type `L`.
 */
final case class Spec[+L, +T](caseValue: SpecCase[L, T, Spec[L, T]]) { self =>

  /**
   * Returns a new spec with the suite labels distinguished by `Left`, and the
   * test labels distinguished by `Right`.
   */
  final def distinguish: Spec[Either[L, L], T] = transform[Either[L, L], T] {
    case SuiteCase(label, specs, exec) => SuiteCase(Left(label), specs, exec)
    case TestCase(label, test)         => TestCase(Right(label), test)
  }

  /**
   * Determines if any node in the spec is satisfied by the given predicate.
   */
  final def exists(f: SpecCase[L, T, Unit] => Boolean): Boolean =
    fold[Boolean] {
      case c @ SuiteCase(_, specs, _) => specs.exists(identity) || f(c.map(_ => ()))
      case c @ TestCase(_, _)         => f(c)
    }

  /**
   * Folds over all nodes to produce a final result.
   */
  final def fold[Z](f: SpecCase[L, T, Z] => Z): Z =
    caseValue match {
      case SuiteCase(label, specs, exec) => f(SuiteCase(label, specs.map(_.fold(f)), exec))
      case t @ TestCase(_, _)            => f(t)
    }

  /**
   * Effectfully folds over all nodes according to the execution strategy of
   * suites, utilizing the specified default for other cases.
   */
  final def foldM[R, E, Z](defExec: ExecutionStrategy)(f: SpecCase[L, T, Z] => ZIO[R, E, Z]): ZIO[R, E, Z] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        exec.getOrElse(defExec) match {
          case ExecutionStrategy.Parallel =>
            ZIO.foreachPar(specs)(_.foldM(defExec)(f)).flatMap(specs => f(SuiteCase(label, specs.toVector, exec)))
          case ExecutionStrategy.ParallelN(n) =>
            ZIO
              .foreachParN(n)(specs)(_.foldM(defExec)(f))
              .flatMap(specs => f(SuiteCase(label, specs.toVector, exec)))
          case ExecutionStrategy.Sequential =>
            ZIO.foreach(specs)(_.foldM(defExec)(f)).flatMap(specs => f(SuiteCase(label, specs.toVector, exec)))
        }

      case t @ TestCase(_, _) => f(t)
    }

  /**
   * Determines if all node in the spec are satisfied by the given predicate.
   */
  final def forall(f: SpecCase[L, T, Unit] => Boolean): Boolean =
    fold[Boolean] {
      case c @ SuiteCase(_, specs, _) => specs.forall(identity) && f(c.map(_ => ()))
      case c @ TestCase(_, _)         => f(c)
    }

  /**
   * Iterates over the spec with the specified default execution strategy, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachExec[R, E, A](defExec: ExecutionStrategy)(f: T => ZIO[R, E, A]): ZIO[R, E, Spec[L, A]] =
    foldM[R, E, Spec[L, A]](defExec) {
      case s @ SuiteCase(_, _, _) => ZIO.succeed(Spec(s))
      case TestCase(label, test)  => f(test).map(test => Spec.test(label, test))
    }

  /**
   * Iterates over the spec with the sequential strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreach[R, E, A](f: T => ZIO[R, E, A]): ZIO[R, E, Spec[L, A]] =
    foreachExec(ExecutionStrategy.Sequential)(f)

  /**
   * Iterates over the spec with the parallel strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachPar[R, E, A](f: T => ZIO[R, E, A]): ZIO[R, E, Spec[L, A]] =
    foreachExec(ExecutionStrategy.Parallel)(f)

  /**
   * Iterates over the spec with the parallel (`n`) strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachParN[R, E, A](f: T => ZIO[R, E, A]): ZIO[R, E, Spec[L, A]] =
    foreachExec(ExecutionStrategy.Parallel)(f)

  /**
   * Returns a new spec with remapped labels.
   */
  final def mapLabel[L1](f: L => L1): Spec[L1, T] =
    transform[L1, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(f(label), specs, exec)
      case TestCase(label, test)         => TestCase(f(label), test)
    }

  /**
   * Returns a new spec with remapped tests.
   */
  final def mapTest[T1](f: T => T1): Spec[L, T1] =
    transform[L, T1] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs, exec)
      case TestCase(label, test)         => TestCase(label, f(test))
    }

  /**
   * Computes the size of the spec, i.e. the number of tests in the spec.
   */
  final def size: Int = fold[Int] {
    case SuiteCase(_, counts, _) => counts.sum
    case TestCase(_, _)          => 1
  }

  /**
   * Transforms the spec one layer at a time.
   */
  final def transform[L1, T1](f: SpecCase[L, T, Spec[L1, T1]] => SpecCase[L1, T1, Spec[L1, T1]]): Spec[L1, T1] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        Spec(f(SuiteCase(label, specs.map(_.transform(f)), exec)))
      case t @ TestCase(_, _) => Spec(f(t))
    }

  /**
   * Transforms the spec statefully, one layer at a time.
   */
  final def transformAccum[L1, T1, Z](
    z0: Z
  )(f: (Z, SpecCase[L, T, Spec[L1, T1]]) => (Z, SpecCase[L1, T1, Spec[L1, T1]])): (Z, Spec[L1, T1]) =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        val (z, specs1) =
          specs.foldLeft(z0 -> Vector.empty[Spec[L1, T1]]) {
            case ((z, vector), spec) =>
              val (z1, spec1) = spec.transformAccum(z)(f)

              z1 -> (vector :+ spec1)
          }

        val (z1, caseValue) = f(z, SuiteCase(label, specs1, exec))

        z1 -> Spec(caseValue)
      case t @ TestCase(_, _) =>
        val (z, caseValue) = f(z0, t)
        z -> Spec(caseValue)
    }
}
object Spec {
  sealed trait SpecCase[+L, +T, +A] { self =>
    final def map[B](f: A => B): SpecCase[L, T, B] = self match {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.map(f), exec)
      case TestCase(label, test)         => TestCase(label, test)
    }
  }
  final case class SuiteCase[+L, +A](label: L, specs: Vector[A], exec: Option[ExecutionStrategy])
      extends SpecCase[L, Nothing, A]
  final case class TestCase[+L, +T](label: L, test: T) extends SpecCase[L, T, Nothing]

  final def suite[L, T](label: L, specs: Vector[Spec[L, T]], exec: Option[ExecutionStrategy]): Spec[L, T] =
    Spec(SuiteCase(label, specs, exec))

  final def test[L, T](label: L, test: T): Spec[L, T] =
    Spec(TestCase(label, test))
}
