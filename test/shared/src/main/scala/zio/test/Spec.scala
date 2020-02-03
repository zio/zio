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
 * A `Spec[R, E, T]` is the backbone of _ZIO Test_. Every spec is either a
 * suite, which contains other specs, or a test of type `T`. All specs require
 * an environment of type `R` and may potentially fail with an error of type
 * `E`.
 */
final case class Spec[-R, +E, +T](caseValue: SpecCase[R, E, T, Spec[R, E, T]]) { self =>

  /**
   * Syntax for adding aspects.
   * {{{
   * test("foo") { assert(42, equalTo(42)) } @@ ignore
   * }}}
   */
  final def @@[R0 <: R1, R1 <: R, E0, E1, E2 >: E0 <: E1, S0, S1, S >: S0 <: S1](
    aspect: TestAspect[R0, R1, E0, E1, S0, S1]
  )(implicit ev1: E <:< TestFailure[E2], ev2: T <:< TestSuccess[S]): ZSpec[R1, E2, S] =
    aspect(self.asInstanceOf[ZSpec[R1, E2, S]])

  /**
   * Annotates each test in this spec with the specified test annotation.
   */
  final def annotate[V](key: TestAnnotation[V], value: V): Spec[R, E, T] =
    transform[R, E, T] {
      case c @ SuiteCase(_, _, _) => c
      case TestCase(label, test, annotations) =>
        Spec.TestCase(label, test, annotations.annotate(key, value))
    }

  /**
   * Returns a new spec with the annotation map at each node.
   */
  final def annotated: Spec[R with Annotations, Annotated[E], Annotated[T]] =
    transform[R with Annotations, Annotated[E], Annotated[T]] {
      case Spec.SuiteCase(label, specs, exec) =>
        Spec.SuiteCase(label, specs.mapError((_, TestAnnotationMap.empty)), exec)
      case Spec.TestCase(label, test, annotations) =>
        Spec.TestCase(label, Annotations.withAnnotation(test), annotations)
    }

  /**
   * Returns a new spec with remapped errors and tests.
   */
  final def bimap[E1, T1](f: E => E1, g: T => T1)(implicit ev: CanFail[E]): Spec[R, E1, T1] =
    transform[R, E1, T1] {
      case SuiteCase(label, specs, exec)      => SuiteCase(label, specs.mapError(f), exec)
      case TestCase(label, test, annotations) => TestCase(label, test.bimap(f, g), annotations)
    }

  /**
   * Returns the number of tests in the spec that satisfy the specified
   * predicate.
   */
  final def countTests(f: T => Boolean): ZIO[R, E, Int] =
    fold[ZIO[R, E, Int]] {
      case SuiteCase(_, specs, _) => specs.flatMap(ZIO.collectAll(_).map(_.sum))
      case TestCase(_, test, _)   => test.map(t => if (f(t)) 1 else 0)
    }

  /**
   * Determines if any node in the spec is satisfied by the given predicate.
   */
  final def exists[R1 <: R, E1 >: E](f: SpecCase[R, E, T, Any] => ZIO[R1, E1, Boolean]): ZIO[R1, E1, Boolean] =
    fold[ZIO[R1, E1, Boolean]] {
      case c @ SuiteCase(_, specs, _) => specs.flatMap(ZIO.collectAll(_).map(_.exists(identity))).zipWith(f(c))(_ || _)
      case c @ TestCase(_, _, _)      => f(c)
    }

  /**
   * Returns a new spec with only those suites and tests satisfying the
   * specified predicate. If a suite label satisfies the predicate the entire
   * suite will be included in the new spec. Otherwise only those specs in a
   * suite that satisfy the specified predicate will be included in the new
   * spec. If no labels satisfy the specified predicate then returns `Some`
   * with an empty suite with the root label if this is a suite or `None`
   * otherwise.
   */
  final def filterLabels(f: String => Boolean): Option[Spec[R, E, T]] = {
    def loop(spec: Spec[R, E, T]): URIO[R, Option[Spec[R, E, T]]] =
      spec.caseValue match {
        case SuiteCase(label, specs, exec) =>
          if (f(label))
            ZIO.succeedNow(Some(Spec.suite(label, specs, exec)))
          else
            specs.foldCauseM(
              c => ZIO.succeedNow(Some(Spec.suite(label, ZIO.haltNow(c), exec))),
              ZIO.foreach(_)(loop).map(_.toVector.flatten).map { specs =>
                if (specs.isEmpty) None
                else Some(Spec.suite(label, ZIO.succeedNow(specs), exec))
              }
            )
        case TestCase(label, test, annotations) =>
          if (f(label)) ZIO.succeedNow(Some(Spec.test(label, test, annotations)))
          else ZIO.succeedNow(None)
      }
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        if (f(label)) Some(Spec.suite(label, specs, exec))
        else Some(Spec.suite(label, specs.flatMap(ZIO.foreach(_)(loop).map(_.toVector.flatten)), exec))
      case TestCase(label, test, annotations) =>
        if (f(label)) Some(Spec.test(label, test, annotations)) else None
    }
  }

  /**
   * Returns a new spec with only those suites and tests with tags satisfying
   * the specified predicate. If no tags satisfy the specified predicate then
   * returns `Some` with an empty suite with the root label if this is a suite
   * or `None` otherwise.
   */
  final def filterTags(f: String => Boolean): Option[Spec[R, E, T]] = {
    def loop(spec: Spec[R, E, T]): URIO[R, Option[Spec[R, E, T]]] =
      spec.caseValue match {
        case SuiteCase(label, specs, exec) =>
          specs.foldCauseM(
            c => ZIO.succeedNow(Some(Spec.suite(label, ZIO.haltNow(c), exec))),
            ZIO.foreach(_)(loop).map(_.toVector.flatten).map { specs =>
              if (specs.isEmpty) None
              else Some(Spec.suite(label, ZIO.succeedNow(specs), exec))
            }
          )
        case t @ TestCase(_, _, annotations) =>
          if (annotations.get(TestAnnotation.tagged).exists(f)) ZIO.succeedNow(Some(Spec(t)))
          else ZIO.succeedNow(None)
      }
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        Some(Spec.suite(label, specs.flatMap(ZIO.foreach(_)(loop).map(_.toVector.flatten)), exec))
      case t @ TestCase(_, _, annotations) =>
        if (annotations.get(TestAnnotation.tagged).exists(f)) Some(Spec(t))
        else None
    }
  }

  /**
   * Folds over all nodes to produce a final result.
   */
  final def fold[Z](f: SpecCase[R, E, T, Z] => Z): Z =
    caseValue match {
      case SuiteCase(label, specs, exec) => f(SuiteCase(label, specs.map(_.map(_.fold(f)).toVector), exec))
      case t @ TestCase(_, _, _)         => f(t)
    }

  /**
   * Effectfully folds over all nodes according to the execution strategy of
   * suites, utilizing the specified default for other cases.
   */
  final def foldM[R1 <: R, E1, Z](
    defExec: ExecutionStrategy
  )(f: SpecCase[R, E, T, Z] => ZIO[R1, E1, Z]): ZIO[R1, E1, Z] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        exec.getOrElse(defExec) match {
          case ExecutionStrategy.Parallel =>
            specs.foldCauseM(
              c => f(SuiteCase(label, ZIO.haltNow(c), exec)),
              ZIO
                .foreachPar(_)(_.foldM(defExec)(f))
                .flatMap(z => f(SuiteCase(label, ZIO.succeedNow(z.toVector), exec)))
            )
          case ExecutionStrategy.ParallelN(n) =>
            specs.foldCauseM(
              c => f(SuiteCase(label, ZIO.haltNow(c), exec)),
              ZIO
                .foreachParN(n)(_)(_.foldM(defExec)(f))
                .flatMap(z => f(SuiteCase(label, ZIO.succeedNow(z.toVector), exec)))
            )
          case ExecutionStrategy.Sequential =>
            specs.foldCauseM(
              c => f(SuiteCase(label, ZIO.haltNow(c), exec)),
              ZIO
                .foreach(_)(_.foldM(defExec)(f))
                .flatMap(z => f(SuiteCase(label, ZIO.succeedNow(z.toVector), exec)))
            )
        }

      case t @ TestCase(_, _, _) => f(t)
    }

  /**
   * Determines if all node in the spec are satisfied by the given predicate.
   */
  final def forall[R1 <: R, E1 >: E](f: SpecCase[R, E, T, Any] => ZIO[R1, E1, Boolean]): ZIO[R1, E1, Boolean] =
    fold[ZIO[R1, E1, Boolean]] {
      case c @ SuiteCase(_, specs, _) => specs.flatMap(ZIO.collectAll(_).map(_.forall(identity))).zipWith(f(c))(_ && _)
      case c @ TestCase(_, _, _)      => f(c)
    }

  /**
   * Iterates over the spec with the specified default execution strategy, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachExec[R1 <: R, E1, A](
    defExec: ExecutionStrategy
  )(failure: Cause[E] => ZIO[R1, E1, A], success: T => ZIO[R1, E1, A]): ZIO[R1, Nothing, Spec[R1, E1, A]] =
    foldM[R1, Nothing, Spec[R1, E1, A]](defExec) {
      case SuiteCase(label, specs, exec) =>
        specs.foldCause(
          e => Spec.test(label, failure(e), TestAnnotationMap.empty),
          t => Spec.suite(label, ZIO.succeedNow(t), exec)
        )
      case TestCase(label, test, annotations) =>
        test.foldCause(e => Spec.test(label, failure(e), annotations), t => Spec.test(label, success(t), annotations))
    }

  /**
   * Iterates over the spec with the sequential strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreach[R1 <: R, E1, A](
    failure: Cause[E] => ZIO[R1, E1, A],
    success: T => ZIO[R1, E1, A]
  ): ZIO[R1, Nothing, Spec[R1, E1, A]] =
    foreachExec(ExecutionStrategy.Sequential)(failure, success)

  /**
   * Iterates over the spec with the parallel strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachPar[R1 <: R, E1, A](
    failure: Cause[E] => ZIO[R1, E1, A],
    success: T => ZIO[R1, E1, A]
  ): ZIO[R1, Nothing, Spec[R1, E1, A]] =
    foreachExec(ExecutionStrategy.Parallel)(failure, success)

  /**
   * Iterates over the spec with the parallel (`n`) strategy as the default, and
   * effectfully transforming every test with the provided function, finally
   * reconstructing the spec with the same structure.
   */
  final def foreachParN[R1 <: R, E1, A](
    n: Int
  )(failure: Cause[E] => ZIO[R1, E1, A], success: T => ZIO[R1, E1, A]): ZIO[R1, Nothing, Spec[R1, E1, A]] =
    foreachExec(ExecutionStrategy.ParallelN(n))(failure, success)

  /**
   * Returns a new spec with remapped errors.
   */
  final def mapError[E1](f: E => E1)(implicit ev: CanFail[E]): Spec[R, E1, T] =
    transform[R, E1, T] {
      case SuiteCase(label, specs, exec)      => SuiteCase(label, specs.mapError(f), exec)
      case TestCase(label, test, annotations) => TestCase(label, test.mapError(f), annotations)
    }

  /**
   * Returns a new spec with remapped labels.
   */
  final def mapLabel(f: String => String): Spec[R, E, T] =
    transform[R, E, T] {
      case SuiteCase(label, specs, exec)      => SuiteCase(f(label), specs, exec)
      case TestCase(label, test, annotations) => TestCase(f(label), test, annotations)
    }

  /**
   * Returns a new spec with remapped tests.
   */
  final def mapTest[T1](f: T => T1): Spec[R, E, T1] =
    transform[R, E, T1] {
      case SuiteCase(label, specs, exec)      => SuiteCase(label, specs, exec)
      case TestCase(label, test, annotations) => TestCase(label, test.map(f), annotations)
    }

  /**
   * Provides each test in this spec with its required environment
   */
  final def provide(r: R)(implicit ev: NeedsEnv[R]): Spec[Any, E, T] =
    provideM(ZIO.succeedNow(r))

  /**
   * Provides a layer to the spec, translating it up a level.
   */
  final def provideLayer[E1 >: E, R0, R1 <: Has[_]](
    layer: ZLayer[R0, E1, R1]
  )(implicit ev: R1 <:< R): Spec[R0, E1, T] =
    self.provideSomeManaged(for {
      r0 <- ZManaged.environment[R0]
      r1 <- layer.value.provide(r0)
    } yield ev(r1))

  /**
   * Provides a layer to the spec, sharing services between all tests.
   */
  final def provideLayerShared[E1 >: E, R0, R1 <: Has[_]](
    layer: ZLayer[R0, E1, R1]
  )(implicit ev: R1 <:< R): Spec[R0, E1, T] =
    self.provideSomeManagedShared(for {
      r0 <- ZManaged.environment[R0]
      r1 <- layer.value.provide(r0)
    } yield ev(r1))

  /**
   * Uses the specified effect to provide each test in this spec with its
   * required environment.
   */
  final def provideM[E1 >: E](zio: ZIO[Any, E1, R])(implicit ev: NeedsEnv[R]): Spec[Any, E1, T] =
    provideManaged(zio.toManaged_)

  /**
   * Uses the specified effect once to provide all tests in this spec with a
   * shared version of their required environment.
   */
  final def provideMShared[E1 >: E](zio: ZIO[Any, E1, R])(implicit ev: NeedsEnv[R]): Spec[Any, E1, T] =
    provideManagedShared(zio.toManaged_)

  /**
   * Uses the specified `Managed` to provide each test in this spec with its
   * required environment.
   */
  final def provideManaged[E1 >: E](managed: Managed[E1, R])(implicit ev: NeedsEnv[R]): Spec[Any, E1, T] =
    provideSomeManaged(managed)

  /**
   * Uses the specified `Managed` once to provide all tests in this spec with
   * a shared version of their required environment.
   */
  final def provideManagedShared[E1 >: E](managed: Managed[E1, R])(implicit ev: NeedsEnv[R]): Spec[Any, E1, T] =
    provideSomeManagedShared(managed)

  /**
   * Uses the specified function to provide each test in this spec with part of
   * its required environment.
   */
  final def provideSome[R0](f: R0 => R)(implicit ev: NeedsEnv[R]): Spec[R0, E, T] =
    provideSomeM(ZIO.fromFunction(f))

  /**
   * Uses the specified effect to provide each test in this spec with part of
   * its required environment.
   */
  final def provideSomeM[R0, E1 >: E](zio: ZIO[R0, E1, R])(implicit ev: NeedsEnv[R]): Spec[R0, E1, T] =
    provideSomeManaged(zio.toManaged_)

  /**
   * Uses the specified effect once to provide all tests in this spec with a
   * shared version of part of their required environment.
   */
  final def provideSomeMShared[R0, E1 >: E](zio: ZIO[R0, E1, R])(implicit ev: NeedsEnv[R]): Spec[R0, E1, T] =
    provideSomeManagedShared(zio.toManaged_)

  /**
   * Uses the specified `ZManaged` to provide each test in this spec with part
   * of its required environment.
   */
  final def provideSomeManaged[R0, E1 >: E](
    managed: ZManaged[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): Spec[R0, E1, T] =
    transform[R0, E1, T] {
      case SuiteCase(label, specs, exec)      => SuiteCase(label, specs.provideSomeManaged(managed), exec)
      case TestCase(label, test, annotations) => TestCase(label, test.provideSomeManaged(managed), annotations)
    }

  /**
   * Uses the specified `ZManaged` once to provide all tests in this spec with
   * a shared version of part of their required environment.
   */
  final def provideSomeManagedShared[R0, E1 >: E](
    managed: ZManaged[R0, E1, R]
  )(implicit ev: NeedsEnv[R]): Spec[R0, E1, T] = {
    def loop(r: R)(spec: Spec[R, E, T]): UIO[Spec[Any, E, T]] =
      spec.caseValue match {
        case SuiteCase(label, specs, exec) =>
          specs.provide(r).run.map { result =>
            Spec.suite(label, ZIO.doneNow(result).flatMap(ZIO.foreach(_)(loop(r))).map(_.toVector), exec)
          }
        case TestCase(label, test, annotations) =>
          test.provide(r).run.map(result => Spec.test(label, ZIO.doneNow(result), annotations))
      }
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        Spec.suite(label, managed.use(r => specs.flatMap(ZIO.foreach(_)(loop(r))).map(_.toVector).provide(r)), exec)
      case TestCase(label, test, annotations) =>
        Spec.test(label, test.provideSomeManaged(managed), annotations)
    }
  }

  /**
   * Uses the specified function once to provide all tests in this spec with a
   * shared version of part of their required environment.
   */
  final def provideSomeShared[R0](f: R0 => R)(implicit ev: NeedsEnv[R]): Spec[R0, E, T] =
    provideSomeMShared(ZIO.fromFunction(f))

  /**
   * Computes the size of the spec, i.e. the number of tests in the spec.
   */
  final def size: ZIO[R, E, Int] =
    fold[ZIO[R, E, Int]] {
      case SuiteCase(_, counts, _) => counts.flatMap(ZIO.collectAll(_).map(_.sum))
      case TestCase(_, _, _)       => ZIO.succeedNow(1)
    }

  /**
   * Transforms the spec one layer at a time.
   */
  final def transform[R1, E1, T1](
    f: SpecCase[R, E, T, Spec[R1, E1, T1]] => SpecCase[R1, E1, T1, Spec[R1, E1, T1]]
  ): Spec[R1, E1, T1] =
    caseValue match {
      case SuiteCase(label, specs, exec) => Spec(f(SuiteCase(label, specs.map(_.map(_.transform(f))), exec)))
      case t @ TestCase(_, _, _)         => Spec(f(t))
    }

  /**
   * Transforms the spec statefully, one layer at a time.
   */
  final def transformAccum[R1, E1, T1, Z](
    z0: Z
  )(
    f: (Z, SpecCase[R, E, T, Spec[R1, E1, T1]]) => (Z, SpecCase[R1, E1, T1, Spec[R1, E1, T1]])
  ): ZIO[R, E, (Z, Spec[R1, E1, T1])] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        for {
          specs <- specs
          result <- ZIO.foldLeft(specs)(z0 -> Vector.empty[Spec[R1, E1, T1]]) {
                     case ((z, vector), spec) =>
                       spec.transformAccum(z)(f).map { case (z1, spec1) => z1 -> (vector :+ spec1) }
                   }
          (z, specs1)     = result
          res             = f(z, SuiteCase(label, ZIO.succeedNow(specs1), exec))
          (z1, caseValue) = res
        } yield z1 -> Spec(caseValue)
      case t @ TestCase(_, _, _) =>
        val (z, caseValue) = f(z0, t)
        ZIO.succeedNow(z -> Spec(caseValue))
    }

  /**
   * Runs only tests whose labels (which must be strings) contain the given substring.
   * If a suite label contains the specified string all specs in that suite will be included in the resulting spec.
   */
  final def only[S, E1](
    s: String
  )(implicit ev1: E <:< TestFailure[E1], ev2: T <:< TestSuccess[S]): ZSpec[R, E1, S] =
    self
      .asInstanceOf[ZSpec[R, E1, S]]
      .filterLabels(_.contains(s))
      .getOrElse(Spec.test("only", ignored, TestAnnotationMap.empty))

  /**
   * Runs the spec only if the specified predicate is satisfied.
   */
  final def when[S](b: Boolean)(implicit ev: T <:< TestSuccess[S]): Spec[R with Annotations, E, TestSuccess[S]] =
    whenM(ZIO.succeedNow(b))

  /**
   * Runs the spec only if the specified effectual predicate is satisfied.
   */
  final def whenM[R1 <: R, E1 >: E, S](
    b: ZIO[R1, E1, Boolean]
  )(implicit ev: T <:< TestSuccess[S]): Spec[R1 with Annotations, E1, TestSuccess[S]] =
    caseValue match {
      case SuiteCase(label, specs, exec) =>
        Spec.suite(
          label,
          b.flatMap(b =>
            if (b) specs.asInstanceOf[ZIO[R1, E1, Vector[Spec[R1, E1, TestSuccess[S]]]]]
            else ZIO.succeedNow(Vector.empty)
          ),
          exec
        )
      case TestCase(label, test, annotations) =>
        Spec.test(
          label,
          b.flatMap(b =>
            if (b) test.asInstanceOf[ZIO[R1, E1, TestSuccess[S]]]
            else Annotations.annotate(TestAnnotation.ignored, 1).as(TestSuccess.Ignored)
          ),
          annotations
        )
    }
}

object Spec {
  sealed trait SpecCase[-R, +E, +T, +A] { self =>
    final def map[B](f: A => B): SpecCase[R, E, T, B] = self match {
      case SuiteCase(label, specs, exec)      => SuiteCase(label, specs.map(_.map(f)), exec)
      case TestCase(label, test, annotations) => TestCase(label, test, annotations)
    }
  }
  final case class SuiteCase[-R, +E, +A](label: String, specs: ZIO[R, E, Vector[A]], exec: Option[ExecutionStrategy])
      extends SpecCase[R, E, Nothing, A]
  final case class TestCase[-R, +E, +T](label: String, test: ZIO[R, E, T], annotations: TestAnnotationMap)
      extends SpecCase[R, E, T, Nothing]

  final def suite[R, E, T](
    label: String,
    specs: ZIO[R, E, Vector[Spec[R, E, T]]],
    exec: Option[ExecutionStrategy]
  ): Spec[R, E, T] =
    Spec(SuiteCase(label, specs, exec))

  final def test[R, E, T](label: String, test: ZIO[R, E, T], annotations: TestAnnotationMap): Spec[R, E, T] =
    Spec(TestCase(label, test, annotations))
}
