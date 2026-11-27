/*
 * Copyright 2019-2024 John A. De Goes and the ZIO Contributors
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

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.test.Spec._

/**
 * A `Spec[R, E]` is a tree of tests. Each node is a test, a labeled suite of
 * other specs, a group of specs, a node that sets the execution strategy of
 * the specs inside it, or a scoped node that acquires resources for them. `R`
 * is the environment that the tests need and `E` is the error type with which
 * they can fail.
 *
 * The `suite` and `test` constructors build specs, `@@` applies a
 * [[TestAspect]], and the `provide` methods supply the environment.
 *
 * {{{
 * import zio.ZIO
 *
 * val spec: Spec[Any, Nothing] =
 *   suite("arithmetic")(
 *     test("addition") {
 *       assertTrue(1 + 1 == 2)
 *     },
 *     test("an effect") {
 *       ZIO.succeed(6 * 7).map(n => assertTrue(n == 42))
 *     }
 *   ) @@ TestAspect.sequential
 * }}}
 *
 * @see [[https://zio.dev/reference/test/spec/ Spec]]
 */
final case class Spec[-R, +E](caseValue: SpecCase[R, E, Spec[R, E]]) extends SpecVersionSpecific[R, E] {
  self =>

  /**
   * Combines this spec and `that` into one unlabeled group that holds both.
   * When either side is already an unlabeled group, its members are added to
   * the new group one by one instead of as a nested group.
   */
  def +[R1 <: R, E1 >: E](that: Spec[R1, E1]): Spec[R1, E1] =
    (self.caseValue, that.caseValue) match {
      case (MultipleCase(self), MultipleCase(that)) => Spec.multiple(self ++ that)
      case (MultipleCase(self), _)                  => Spec.multiple(self :+ that)
      case (_, MultipleCase(that))                  => Spec.multiple(self +: that)
      case _                                        => Spec.multiple(Chunk(self, that))
    }

  /**
   * Applies the test aspect `aspect` to this spec and returns the result. In
   * `spec @@ a @@ b`, the aspect `a` is applied first and `b` is applied to
   * the result.
   *
   * {{{
   * val ignored: Spec[Any, Nothing] =
   *   test("answer") {
   *     assert(42)(Assertion.equalTo(42))
   *   } @@ TestAspect.ignore
   * }}}
   *
   * @see [[https://zio.dev/reference/test/aspects/ Test Aspects]]
   */
  final def @@[R0 <: R1, R1 <: R, E0 >: E, E1 >: E0](aspect: TestAspect[R0, R1, E0, E1])(implicit
    trace: Trace
  ): Spec[R1, E0] =
    aspect(self)

  /**
   * Adds `value` to the annotation `key` of each test in this spec, combining
   * it with the value the test already has with the annotation's `combine`
   * function.
   */
  final def annotate[V](key: TestAnnotation[V], value: V)(implicit trace: Trace): Spec[R, E] =
    transform[R, E] {
      case TestCase(test, annotations) => TestCase(test, annotations.annotate(key, value))
      case c                           => c
    }

  /**
   * Returns a spec whose tests, when they finish, add the annotations recorded
   * so far through the current test's [[Annotations]] service to the
   * annotations of their result, whether the test passes or fails.
   */
  final def annotated(implicit trace: Trace): Spec[R, E] =
    transform[R, E] {
      case TestCase(test, annotations) => TestCase(Annotations.withAnnotation(test), annotations)
      case c                           => c
    }

  /**
   * Returns an effect that runs every test of this spec with the environment
   * it requires, and returns a spec of the same shape whose tests hold the
   * results. Suites that do not set their own execution strategy use
   * `defExec`.
   */
  final def execute(defExec: ExecutionStrategy)(implicit
    trace: Trace
  ): ZIO[R with Scope, Nothing, Spec[Any, E]] =
    ZIO.environmentWithZIO(provideEnvironment(_).foreachExec(defExec)(Exit.failCause, ZIO.successFn))

  /**
   * Returns a new spec with only those tests whose value for the annotation
   * `key` satisfies `f`. Returns `None` when no test matches. A scoped node
   * (one that acquires resources, as `provideLayerShared` and `aroundAll`
   * create) is kept, and is left with no tests when none of its tests match.
   */
  final def filterAnnotations[V](
    key: TestAnnotation[V]
  )(f: V => Boolean)(implicit trace: Trace): Option[Spec[R, E]] =
    caseValue match {
      case ExecCase(exec, spec)     => spec.filterAnnotations(key)(f).map(Spec.exec(exec, _))
      case LabeledCase(label, spec) => spec.filterAnnotations(key)(f).map(Spec.labeled(label, _))
      case ScopedCase(scoped)       => Some(Spec.scoped[R](scoped.map(_.filterAnnotations(key)(f).getOrElse(Spec.empty))))
      case MultipleCase(specs) =>
        val filtered = specs.flatMap(_.filterAnnotations(key)(f))
        if (filtered.isEmpty) None else Some(Spec.multiple(filtered))
      case t: TestCase[R, E] => if (f(t.annotations.get(key))) Some(self) else None
    }

  /**
   * Returns a new spec with only those suites and tests satisfying the
   * specified predicate. If a suite label satisfies the predicate the entire
   * suite will be included in the new spec. Otherwise only those specs in a
   * suite that satisfy the specified predicate will be included in the new
   * spec. Returns `None` when no label matches. A scoped node (one that
   * acquires resources, as `provideLayerShared` and `aroundAll` create) is
   * kept, and is left with no tests when none of its labels match.
   */
  final def filterLabels(f: String => Boolean)(implicit trace: Trace): Option[Spec[R, E]] =
    filterLabels(f, prefix = "", accumulatePrefix = false)

  /**
   * Filters the spec by label as the one-argument `filterLabels` does. When
   * `accumulatePrefix` is `true`, `f` sees each label joined to the labels of
   * its enclosing suites with `" - "`, starting from `prefix`; when it is
   * `false`, `f` sees each label on its own.
   */
  final def filterLabels(f: String => Boolean, prefix: String, accumulatePrefix: Boolean)(implicit
    trace: Trace
  ): Option[Spec[R, E]] =
    caseValue match {
      case ExecCase(exec, spec) =>
        spec.filterLabels(f, prefix, accumulatePrefix).map(Spec.exec(exec, _))
      case LabeledCase(label, spec) =>
        val prefixedLabel: String = if (prefix.isEmpty || !accumulatePrefix) label else s"$prefix - $label"
        if (f(prefixedLabel)) Some(self)
        else spec.filterLabels(f, prefixedLabel, accumulatePrefix).map(Spec.labeled(label, _))
      case ScopedCase(scoped) =>
        Some(Spec.scoped[R](scoped.map(_.filterLabels(f, prefix, accumulatePrefix).getOrElse(Spec.empty))))
      case MultipleCase(specs) =>
        val filtered = specs.flatMap(_.filterLabels(f, prefix, accumulatePrefix))
        if (filtered.isEmpty) None else Some(Spec.multiple(filtered))
      case _: TestCase[R, E] => None
    }

  /**
   * Returns a new spec with only those tests that have at least one tag (set
   * with `TestAspect.tag`) satisfying `f`. Returns `None` when no test
   * matches. A scoped node (one that acquires resources, as
   * `provideLayerShared` and `aroundAll` create) is kept, and is left with no
   * tests when none of its tests match.
   */
  final def filterTags(f: String => Boolean)(implicit trace: Trace): Option[Spec[R, E]] =
    filterAnnotations(TestAnnotation.tagged)(_.exists(f))

  /**
   * Returns a new spec without the tests that have at least one tag (set with
   * `TestAspect.tag`) satisfying `f`. Returns `None` when every test is
   * removed. A scoped node (one that acquires resources, as
   * `provideLayerShared` and `aroundAll` create) is kept, and is left with no
   * tests when all of its tests are removed.
   */
  final def filterNotTags(f: String => Boolean)(implicit trace: Trace): Option[Spec[R, E]] =
    filterAnnotations(TestAnnotation.tagged)(!_.exists(f))

  /**
   * Folds the spec bottom-up with the effectful function `f`, which receives
   * each node with its children already folded. The members of a group are
   * folded with the execution strategy of the nearest enclosing `Spec.exec`
   * node (which `TestAspect.sequential` and `TestAspect.parallel` add), or
   * with `defExec` when there is none. A scoped node whose effect fails is
   * passed to `f` with that failure.
   */
  final def foldScoped[R1 <: R, E1, Z](
    defExec: ExecutionStrategy
  )(f: SpecCase[R, E, Z] => ZIO[R1 with Scope, E1, Z])(implicit trace: Trace): ZIO[R1 with Scope, E1, Z] =
    caseValue match {
      case ExecCase(exec, spec)     => spec.foldScoped[R1, E1, Z](exec)(f).flatMap(z => f(ExecCase(exec, z)))
      case LabeledCase(label, spec) => spec.foldScoped[R1, E1, Z](defExec)(f).flatMap(z => f(LabeledCase(label, z)))
      case ScopedCase(scoped) =>
        scoped.foldCauseZIO(
          failure = c => f(ScopedCase(Exit.failCause(c))),
          success = spec => spec.foldScoped[R1, E1, Z](defExec)(f).flatMap(z => f(ScopedCase(ZIO.succeed(z))))
        )
      case MultipleCase(specs) =>
        ZIO
          .foreachExec(specs)(defExec)(spec => ZIO.scoped[R1](spec.foldScoped[R1, E1, Z](defExec)(f)))
          .flatMap(zs => f(MultipleCase(zs)))
      case t: TestCase[R, E] => f(t)
    }

  /**
   * Runs every test of this spec and passes each result to `failure` or
   * `success`, returning a spec of the same shape whose tests are the effects
   * those functions return. A scoped node whose effect fails becomes a test
   * built from `failure`. Groups that have no execution strategy of their own
   * run their members with `defExec`.
   */
  final def foreachExec[R1 <: R, E1](
    defExec: ExecutionStrategy
  )(
    failure: Cause[TestFailure[E]] => ZIO[R1, TestFailure[E1], TestSuccess],
    success: TestSuccess => ZIO[R1, E1, TestSuccess]
  )(implicit
    trace: Trace
  ): ZIO[R1 with Scope, Nothing, Spec[R1, E1]] =
    foldScoped[R1, Nothing, Spec[R1, E1]](defExec) {
      case exec: ExecCase[?]         => ZIO.succeed(Spec(exec).asInstanceOf[Spec[R1, E1]])
      case labelCase: LabeledCase[?] => ZIO.succeed(Spec(labelCase).asInstanceOf[Spec[R1, E1]])
      case ScopedCase(scoped) =>
        scoped.foldCause(
          failure = c => Spec.test(failure(c), TestAnnotationMap.empty),
          success = t => Spec.scoped(ZIO.succeed(t))
        )
      case multiple: MultipleCase[?] => ZIO.succeed(Spec(multiple).asInstanceOf[Spec[R1, E1]])
      case TestCase(test, annotations) =>
        test
          .foldCause(
            failure = e => Spec.test(failure(e), annotations),
            success = t => Spec.test(success(t).mapError(TestFailure.fail), annotations)
          )
    }

  /**
   * Same as `foreachExec`, with `ExecutionStrategy.Sequential` as the default
   * execution strategy.
   */
  final def foreach[R1 <: R, E1](
    failure: Cause[TestFailure[E]] => ZIO[R1, TestFailure[E1], TestSuccess],
    success: TestSuccess => ZIO[R1, E1, TestSuccess]
  )(implicit trace: Trace): ZIO[R1 with Scope, Nothing, Spec[R1, E1]] =
    foreachExec(ExecutionStrategy.Sequential)(failure, success)

  /**
   * Same as `foreachExec`, with `ExecutionStrategy.Parallel` as the default
   * execution strategy.
   */
  final def foreachPar[R1 <: R, E1](
    failure: Cause[TestFailure[E]] => ZIO[R1, TestFailure[E1], TestSuccess],
    success: TestSuccess => ZIO[R1, E1, TestSuccess]
  )(implicit trace: Trace): ZIO[R1 with Scope, Nothing, Spec[R1, E1]] =
    foreachExec(ExecutionStrategy.Parallel)(failure, success)

  /**
   * Same as `foreachExec`, with `ExecutionStrategy.ParallelN(n)` as the
   * default execution strategy.
   */
  final def foreachParN[R1 <: R, E1](
    n: Int
  )(
    failure: Cause[TestFailure[E]] => ZIO[R1, TestFailure[E1], TestSuccess],
    success: TestSuccess => ZIO[R1, E1, TestSuccess]
  )(implicit
    trace: Trace
  ): ZIO[R1 with Scope, Nothing, Spec[R1, E1]] =
    foreachExec(ExecutionStrategy.ParallelN(n))(failure, success)

  /**
   * Returns a spec whose tests and scoped nodes fail with `f(e)` where they
   * would fail with the error `e`. Assertion failures and defects are left
   * unchanged.
   */
  final def mapError[E1](f: E => E1)(implicit ev: CanFail[E], trace: Trace): Spec[R, E1] =
    transform[R, E1] {
      case e: ExecCase[Spec[R, E1]]     => e
      case l: LabeledCase[Spec[R, E1]]  => l
      case ScopedCase(scoped)           => ScopedCase[R, E1, Spec[R, E1]](scoped.mapError(_.map(f)))
      case m: MultipleCase[Spec[R, E1]] => m
      case TestCase(test, annotations)  => TestCase(test.mapError(_.map(f)), annotations)
    }

  /**
   * Returns a new spec in which each suite and test label is replaced by
   * `f(label)`.
   */
  final def mapLabel(f: String => String)(implicit trace: Trace): Spec[R, E] =
    transform[R, E] {
      case LabeledCase(label, spec) => LabeledCase(f(label), spec)
      case s                        => s
    }

  /**
   * Provides each test with the part of the environment that is not part of the
   * `TestEnvironment`, leaving a spec that only depends on the
   * `TestEnvironment`. The layer is built once for each test.
   *
   * {{{
   * import zio.{ULayer, ZIO, ZLayer}
   *
   * trait Logging
   *
   * val loggingLayer: ULayer[Logging] = ZLayer.succeed(new Logging {})
   *
   * val spec: Spec[TestEnvironment with Logging, Nothing] =
   *   test("logs")(ZIO.service[Logging].as(assertCompletes))
   *
   * val spec2: Spec[TestEnvironment, Nothing] =
   *   spec.provideCustomLayer(loggingLayer)
   * }}}
   */
  @deprecated("use provideLayer", "2.0.2")
  def provideCustomLayer[E1 >: E, R1](layer: ZLayer[TestEnvironment, E1, R1])(implicit
    ev: TestEnvironment with R1 <:< R,
    tagged: EnvironmentTag[R1],
    trace: Trace
  ): Spec[TestEnvironment, E1] =
    provideSomeLayer[TestEnvironment](layer)

  /**
   * Provides all tests with a shared version of the part of the environment
   * that is not part of the `TestEnvironment`, leaving a spec that only depends
   * on the `TestEnvironment`. The layer is built once and shared by the tests.
   *
   * {{{
   * import zio.{ULayer, ZIO, ZLayer}
   *
   * trait Logging
   *
   * val loggingLayer: ULayer[Logging] = ZLayer.succeed(new Logging {})
   *
   * val spec: Spec[TestEnvironment with Logging, Nothing] =
   *   test("logs")(ZIO.service[Logging].as(assertCompletes))
   *
   * val spec2: Spec[TestEnvironment, Nothing] =
   *   spec.provideCustomLayerShared(loggingLayer)
   * }}}
   */
  @deprecated("use provideLayerShared", "2.0.2")
  def provideCustomLayerShared[E1 >: E, R1](layer: ZLayer[TestEnvironment, E1, R1])(implicit
    ev: TestEnvironment with R1 <:< R,
    tagged: EnvironmentTag[R1],
    trace: Trace
  ): Spec[TestEnvironment, E1] =
    provideSomeLayerShared(layer)

  /**
   * Provides each test in this spec with its required environment `r`.
   */
  final def provideEnvironment(r: ZEnvironment[R])(implicit trace: Trace): Spec[Any, E] =
    provideSomeEnvironment(_ => r)

  /**
   * Provides each test in this spec with the services of `layer`, leaving a
   * spec that requires only the layer's input `R0`. The layer is built once
   * for each test, so tests do not share its services; a failure to build it
   * fails the test.
   */
  final def provideLayer[E1 >: E, R0](
    layer: ZLayer[R0, E1, R]
  )(implicit trace: Trace): Spec[R0, E1] =
    transform[R0, E1] {
      case e: ExecCase[Spec[R0, E1]]     => e
      case l: LabeledCase[Spec[R0, E1]]  => l
      case m: MultipleCase[Spec[R0, E1]] => m
      case ScopedCase(scoped) =>
        ScopedCase[R0, E1, Spec[R0, E1]](
          layer.mapError(TestFailure.fail).build.flatMap(r => scoped.provideSomeEnvironment[Scope](r.union[Scope](_)))
        )
      case TestCase(test, annotations) => TestCase(test.provideLayer(layer.mapError(TestFailure.fail)), annotations)
    }

  /**
   * Provides the tests in this spec with the services of `layer`, built once
   * and shared by all of them, leaving a spec that requires only the layer's
   * input `R0`. The services are released when the spec finishes. A failure to
   * build the layer fails the tests.
   */
  final def provideLayerShared[E1 >: E, R0](
    layer: ZLayer[R0, E1, R]
  )(implicit trace: Trace): Spec[R0, E1] =
    caseValue match {
      case ExecCase(exec, spec)     => Spec.exec(exec, spec.provideLayerShared(layer))
      case LabeledCase(label, spec) => Spec.labeled(label, spec.provideLayerShared(layer))
      case ScopedCase(scoped) =>
        Spec.scoped[R0](
          layer
            .mapError(TestFailure.fail)
            .build
            .flatMap(r => scoped.map(_.provideEnvironment(r)).provideSomeEnvironment[Scope](r.union[Scope]))
        )
      case MultipleCase(specs) =>
        Spec.scoped[R0] {
          ZIO.scopeWith { scope =>
            ZLayer
              .makeSome[R0, R](
                ZLayer.succeed(scope),
                layer.extendScope
              )
              .memoize
              .map { memoizedLayer =>
                Spec.multiple(specs.map(_.provideLayer(memoizedLayer)))
              }
          }
        }
      case TestCase(test, annotations) => Spec.test(test.provideLayer(layer.mapError(TestFailure.fail)), annotations)
    }

  /**
   * Transforms the environment being provided to each test in this spec with
   * the specified function.
   */
  final def provideSomeEnvironment[R0](
    f: ZEnvironment[R0] => ZEnvironment[R]
  )(implicit trace: Trace): Spec[R0, E] =
    transform[R0, E] {
      case e: ExecCase[Spec[R0, E]]     => e
      case l: LabeledCase[Spec[R0, E]]  => l
      case m: MultipleCase[Spec[R0, E]] => m
      case ScopedCase(scoped) =>
        ScopedCase[R0, E, Spec[R0, E]](
          scoped.provideSomeEnvironment[R0 with Scope](in => f(in).unsafe.addScope(in.getScope)(Unsafe))
        )
      case TestCase(test, annotations) => TestCase(test.provideSomeEnvironment(f), annotations)
    }

  /**
   * Splits the environment into two parts, providing each test with one part
   * using the specified layer and leaving the remainder `R0`. The layer is
   * built once for each test.
   *
   * {{{
   * import zio.{ULayer, ZIO, ZLayer}
   *
   * trait Database
   * trait Cache
   *
   * val databaseLayer: ULayer[Database] = ZLayer.succeed(new Database {})
   *
   * val spec: Spec[Database with Cache, Nothing] =
   *   test("reads through the cache") {
   *     ZIO.service[Database].zip(ZIO.service[Cache]).as(assertCompletes)
   *   }
   *
   * val spec2: Spec[Cache, Nothing] =
   *   spec.provideSomeLayer[Cache](databaseLayer)
   * }}}
   */
  final def provideSomeLayer[R0]: Spec.ProvideSomeLayer[R0, R, E] =
    new Spec.ProvideSomeLayer[R0, R, E](self)

  /**
   * Splits the environment into two parts, providing all tests with a shared
   * version of one part using the specified layer and leaving the remainder
   * `R0`. The layer is built once and shared by the tests.
   *
   * {{{
   * import zio.{ULayer, ZIO, ZLayer}
   *
   * trait Database
   * trait Cache
   *
   * val databaseLayer: ULayer[Database] = ZLayer.succeed(new Database {})
   *
   * val spec: Spec[Database with Cache, Nothing] =
   *   suite("queries")(
   *     test("first") {
   *       ZIO.service[Database].zip(ZIO.service[Cache]).as(assertCompletes)
   *     },
   *     test("second") {
   *       ZIO.service[Database].as(assertCompletes)
   *     }
   *   )
   *
   * val spec2: Spec[Cache, Nothing] =
   *   spec.provideSomeLayerShared[Cache](databaseLayer)
   * }}}
   */
  final def provideSomeLayerShared[R0]: Spec.ProvideSomeLayerShared[R0, R, E] =
    new Spec.ProvideSomeLayerShared[R0, R, E](self)

  /**
   * Rebuilds the spec bottom-up, applying `f` to each node after the node's
   * children have been transformed.
   */
  final def transform[R1, E1](
    f: SpecCase[R, E, Spec[R1, E1]] => SpecCase[R1, E1, Spec[R1, E1]]
  )(implicit trace: Trace): Spec[R1, E1] =
    caseValue match {
      case ExecCase(exec, spec)     => Spec(f(ExecCase(exec, spec.transform(f))))
      case LabeledCase(label, spec) => Spec(f(LabeledCase(label, spec.transform(f))))
      case ScopedCase(scoped)       => Spec(f(ScopedCase[R, E, Spec[R1, E1]](scoped.map(_.transform[R1, E1](f)))))
      case MultipleCase(specs)      => Spec(f(MultipleCase(specs.map(_.transform(f)))))
      case t: TestCase[R, E]        => Spec(f(t))
    }

  /**
   * Updates the service of type `M` in the environment of each test in this
   * spec with a function, given as `spec.updateService[M](f)`.
   */
  final def updateService[M] =
    new Spec.UpdateService[R, E, M](self)

  /**
   * Updates the service stored under a key in a `Map[Key, Service]` in the
   * environment of each test in this spec, given as
   * `spec.updateServiceAt[Service](key)(f)`.
   */
  final def updateServiceAt[Service]: Spec.UpdateServiceAt[R, E, Service] =
    new Spec.UpdateServiceAt[R, E, Service](self)

  /**
   * Runs the spec only if `b` is `true`, as `whenZIO` does with an effect.
   * `b` is evaluated again for each test and scoped node.
   */
  final def when(b: => Boolean)(implicit trace: Trace): Spec[R, E] =
    whenZIO(ZIO.succeed(b))

  /**
   * Runs the spec only if the effect `b` succeeds with `true`. `b` runs once
   * for each test and for each scoped node (one that acquires resources, as
   * `provideLayerShared` creates). When it returns `false`, a test is reported
   * as ignored, and a scoped node is replaced by an empty spec without
   * acquiring its resources, so the tests inside it are not reported at all.
   * When `b` fails, the guarded tests and scoped nodes fail with its error.
   */
  final def whenZIO[R1 <: R, E1 >: E](b: ZIO[R1, E1, Boolean])(implicit trace: Trace): Spec[R1, E1] =
    caseValue match {
      case ExecCase(exec, spec)     => Spec.exec(exec, spec.whenZIO(b))
      case LabeledCase(label, spec) => Spec.labeled(label, spec.whenZIO(b))
      case MultipleCase(specs)      => Spec.multiple(specs.map(_.whenZIO(b)))
      case ScopedCase(scoped) =>
        Spec.scoped[R1](
          b.foldZIO(
            failure = e => Exit.fail(TestFailure.fail(e)),
            success = if (_) scoped else Exit.succeed(Spec.empty)
          )
        )
      case TestCase(test, annotations) =>
        Spec.test(
          b.foldZIO(
            failure = e => Exit.fail(TestFailure.fail(e)),
            success = if (_) test else Annotations.annotate(TestAnnotation.ignored, 1).as(TestSuccess.ignored)
          ),
          annotations
        )
    }
}

/**
 * The node types of [[Spec]] and the constructors that build one node.
 */
object Spec {
  /**
   * One node of a [[Spec]] tree. `A` is the type of the node's children: a
   * `Spec` inside a spec, or the result of a fold.
   */
  sealed abstract class SpecCase[-R, +E, +A] { self =>
    /**
     * Applies `f` to the children of this node. A test node has no children and
     * is returned unchanged.
     */
    final def map[B](f: A => B)(implicit trace: Trace): SpecCase[R, E, B] =
      self match {
        case ExecCase(exec, spec)     => ExecCase(exec, f(spec))
        case LabeledCase(label, spec) => LabeledCase(label, f(spec))
        case ScopedCase(scoped)       => ScopedCase[R, E, B](scoped.map(f))
        case MultipleCase(specs)      => MultipleCase(specs.map(f))
        case t: TestCase[R, E]        => t
      }
  }
  /**
   * A node that runs the groups inside `spec` with the execution strategy
   * `exec`, unless a nested node sets another one.
   */
  final case class ExecCase[+Spec](exec: ExecutionStrategy, spec: Spec) extends SpecCase[Any, Nothing, Spec]
  /**
   * A node that gives `spec` the label `label`, as `suite` and `test` do.
   */
  final case class LabeledCase[+Spec](label: String, spec: Spec)        extends SpecCase[Any, Nothing, Spec]
  /**
   * A node whose child spec is produced by the scoped effect `scoped`. When the
   * spec runs, the resources that `scoped` acquires stay open until the child
   * spec has finished.
   */
  final case class ScopedCase[-R, +E, +Spec](scoped: ZIO[Scope with R, TestFailure[E], Spec])
      extends SpecCase[R, E, Spec]
  /**
   * A node that groups `specs`, running them with the execution strategy in
   * effect.
   */
  final case class MultipleCase[+Spec](specs: Chunk[Spec]) extends SpecCase[Any, Nothing, Spec]
  /**
   * A single test: the effect `test` and the annotations given to it before it
   * runs.
   */
  final case class TestCase[-R, +E](test: ZIO[R, TestFailure[E], TestSuccess], annotations: TestAnnotationMap)
      extends SpecCase[R, E, Nothing]

  /**
   * Returns `spec` with its groups run with the execution strategy `exec`.
   */
  final def exec[R, E](exec: ExecutionStrategy, spec: Spec[R, E]): Spec[R, E] =
    Spec(ExecCase(exec, spec))

  /**
   * Returns `spec` with the label `label`.
   */
  final def labeled[R, E](label: String, spec: Spec[R, E]): Spec[R, E] =
    Spec(LabeledCase(label, spec))

  /**
   * Creates a spec from a scoped effect that produces it, given as
   * `Spec.scoped[R](effect)`. Resources that the effect acquires stay open
   * until the produced spec has finished.
   */
  final def scoped[R]: ScopedPartiallyApplied[R] =
    new ScopedPartiallyApplied[R]

  /**
   * Groups `specs` into one spec without a label.
   */
  final def multiple[R, E](specs: Chunk[Spec[R, E]]): Spec[R, E] =
    Spec(MultipleCase(specs))

  /**
   * Creates a spec with a single test from the effect `test` and its initial
   * `annotations`. The test has no label of its own.
   */
  final def test[R, E](test: ZIO[R, TestFailure[E], TestSuccess], annotations: TestAnnotationMap)(implicit
    trace: Trace
  ): Spec[R, E] =
    Spec(TestCase(test, annotations))

  /**
   * A spec with no tests.
   */
  val empty: Spec[Any, Nothing] = Spec.multiple(Chunk.empty)

  /**
   * The partially applied form of `Spec#provideSomeLayer`, with the remainder
   * type `R0` fixed.
   */
  final class ProvideSomeLayer[R0, -R, +E](private val self: Spec[R, E]) extends AnyVal {
    /**
     * Provides each test with the services of `layer`, built once for each
     * test, and leaves the remainder `R0` to be provided later.
     */
    def apply[E1 >: E, R1](
      layer: ZLayer[R0, E1, R1]
    )(implicit
      ev: R0 with R1 <:< R,
      tagged: EnvironmentTag[R1],
      trace: Trace
    ): Spec[R0, E1] =
      self.asInstanceOf[Spec[R0 with R1, E]].provideLayer(ZLayer.environment[R0] <*> layer)
  }

  /**
   * The partially applied form of `Spec#provideSomeLayerShared`, with the
   * remainder type `R0` fixed.
   */
  final class ProvideSomeLayerShared[R0, -R, +E](private val self: Spec[R, E]) extends AnyVal {
    /**
     * Provides the tests with the services of `layer`, built once and shared by
     * all of them, and leaves the remainder `R0` to be provided later.
     */
    def apply[E1 >: E, R1](
      layer: ZLayer[R0, E1, R1]
    )(implicit
      ev: R0 with R1 <:< R,
      tagged: EnvironmentTag[R1],
      trace: Trace
    ): Spec[R0, E1] =
      self.caseValue match {
        case ExecCase(exec, spec)     => Spec.exec(exec, spec.provideSomeLayerShared(layer))
        case LabeledCase(label, spec) => Spec.labeled(label, spec.provideSomeLayerShared(layer))
        case ScopedCase(scoped) =>
          Spec.scoped[R0](
            layer.mapError(TestFailure.fail).build.flatMap { r =>
              scoped
                .map(_.provideSomeLayer[R0](ZLayer.succeedEnvironment(r)))
                .provideSomeEnvironment[R0 with Scope](in => in.union[R1](r).asInstanceOf[ZEnvironment[R with Scope]])
            }
          )
        case MultipleCase(specs) =>
          Spec.scoped[R0] {
            ZIO.scopeWith { scope =>
              ZLayer
                .makeSome[R0, R1](
                  ZLayer.succeed(scope),
                  layer.extendScope
                )
                .memoize
                .map { memoizedLayer =>
                  Spec.multiple(specs.map(_.provideSomeLayer[R0](memoizedLayer)))
                }
            }
          }
        case TestCase(test, annotations) =>
          Spec.test(test.provideSomeLayer(layer.mapError(TestFailure.fail)), annotations)
      }
  }

  /**
   * The partially applied form of `Spec.scoped`, with the environment type `R`
   * fixed.
   */
  final class ScopedPartiallyApplied[R](private val dummy: Boolean = true) extends AnyVal {
    /**
     * Creates a spec from the scoped effect `scoped` that produces it.
     */
    def apply[E, T](scoped: => ZIO[Scope with R, TestFailure[E], Spec[R, E]]): Spec[R, E] =
      Spec(ScopedCase[R, E, Spec[R, E]](scoped))
  }

  /**
   * The partially applied form of `Spec#updateService`, with the service type
   * `M` fixed.
   */
  final class UpdateService[-R, +E, M](private val self: Spec[R, E]) extends AnyVal {
    /**
     * Replaces the service of type `M` in the environment of each test with
     * `f` applied to it.
     */
    def apply[R1 <: R with M](
      f: M => M
    )(implicit tag: Tag[M], trace: Trace): Spec[R1, E] =
      self.provideSomeEnvironment(_.update(f))
  }

  /**
   * The partially applied form of `Spec#updateServiceAt`, with the service type
   * `Service` fixed.
   */
  final class UpdateServiceAt[-R, +E, Service](private val self: Spec[R, E]) extends AnyVal {
    /**
     * Replaces the service stored under `key` in the `Map[Key, Service]` of
     * each test's environment with `f` applied to it. When the map has no entry
     * for `key`, the test fails with a `java.util.NoSuchElementException`.
     */
    def apply[R1 <: R with Map[Key, Service], Key](key: => Key)(
      f: Service => Service
    )(implicit tag: Tag[Map[Key, Service]], trace: Trace): Spec[R1, E] =
      self.provideSomeEnvironment(_.updateAt(key)(f))
  }

}
