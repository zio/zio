/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
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

import Spec._

import zio._

/**
 * A `Spec[R, E, L, T]` is the backbone of _ZIO Test_. Every spec is either a
 * suite, which contains other specs, or a test of type `T`. All specs are
 * annotated with labels of type `L`, require an environment of type `R` and
 * may potentially fail with an error of type `E`.
 */
final case class Spec[-R, +E, +L, +T](caseValue: SpecCase[R, E, L, T, Spec[R, E, L, T]]) { self =>

  /**
   * Syntax for adding aspects.
   * {{{
   * test("foo") { assert(42, equalTo(42)) } @@ ignore
   * }}}
   */
  final def @@[R0 <: R1, R1 <: R, E0, E1, E2 >: E0 <: E1, S0, S1, S >: S0 <: S1](
    aspect: TestAspect[R0, R1, E0, E1, S0, S1]
  )(implicit ev1: E <:< TestFailure[E2], ev2: T <:< TestSuccess[S]): ZSpec[R1, E2, L, S] =
    aspect(self.asInstanceOf[ZSpec[R1, E2, L, S]])

  /**
   * Returns a new spec with the annotation map at each node.
   */
  final def annotated: Spec[R with Annotations, Annotated[E], L, Annotated[T]] =
    transform[R with Annotations, Annotated[E], L, Annotated[T]] {
      case Spec.SuiteCase(label, specs, exec) =>
        Spec.SuiteCase(label, specs.mapError((_, TestAnnotationMap.empty)), exec)
      case Spec.TestCase(label, test) =>
        Spec.TestCase(label, Annotations.withAnnotation(test))
    }

  /**
   * Returns a new spec with remapped errors and tests.
   */
  final def bimap[E1, T1](f: E => E1, g: T => T1)(implicit ev: CanFail[E]): Spec[R, E1, L, T1] =
    transform[R, E1, L, T1] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.mapError(f), exec)
      case TestCase(label, test)         => TestCase(label, test.bimap(f, g))
    }

  /**
   * Returns the number of tests in the spec that satisfy the specified
   * predicate.
   */
  final def countTests(f: T => Boolean): ZIO[R, E, Int] =
    fold[ZIO[R, E, Int]] {
      case SuiteCase(_, specs, _) => specs.flatMap(ZIO.collectAll(_).map(_.sum))
      case TestCase(_, test)      => test.map(t => if (f(t)) 1 else 0)
    }

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
  final def exists[R1 <: R, E1 >: E](f: SpecCase[R, E, L, T, Any] => ZIO[R1, E1, Boolean]): ZIO[R1, E1, Boolean] =
    fold[ZIO[R1, E1, Boolean]] {
      case c @ SuiteCase(_, specs, _) => specs.flatMap(ZIO.collectAll(_).map(_.exists(identity))).zipWith(f(c))(_ || _)
      case c @ TestCase(_, _)         => f(c)
    }

  /**
   * Returns a new Spec containing only tests with labels satisfying the specified predicate.
   */
  final def filterTestLabels(f: L => Boolean): Option[Spec[R, E, L, T]] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        val filtered = SuiteCase(label, specs.map(_.flatMap(_.filterTestLabels(f))), exec)
        Some(Spec(filtered))

      case t @ TestCase(label, _) =>
        if (f(label)) Some(Spec(t)) else None
    }

  /**
   * Returns a new Spec containing only tests/suites with labels satisfying the specified predicate.
   */
  final def filterLabels(f: L => Boolean): Option[Spec[R, E, L, T]] =
    caseValue match {
      case s @ SuiteCase(label, specs, exec) =>
        // If the suite matched the label, no need to filter anything underneath it.
        if (f(label)) {
          Some(Spec(s))
        } else {
          val filtered = SuiteCase(label, specs.map(_.flatMap(_.filterLabels(f))), exec)
          Some(Spec(filtered))
        }

      case t @ TestCase(label, _) =>
        if (f(label)) Some(Spec(t)) else None
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
            specs.foldCauseM(
              c => f(SuiteCase(label, ZIO.halt(c), exec)),
              ZIO
                .foreachPar(_)(_.foldM(defExec)(f))
                .flatMap(z => f(SuiteCase(label, ZIO.succeed(z.toVector), exec)))
            )
          case ExecutionStrategy.ParallelN(n) =>
            specs.foldCauseM(
              c => f(SuiteCase(label, ZIO.halt(c), exec)),
              ZIO
                .foreachParN(n)(_)(_.foldM(defExec)(f))
                .flatMap(z => f(SuiteCase(label, ZIO.succeed(z.toVector), exec)))
            )
          case ExecutionStrategy.Sequential =>
            specs.foldCauseM(
              c => f(SuiteCase(label, ZIO.halt(c), exec)),
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
  final def forall[R1 <: R, E1 >: E](f: SpecCase[R, E, L, T, Any] => ZIO[R1, E1, Boolean]): ZIO[R1, E1, Boolean] =
    fold[ZIO[R1, E1, Boolean]] {
      case c @ SuiteCase(_, specs, _) => specs.flatMap(ZIO.collectAll(_).map(_.forall(identity))).zipWith(f(c))(_ && _)
      case c @ TestCase(_, _)         => f(c)
    }

  /**
   * Iterates over the spec with the specified default execution strategy, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachExec[R1 <: R, E1, A](
    defExec: ExecutionStrategy
  )(failure: Cause[E] => ZIO[R1, E1, A], success: T => ZIO[R1, E1, A]): ZIO[R1, Nothing, Spec[R1, E1, L, A]] =
    foldM[R1, Nothing, Spec[R1, E1, L, A]](defExec) {
      case SuiteCase(label, specs, exec) =>
        specs.foldCause(e => Spec.test(label, failure(e)), t => Spec.suite(label, ZIO.succeed(t), exec))
      case TestCase(label, test) =>
        test.foldCause(e => Spec.test(label, failure(e)), t => Spec.test(label, success(t)))
    }

  /**
   * Iterates over the spec with the sequential strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreach[R1 <: R, E1, A](
    failure: Cause[E] => ZIO[R1, E1, A],
    success: T => ZIO[R1, E1, A]
  ): ZIO[R1, Nothing, Spec[R1, E1, L, A]] =
    foreachExec(ExecutionStrategy.Sequential)(failure, success)

  /**
   * Iterates over the spec with the parallel strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachPar[R1 <: R, E1, A](
    failure: Cause[E] => ZIO[R1, E1, A],
    success: T => ZIO[R1, E1, A]
  ): ZIO[R1, Nothing, Spec[R1, E1, L, A]] =
    foreachExec(ExecutionStrategy.Parallel)(failure, success)

  /**
   * Iterates over the spec with the parallel (`n`) strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachParN[R1 <: R, E1, A](
    n: Int
  )(failure: Cause[E] => ZIO[R1, E1, A], success: T => ZIO[R1, E1, A]): ZIO[R1, Nothing, Spec[R1, E1, L, A]] =
    foreachExec(ExecutionStrategy.ParallelN(n))(failure, success)

  /**
   * Returns a new spec with remapped errors.
   */
  final def mapError[E1](f: E => E1)(implicit ev: CanFail[E]): Spec[R, E1, L, T] =
    transform[R, E1, L, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.mapError(f), exec)
      case TestCase(label, test)         => TestCase(label, test.mapError(f))
    }

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
      case TestCase(label, test)         => TestCase(label, test.map(f))
    }

  /**
   * Provides each test in this spec with its required environment
   */
  final def provide(r: R)(implicit ev: NeedsEnv[R]): Spec[Any, E, L, T] =
    provideM(ZIO.succeed(r))

  /**
   * Provides a layer to the spec, translating it up a level.
   */
  final def provideLayer[E1 >: E, R0, R1 <: Has[_]](
    layer: ZLayer[R0, E1, R1]
  )(implicit ev: R1 <:< R): Spec[R0, E1, L, T] =
    self.provideSomeManaged(for {
      r0 <- ZManaged.environment[R0]
      r1 <- layer.value.provide(r0)
    } yield ev(r1))

  /**
   * Provides a layer to the spec, sharing services between all tests.
   */
  final def provideLayerShared[E1 >: E, R0, R1 <: Has[_]](
    layer: ZLayer[R0, E1, R1]
  )(implicit ev: R1 <:< R): Spec[R0, E1, L, T] =
    self.provideSomeManagedShared(for {
      r0 <- ZManaged.environment[R0]
      r1 <- layer.value.provide(r0)
    } yield ev(r1))

  /**
   * Uses the specified effect to provide each test in this spec with its
   * required environment.
   */
  final def provideM[E1 >: E](zio: ZIO[Any, E1, R])(implicit ev: NeedsEnv[R]): Spec[Any, E1, L, T] =
    provideManaged(zio.toManaged_)

  /**
   * Uses the specified effect once to provide all tests in this spec with a
   * shared version of their required environment.
   */
  final def provideMShared[E1 >: E](zio: ZIO[Any, E1, R])(implicit ev: NeedsEnv[R]): Spec[Any, E1, L, T] =
    provideManagedShared(zio.toManaged_)

  /**
   * Uses the specified `Managed` to provide each test in this spec with its
   * required environment.
   */
  final def provideManaged[E1 >: E](managed: Managed[E1, R])(implicit ev: NeedsEnv[R]): Spec[Any, E1, L, T] =
    provideSomeManaged(managed)

  /**
   * Uses the specified `Managed` once to provide all tests in this spec with
   * a shared version of their required environment.
   */
  final def provideManagedShared[E1 >: E](managed: Managed[E1, R])(implicit ev: NeedsEnv[R]): Spec[Any, E1, L, T] =
    provideSomeManagedShared(managed)

  /**
   * Uses the specified function to provide each test in this spec with part of
   * its required environment.
   */
  final def provideSome[R0](f: R0 => R)(implicit ev: NeedsEnv[R]): Spec[R0, E, L, T] =
    provideSomeM(ZIO.fromFunction(f))

  /**
   * Uses the specified effect to provide each test in this spec with part of
   * its required environment.
   */
  final def provideSomeM[R0, E1 >: E](zio: ZIO[R0, E1, R])(implicit ev: NeedsEnv[R]): Spec[R0, E1, L, T] =
    provideSomeManaged(zio.toManaged_)

  /**
   * Uses the specified effect once to provide all tests in this spec with a
   * shared version of part of their required environment.
   */
  final def provideSomeMShared[R0, E1 >: E](zio: ZIO[R0, E1, R])(implicit ev: NeedsEnv[R]): Spec[R0, E1, L, T] =
    provideSomeManagedShared(zio.toManaged_)

  /**
   * Uses the specified `ZManaged` to provide each test in this spec with part
   * of its required environment.
   */
  final def provideSomeManaged[R0, E1 >: E](
    managed: ZManaged[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): Spec[R0, E1, L, T] =
    transform[R0, E1, L, T] {
      case SuiteCase(label, specs, exec) => SuiteCase(label, specs.provideSomeManaged(managed), exec)
      case TestCase(label, test)         => TestCase(label, test.provideSomeManaged(managed))
    }

  /**
   * Uses the specified `ZManaged` once to provide all tests in this spec with
   * a shared version of part of their required environment.
   */
  final def provideSomeManagedShared[R0, E1 >: E](
    managed: ZManaged[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): Spec[R0, E1, L, T] = {
    def loop(r: R)(spec: Spec[R, E, L, T]): UIO[Spec[Any, E, L, T]] =
      spec.caseValue match {
        case SuiteCase(label, specs, exec) =>
          specs.provide(r).run.map { result =>
            Spec.suite(label, ZIO.done(result).flatMap(ZIO.foreach(_)(loop(r))).map(_.toVector), exec)
          }
        case TestCase(label, test) =>
          test.provide(r).run.map(result => Spec.test(label, ZIO.done(result)))
      }
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        Spec.suite(label, managed.use(r => specs.flatMap(ZIO.foreach(_)(loop(r))).map(_.toVector).provide(r)), exec)
      case TestCase(label, test) =>
        Spec.test(label, test.provideSomeManaged(managed))
    }
  }

  /**
   * Uses the specified function once to provide all tests in this spec with a
   * shared version of part of their required environment.
   */
  final def provideSomeShared[R0](f: R0 => R)(implicit ev: NeedsEnv[R]): Spec[R0, E, L, T] =
    provideSomeMShared(ZIO.fromFunction(f))

  /**
   * Computes the size of the spec, i.e. the number of tests in the spec.
   */
  final def size: ZIO[R, E, Int] =
    fold[ZIO[R, E, Int]] {
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
    f: (Z, SpecCase[R, E, L, T, Spec[R1, E1, L1, T1]]) => (Z, SpecCase[R1, E1, L1, T1, Spec[R1, E1, L1, T1]])
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

  /**
   * Runs only tests whose labels (which must be strings) contain the given substring.
   * If a suite label contains the specified string all specs in that suite will be included in the resulting spec.
   */
  final def only[S, E1](
    s: String
  )(implicit ev1: L <:< String, ev2: E <:< TestFailure[E1], ev3: T <:< TestSuccess[S]): ZSpec[R, E1, String, S] =
    self
      .asInstanceOf[ZSpec[R, E1, String, S]]
      .filterLabels(_.contains(s))
      .getOrElse(Spec.test("only", ignored))

  /**
   * Runs the spec only if the specified predicate is satisfied.
   */
  final def when[S](b: Boolean)(implicit ev: T <:< TestSuccess[S]): Spec[R with Annotations, E, L, TestSuccess[S]] =
    whenM(ZIO.succeed(b))

  /**
   * Runs the spec only if the specified effectual predicate is satisfied.
   */
  final def whenM[R1 <: R, E1 >: E, S](
    b: ZIO[R1, E1, Boolean]
  )(implicit ev: T <:< TestSuccess[S]): Spec[R1 with Annotations, E1, L, TestSuccess[S]] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        Spec.suite(
          label,
          b.flatMap(
            b =>
              if (b) specs.asInstanceOf[ZIO[R1, E1, Vector[Spec[R1, E1, L, TestSuccess[S]]]]]
              else ZIO.succeed(Vector.empty)
          ),
          exec
        )
      case TestCase(label, test) =>
        Spec.test(
          label,
          b.flatMap(
            b =>
              if (b) test.asInstanceOf[ZIO[R1, E1, TestSuccess[S]]]
              else Annotations.annotate(TestAnnotation.ignored, 1).as(TestSuccess.Ignored)
          )
        )
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
  final case class TestCase[-R, +E, +L, +T](label: L, test: ZIO[R, E, T]) extends SpecCase[R, E, L, T, Nothing]

  final def suite[R, E, L, T](
    label: L,
    specs: ZIO[R, E, Vector[Spec[R, E, L, T]]],
    exec: Option[ExecutionStrategy]
  ): Spec[R, E, L, T] =
    Spec(SuiteCase(label, specs, exec))

  final def test[R, E, L, T](label: L, test: ZIO[R, E, T]): Spec[R, E, L, T] =
    Spec(TestCase(label, test))
}
