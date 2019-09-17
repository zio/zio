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
      case EffectCase(_)                 => ???
    }

  /**
   * Determines if any node in the spec is satisfied by the given predicate.
   */
  final def exists(f: SpecCase[R, E, L, T, Unit] => Boolean): ZIO[R, E, Boolean] =
    fold[ZIO[R, E, Boolean]] {
      case c @ SuiteCase(_, specs, _) => ZIO.collectAll(specs).map(_.exists(identity) || f(c.map(_ => ())))
      case c @ TestCase(_, _)         => ZIO.succeed(f(c))
      case c @ EffectCase(effect)     => effect.map(specs => f(specs.map(_ => ())) || f(c.map(_ => ())))
    }

  /**
   * Folds over all nodes to produce a final result.
   */
  final def fold[Z](f: SpecCase[R, E, L, T, Z] => Z): Z =
    caseValue match {
      case SuiteCase(label, specs, exec) => f(SuiteCase(label, specs.map(_.fold(f)), exec))
      case t @ TestCase(_, _)            => f(t)
      case EffectCase(effect)            => f(EffectCase(effect.map(_.map(_.fold(f)))))
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
            ZIO.foreachPar(specs)(_.foldM(defExec)(f)).flatMap(specs => f(SuiteCase(label, specs.toVector, exec)))
          case ExecutionStrategy.ParallelN(n) =>
            ZIO
              .foreachParN(n)(specs)(_.foldM(defExec)(f))
              .flatMap(specs => f(SuiteCase(label, specs.toVector, exec)))
          case ExecutionStrategy.Sequential =>
            ZIO.foreach(specs)(_.foldM(defExec)(f)).flatMap(specs => f(SuiteCase(label, specs.toVector, exec)))
        }
      case t @ TestCase(_, _) => f(t)
      case EffectCase(effect) =>
        effect.foldM(
          e => f(EffectCase(ZIO.fail(e))),
          a => Spec(a).foldM(defExec)(f)
        )
    }

  /**
   * Determines if all node in the spec are satisfied by the given predicate.
   */
  final def forall(f: SpecCase[R, E, L, T, Unit] => Boolean): ZIO[R, E, Boolean] =
    fold[ZIO[R, E, Boolean]] {
      case c @ SuiteCase(_, specs, _) => ZIO.collectAll(specs).map(_.forall(identity) && f(c.map(_ => ())))
      case c @ TestCase(_, _)         => ZIO.succeed(f(c))
      case c @ EffectCase(effect)     => effect.map(specs => f(specs.map(_ => ())) && f(c.map(_ => ())))
    }

  /**
   * Iterates over the spec with the specified default execution strategy, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachExec[R1 <: R, E1, A](
    defExec: ExecutionStrategy
  )(failure: E => ZIO[R1, E1, A], success: T => ZIO[R1, E1, A]): ZIO[R1, E1, Spec[R1, E1, L, A]] = {
    val _ = failure
    foldM[R1, E1, Spec[R1, E1, L, A]](defExec) {
      case s @ SuiteCase(_, _, _) =>
        ZIO.succeed(Spec(s))
      case TestCase(label, test) =>
        success(test).map(test => Spec.test(label, test))
      case EffectCase(_) =>
        ???
    }
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
      case EffectCase(_)                 => ???
    }

  /**
   * Returns a new spec with remapped tests.
   */
  final def mapTest[T1](f: T => T1): Spec[R, E, L, T1] =
    transform[R, E, L, T1] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs, exec)
      case TestCase(label, test)         => TestCase(label, f(test))
      case EffectCase(_)                 => ???
    }

  final def provideManaged[E1 >: E](r: Managed[E1, R]): Spec[Any, E1, L, T] =
    transform[Any, E1, L, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.map(_.provideManaged(r)), exec)
      case TestCase(label, test)         => TestCase(label, test)
      case EffectCase(_)                 => ???
    }

  /**
   * Computes the size of the spec, i.e. the number of tests in the spec.
   */
  final def size: ZIO[R, E, Int] = fold[ZIO[R, E, Int]] {
    case SuiteCase(_, counts, _) => ZIO.collectAll(counts).map(_.sum)
    case TestCase(_, _)          => ZIO.succeed(1)
    case EffectCase(_)           => ???
  }

  /**
   * Transforms the spec one layer at a time.
   */
  final def transform[R1, E1, L1, T1](
    f: SpecCase[R, E, L, T, Spec[R1, E1, L1, T1]] => SpecCase[R1, E1, L1, T1, Spec[R1, E1, L1, T1]]
  ): Spec[R1, E1, L1, T1] =
    caseValue match {
      case SuiteCase(label, specs, exec) => Spec(f(SuiteCase(label, specs.map(_.transform(f)), exec)))
      case t @ TestCase(_, _)            => Spec(f(t))
      case EffectCase(effect)            => Spec(f(EffectCase(effect.map(_.map(_.transform(f))))))
    }

  /**
   * Transforms the spec statefully, one layer at a time.
   */
  // final def transformAccum[R1, E1, L1, T1, Z](
  //   z0: Z
  // )(
  //   f: (Z, SpecCase[R1, E, L, T, Spec[R1, E1, L1, T1]]) => (Z, SpecCase[R1, E1, L1, T1, Spec[R1, E1, L1, T1]])
  // ): ZIO[R, E, (Z, Spec[R1, E1, L1, T1])] =
  //   caseValue match {
  //     case SuiteCase(label, specs, exec) =>
  //       for {
  //         specs <- specs
  //         result <- ZIO.foldLeft(specs)(z0 -> Vector.empty[Spec[R1, E1, L1, T1]]) {
  //                    case ((z, vector), spec) =>
  //                      spec.transformAccum(z)(f).map { case (z1, spec1) => z1 -> (vector :+ spec1) }
  //                  }
  //         (z, specs1)     = result
  //         res             = f(z, SuiteCase(label, ZIO.succeed(specs1), exec))
  //         (z1, caseValue) = res
  //       } yield z1 -> Spec(caseValue)
  //     case t @ TestCase(_, _) =>
  //       val (z, caseValue) = f(z0, t)
  //       ZIO.succeed(z -> Spec(caseValue))
  //   }
}

object Spec {
  sealed trait SpecCase[-R, +E, +L, +T, +A] { self =>
    final def map[B](f: A => B): SpecCase[R, E, L, T, B] = self match {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.map(f), exec)
      case TestCase(label, test)         => TestCase(label, test)
      case EffectCase(effect)            => EffectCase(effect.map(_.map(f)))
    }
    final def mapM[R1 <: R, E1 >: E, B](f: A => ZIO[R1, E1, B]): SpecCase[R1, E1, L, T, B] = self match {
      case SuiteCase(label, specs, exec) =>
        EffectCase(ZIO.foreach(specs)(f).map(bs => SuiteCase(label, bs.toVector, exec)))
      case TestCase(label, test) => TestCase(label, test)
      case EffectCase(effect)    => EffectCase(effect.map(_.mapM(f)))
    }
  }
  final case class SuiteCase[+L, +A](label: L, specs: Vector[A], exec: Option[ExecutionStrategy])
      extends SpecCase[Any, Nothing, L, Nothing, A]
  final case class TestCase[+L, +T](label: L, test: T) extends SpecCase[Any, Nothing, L, T, Nothing]
  final case class EffectCase[-R, +E, +L, +T, +A](effect: ZIO[R, E, SpecCase[R, E, L, T, A]])
      extends SpecCase[R, E, L, T, A]

  final def effect[R, E, L, T](effect: ZIO[R, E, SpecCase[R, E, L, T, Spec[R, E, L, T]]]): Spec[R, E, L, T] =
    Spec(EffectCase(effect))

  final def suite[R, E, L, T](
    label: L,
    specs: Vector[Spec[R, E, L, T]],
    exec: Option[ExecutionStrategy]
  ): Spec[R, E, L, T] =
    Spec(SuiteCase(label, specs, exec))

  final def test[L, T](label: L, test: T): Spec[Any, Nothing, L, T] =
    Spec(TestCase(label, test))
}
