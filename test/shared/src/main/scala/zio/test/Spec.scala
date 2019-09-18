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
 * A `Spec[R, E, L, T]` is the backbone of _ZIO Test_. Every spec is either a
 * suite, which contains other specs, or a test of type `T`. All specs are
 * annotated with labels of type `L`. Specs may be effectual, requiring an
 * environment of type `R` and potentially failing with an error of type `E`.
 */
final case class Spec[-R, +E, +L, +T](caseValue: SpecCase[R, E, L, T, Spec[R, E, L, T]]) { self =>

  /**
   * Returns a new spec with the suite labels distinguished by `Left`, and the
   * test labels distinguished by `Right`.
   */
  final def distinguish: Spec[R, E, Either[L, L], T] = transform[R, E, Either[L, L], T] {
    case SuiteCase(label, specs, exec) => SuiteCase(Left(label), specs, exec)
    case TestCase(label, test)         => TestCase(Right(label), test)
  }

  /**
   * Determines if any node in the spec is satisfied by the given predicate.
   */
  final def exists(f: PureCase[R, E, L, T, Boolean] => Boolean): ZIO[R, E, Boolean] =
    fold[Boolean] {
      case c @ SuiteCase(_, specs, _) => specs.exists(identity) || f(c)
      case c @ TestCase(_, _)         => f(c)
    }

  /**
   * Folds over all nodes to produce a final result.
   */
  final def fold[Z](f: PureCase[R, E, L, T, Z] => Z): ZIO[R, E, Z] =
    foldM(ExecutionStrategy.Sequential)(ZIO.fail, f andThen ZIO.succeed)

  /**
   * Effectfully folds over all nodes according to the execution strategy of
   * suites, utilizing the specified default for other cases.
   */
  final def foldM[R1 <: R, E1, Z](
    defExec: ExecutionStrategy
  )(failure: E => ZIO[R1, E1, Z], success: PureCase[R, E, L, T, Z] => ZIO[R1, E1, Z]): ZIO[R1, E1, Z] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        exec.getOrElse(defExec) match {
          case ExecutionStrategy.Parallel =>
            ZIO
              .foreachPar(specs)(_.foldM[R1, E1, Z](defExec)(failure, success))
              .flatMap(specs => success(SuiteCase(label, specs.toVector, exec)))
          case ExecutionStrategy.ParallelN(n) =>
            ZIO
              .foreachParN(n)(specs)(_.foldM[R1, E1, Z](defExec)(failure, success))
              .flatMap(specs => success(SuiteCase(label, specs.toVector, exec)))
          case ExecutionStrategy.Sequential =>
            ZIO
              .foreach(specs)(_.foldM[R1, E1, Z](defExec)(failure, success))
              .flatMap(specs => success(SuiteCase(label, specs.toVector, exec)))
        }
      case t @ TestCase(_, _) => success(t)
      case EffectCase(effect) => effect.foldM(failure, Spec(_).foldM[R1, E1, Z](defExec)(failure, success))
    }

  /**
   * Determines if all node in the spec are satisfied by the given predicate.
   */
  final def forall(f: PureCase[R, E, L, T, Boolean] => Boolean): ZIO[R, E, Boolean] =
    fold[Boolean] {
      case c @ SuiteCase(_, specs, _) => specs.forall(identity) && f(c)
      case c @ TestCase(_, _)         => f(c)
    }

  /**
   * Iterates over the spec with the specified default execution strategy, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachExec[R1 <: R, E1, A](
    defExec: ExecutionStrategy
  )(failure: E => ZIO[R1, E1, Nothing], success: T => ZIO[R1, E1, A]): ZIO[R1, E1, Spec[R1, E1, L, A]] =
    foldM[R1, E1, Spec[R1, E1, L, A]](defExec)(failure, {
      case s @ SuiteCase(_, _, _) => ZIO.succeed(Spec(s))
      case TestCase(label, test)  => success(test).map(test => Spec.test(label, test))
    })

  /**
   * Iterates over the spec with the sequential strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreach[R1 <: R, E1 >: E, A](
    failure: E => ZIO[R1, E1, Nothing],
    success: T => ZIO[R1, E1, A]
  ): ZIO[R1, E1, Spec[R1, E1, L, A]] =
    foreachExec[R1, E1, A](ExecutionStrategy.Sequential)(failure, success)

  /**
   * Iterates over the spec with the parallel strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachPar[R1 <: R, E1 >: E, A](
    failure: E => ZIO[R1, E1, Nothing],
    success: T => ZIO[R1, E1, A]
  ): ZIO[R1, E1, Spec[R1, E1, L, A]] =
    foreachExec[R1, E1, A](ExecutionStrategy.Parallel)(failure, success)

  /**
   * Iterates over the spec with the parallel (`n`) strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachParN[R1 <: R, E1 >: E, A](
    n: Int
  )(failure: E => ZIO[R1, E1, Nothing], success: T => ZIO[R1, E1, A]): ZIO[R1, E1, Spec[R1, E1, L, A]] =
    foreachExec[R1, E1, A](ExecutionStrategy.ParallelN(n))(failure, success)

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
   * Uses the specified `Managed` to provide each effect in this spec with its
   * required environment.
   */
  final def provideManaged[E1 >: E](r: Managed[E1, R]): Spec[Any, E1, L, T] =
    caseValue match {
      case SuiteCase(label, specs, exec) => Spec.suite(label, specs.map(_.provideManaged(r)), exec)
      case TestCase(label, test)         => Spec.test(label, test)
      case EffectCase(effect)            => Spec.effect(effect.map(Spec(_).provideManaged(r).caseValue).provideManaged(r))
    }

  /**
   * Computes the size of the spec, i.e. the number of tests in the spec.
   */
  final def size: ZIO[R, E, Int] = fold[Int] {
    case SuiteCase(_, counts, _) => counts.sum
    case TestCase(_, _)          => 1
  }

  /**
   * Transforms the spec one layer at a time.
   */
  final def transform[R1 <: R, E1 >: E, L1, T1](
    f: PureCase[R, E, L, T, Spec[R1, E1, L1, T1]] => PureCase[R1, E1, L1, T1, Spec[R1, E1, L1, T1]]
  ): Spec[R1, E1, L1, T1] =
    caseValue match {
      case SuiteCase(label, specs, exec) => Spec(f(SuiteCase(label, specs.map(_.transform(f)), exec)))
      case t @ TestCase(_, _)            => Spec(f(t))
      case EffectCase(effect)            => Spec(EffectCase(effect.map(Spec(_).transform(f).caseValue)))
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
          result <- ZIO.foldLeft(specs)(z0 -> Vector.empty[Spec[R1, E1, L1, T1]]) {
                     case ((z, vector), spec) =>
                       spec.transformAccum(z)(f).map { case (z1, spec1) => z1 -> (vector :+ spec1) }
                   }
          (z, specs1)     = result
          res             = f(z, SuiteCase(label, specs1, exec))
          (z1, caseValue) = res
        } yield z1 -> Spec(caseValue)
      case t @ TestCase(_, _) =>
        val (z, caseValue) = f(z0, t)
        ZIO.succeed(z -> Spec(caseValue))
      case EffectCase(effect) =>
        effect.flatMap(specs => Spec(specs).transformAccum(z0)(f))
    }
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
  sealed trait PureCase[-R, +E, +L, +T, +A] extends SpecCase[R, E, L, T, A]
  final case class SuiteCase[+L, +A](label: L, specs: Vector[A], exec: Option[ExecutionStrategy])
      extends PureCase[Any, Nothing, L, Nothing, A]
  final case class TestCase[+L, +T](label: L, test: T) extends PureCase[Any, Nothing, L, T, Nothing]
  final case class EffectCase[-R, +E, +L, +T, +A](effect: ZIO[R, E, SpecCase[R, E, L, T, A]])
      extends SpecCase[R, E, L, T, A]

  final def effect[R, E, L, T](effect: ZIO[R, E, SpecCase[R, E, L, T, Spec[R, E, L, T]]]): Spec[R, E, L, T] =
    Spec(EffectCase(effect))

  /**
   * Uses the specified `Managed` once to provide all tests in this spec with
   * a shared version of their required environment. This is useful when the
   * act of creating the environment is expensive and should only be performed
   * a single time.
   */
  final def provideManaged[R, E, L, S](
    r: Managed[Nothing, R]
  )(spec: Spec[R, E, L, ZIO[R, E, S]]): Spec[Any, E, L, ZIO[Any, E, S]] = {
    def loop(r: R)(spec: Spec[R, E, L, ZIO[R, E, S]]): ZIO[Any, E, Spec[Any, E, L, ZIO[Any, E, S]]] =
      spec.caseValue match {
        case SuiteCase(label, specs, exec) =>
          ZIO.foreach(specs)(loop(r)).map(z => Spec.suite(label, z.toVector, exec))
        case TestCase(label, test) => test.map(r => Spec.test(label, ZIO.succeed(r))).provide(r)
        case EffectCase(effect)    => effect.provide(r).flatMap(specs => loop(r)(Spec(specs)))
      }
    spec.caseValue match {
      case SuiteCase(label, specs, exec) =>
        Spec.effect(r.use(r => ZIO.foreach(specs)(loop(r))).map(z => SuiteCase(label, z.toVector, exec)))
      case c @ TestCase(_, _) => Spec.effect(r.use(r => loop(r)(Spec(c))).map(_.caseValue))
      case EffectCase(effect) =>
        Spec.effect(r.use(r => effect.provide(r).flatMap(specs => loop(r)(Spec(specs)))).map(_.caseValue))
    }
  }

  final def suite[R, E, L, T](
    label: L,
    specs: Vector[Spec[R, E, L, T]],
    exec: Option[ExecutionStrategy]
  ): Spec[R, E, L, T] =
    Spec(SuiteCase(label, specs, exec))

  final def test[L, T](label: L, test: T): Spec[Any, Nothing, L, T] =
    Spec(TestCase(label, test))
}
