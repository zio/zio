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

import Spec._

/**
 * A `Spec[R, E, L, T]` is the backbone of _ZIO Test_. Every spec is either an
 * effectual suite that requires an environment `R` and may fail with an `E` or
 * succeed with other specs, or a test of type `T`. All specs are annotated
 * with labels of type `L`.
 */
final case class Spec[-R, +E, +L, +T](caseValue: SpecCase[R, E, L, T, Spec[R, E, L, T]]) { self =>

  /**
   * Returns a new spec with the suite labels distinguished by `Left`, and the
   * test labels distinguished by `Right`.
   */
  final def distinguish: Spec[R, E, Either[L, L], T] =
    transform[R, E, Either[L, L], T] {
      case SuiteCase(label, specs, exec) => SuiteCase(Left(label), specs, exec)
      case TestCase(label, test)         => TestCase(Right(label), test)
    }

  /**
   * Determines if any node in the spec is satisfied by the given predicate.
   */
  final def exists(f: SpecCase[R, E, L, T, Unit] => Boolean): ZIO[R, E, Boolean] =
    fold[ZIO[R, E, Boolean]] {
      case c @ SuiteCase(_, specs, _) => specs.flatMap(ZIO.collectAll(_).map(_.exists(identity) || f(c.map(_ => ()))))
      case c @ TestCase(_, _)         => ZIO.succeed(f(c))
    }

  /**
   * Folds over all nodes to produce a final result.
   */
  final def fold[Z](f: SpecCase[R, E, L, T, Z] => Z): Z =
    caseValue match {
      case SuiteCase(label, specs, exec) => f(SuiteCase(label, specs.map(_.map(_.fold(f)).toVector), exec))
      case t @ TestCase(_, _)            => f(t)
    }

  /**
   * Effectfully folds over all nodes according to the execution strategy of
   * suites, utilizing the specified default for other cases.
   */
  final def foldM[R1 <: R, E1, Z](
    defExec: ExecutionStrategy
  )(f: SpecCase[R, E, L, T, Z] => ZIO[R1, E1, Z]): ZIO[R1, E1, Z] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        exec.getOrElse(defExec) match {
          case ExecutionStrategy.Parallel =>
            specs.foldM(
              e => f(SuiteCase(label, ZIO.fail(e), exec)),
              ZIO
                .foreachPar(_)(_.foldM(defExec)(f))
                .flatMap(z => f(SuiteCase(label, ZIO.succeed(z.toVector), exec)))
            )
          case ExecutionStrategy.ParallelN(n) =>
            specs.foldM(
              e => f(SuiteCase(label, ZIO.fail(e), exec)),
              ZIO
                .foreachParN(n)(_)(_.foldM(defExec)(f))
                .flatMap(z => f(SuiteCase(label, ZIO.succeed(z.toVector), exec)))
            )
          case ExecutionStrategy.Sequential =>
            specs.foldM(
              e => f(SuiteCase(label, ZIO.fail(e), exec)),
              ZIO
                .foreach(_)(_.foldM(defExec)(f))
                .flatMap(z => f(SuiteCase(label, ZIO.succeed(z.toVector), exec)))
            )
        }
      case t @ TestCase(_, _) => f(t)
    }

  /**
   * Determines if all node in the spec are satisfied by the given predicate.
   */
  final def forall(f: SpecCase[R, E, L, T, Unit] => Boolean): ZIO[R, E, Boolean] =
    fold[ZIO[R, E, Boolean]] {
      case c @ SuiteCase(_, specs, _) => specs.flatMap(ZIO.collectAll(_).map(_.forall(identity) && f(c.map(_ => ()))))
      case c @ TestCase(_, _)         => ZIO.succeed(f(c))
    }

  /**
   * Iterates over the spec with the specified default execution strategy, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachExec[R1 <: R, E1, A](
    defExec: ExecutionStrategy
  )(failure: E => ZIO[R1, E1, A], success: T => ZIO[R1, E1, A]): ZIO[R1, E1, Spec[R1, E1, L, A]] =
    foldM[R1, E1, Spec[R1, E1, L, A]](defExec) {
      case SuiteCase(label, specs, exec) =>
        specs.foldM(e => failure(e).map(Spec.test(label, _)), a => ZIO.succeed(Spec.suite(label, ZIO.succeed(a), exec)))
      case TestCase(label, test) =>
        success(test).map(test => Spec.test(label, test))
    }

  /**
   * Iterates over the spec with the sequential strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreach[R1 <: R, E1, A](
    failure: E => ZIO[R1, E1, A],
    success: T => ZIO[R1, E1, A]
  ): ZIO[R1, E1, Spec[R1, E1, L, A]] =
    foreachExec(ExecutionStrategy.Sequential)(failure, success)

  /**
   * Iterates over the spec with the parallel strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachPar[R1 <: R, E1, A](
    failure: E => ZIO[R1, E1, A],
    success: T => ZIO[R1, E1, A]
  ): ZIO[R1, E1, Spec[R1, E1, L, A]] =
    foreachExec(ExecutionStrategy.Parallel)(failure, success)

  /**
   * Iterates over the spec with the parallel (`n`) strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachParN[R1 <: R, E1, A](
    n: Int
  )(failure: E => ZIO[R1, E1, A], success: T => ZIO[R1, E1, A]): ZIO[R1, E1, Spec[R1, E1, L, A]] =
    foreachExec(ExecutionStrategy.ParallelN(n))(failure, success)

  /**
   * Returns a new spec with remapped labels.
   */
  final def mapLabel[L1](f: L => L1): Spec[R, E, L1, T] =
    transform[R, E, L1, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(f(label), specs, exec)
      case TestCase(label, test)         => TestCase(f(label), test)
    }

  /**
   * Returns a new spec with remapped tests.
   */
  final def mapTest[T1](f: T => T1): Spec[R, E, L, T1] =
    transform[R, E, L, T1] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs, exec)
      case TestCase(label, test)         => TestCase(label, f(test))
    }

  /**
   * Provides any suites in this spec with their required environment,
   * eliminating their dependency on `R`.
   */
  final def provide(r: R): Spec[Any, E, L, T] =
    transform[Any, E, L, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.provide(r), exec)
      case TestCase(label, test)         => TestCase(label, test)
    }

  /**
   * An effectual version of `provide`, useful when the act of provision
   * requires an effect.
   */
  final def provideM[E1 >: E](r: ZIO[Any, E1, R]): Spec[Any, E1, L, T] =
    transform[Any, E1, L, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.provideM(r), exec)
      case TestCase(label, test)         => TestCase(label, test)
    }

  /**
   * Uses the specified `Managed` to provide any suites in this spec with their
   * required environment.
   */
  final def provideManaged[E1 >: E](r: Managed[E1, R]): Spec[Any, E1, L, T] =
    transform[Any, E1, L, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.provideManaged(r), exec)
      case TestCase(label, test)         => TestCase(label, test)
    }

  /**
   * Provides some of the environment required by any suites in this spec,
   * leaving the remainder `R0`.
   */
  final def provideSome[R0](f: R0 => R): Spec[R0, E, L, T] =
    transform[R0, E, L, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.provideSome(f), exec)
      case TestCase(label, test)         => TestCase(label, test)
    }

  /**
   * An effectual version of `provideSome`, useful when the act of partial
   * provision requires an effect.
   */
  final def provideSomeM[R0, E1 >: E](r: ZIO[R0, E1, R]): Spec[R0, E1, L, T] =
    transform[R0, E1, L, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.provideSomeM(r), exec)
      case TestCase(label, test)         => TestCase(label, test)
    }

  /**
   * Uses the given `ZManaged` to provide some of the environment required by
   * any suites in this spec, leaving the remainder `R0`.
   */
  final def provideSomeManaged[R0, E1 >: E](r: ZManaged[R0, E1, R]): Spec[R0, E1, L, T] =
    transform[R0, E1, L, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.provideSomeManaged(r), exec)
      case TestCase(label, test)         => TestCase(label, test)
    }

  /**
   * Computes the size of the spec, i.e. the number of tests in the spec.
   */
  final def size: ZIO[R, E, Int] = fold[ZIO[R, E, Int]] {
    case SuiteCase(_, counts, _) => counts.flatMap(ZIO.collectAll(_).map(_.sum))
    case TestCase(_, _)          => ZIO.succeed(1)
  }

  /**
   * Transforms the spec one layer at a time.
   */
  final def transform[R1, E1, L1, T1](
    f: SpecCase[R, E, L, T, Spec[R1, E1, L1, T1]] => SpecCase[R1, E1, L1, T1, Spec[R1, E1, L1, T1]]
  ): Spec[R1, E1, L1, T1] =
    caseValue match {
      case SuiteCase(label, specs, exec) => Spec(f(SuiteCase(label, specs.map(_.map(_.transform(f))), exec)))
      case t @ TestCase(_, _)            => Spec(f(t))
    }

  /**
   * Transforms the spec statefully, one layer at a time.
   */
  final def transformAccum[R1, E1, L1, T1, Z](
    z0: Z
  )(
    f: (Z, SpecCase[R1, E, L, T, Spec[R1, E1, L1, T1]]) => (Z, SpecCase[R1, E1, L1, T1, Spec[R1, E1, L1, T1]])
  ): ZIO[R, E, (Z, Spec[R1, E1, L1, T1])] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        for {
          specs <- specs
          result <- ZIO.foldLeft(specs)(z0 -> Vector.empty[Spec[R1, E1, L1, T1]]) {
                     case ((z, vector), spec) =>
                       spec.transformAccum(z)(f).map { case (z1, spec1) => z1 -> (vector :+ spec1) }
                   }
          (z, specs1)     = result
          res             = f(z, SuiteCase(label, ZIO.succeed(specs1), exec))
          (z1, caseValue) = res
        } yield z1 -> Spec(caseValue)
      case t @ TestCase(_, _) =>
        val (z, caseValue) = f(z0, t)
        ZIO.succeed(z -> Spec(caseValue))
    }
}
object Spec {
  sealed trait SpecCase[-R, +E, +L, +T, +A] { self =>
    final def map[B](f: A => B): SpecCase[R, E, L, T, B] = self match {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.map(_.map(f)), exec)
      case TestCase(label, test)         => TestCase(label, test)
    }
  }
  final case class SuiteCase[-R, +E, +L, +A](label: L, specs: ZIO[R, E, Vector[A]], exec: Option[ExecutionStrategy])
      extends SpecCase[R, E, L, Nothing, A]
  final case class TestCase[+L, +T](label: L, test: T) extends SpecCase[Any, Nothing, L, T, Nothing]

  final def suite[R, E, L, T](
    label: L,
    specs: ZIO[R, E, Vector[Spec[R, E, L, T]]],
    exec: Option[ExecutionStrategy]
  ): Spec[R, E, L, T] =
    Spec(SuiteCase(label, specs, exec))

  final def test[L, T](label: L, test: T): Spec[Any, Nothing, L, T] =
    Spec(TestCase(label, test))
}
