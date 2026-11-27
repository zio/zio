/*
 * Copyright 2021-2024 John A. De Goes and the ZIO Contributors
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
import zio.internal.stacktracer.SourceLocation
import zio.stacktracer.TracingImplicits.disableAutoTrace

/**
 * The base class for a test suite whose tests need an environment `R` in
 * addition to `TestEnvironment`. Define `bootstrap` as a layer that builds
 * `R`: ZIO Test builds it before the tests run and shares its services with
 * all of them. `ZIOSpecDefault` is the variant without extra services.
 *
 * {{{
 * import zio.{ULayer, ZIO, ZLayer}
 *
 * trait Database {
 *   def ping: Boolean
 * }
 *
 * object DatabaseSpec extends ZIOSpec[Database] {
 *   val bootstrap: ULayer[Database] =
 *     ZLayer.succeed(new Database { def ping: Boolean = true })
 *
 *   def spec =
 *     test("the database answers") {
 *       ZIO.serviceWith[Database](db => assertTrue(db.ping))
 *     }
 * }
 * }}}
 *
 * @see [[https://zio.dev/reference/test/sharing-layers-between-multiple-files/ Sharing Layers between Multiple Files]]
 */
abstract class ZIOSpec[R: EnvironmentTag] extends ZIOSpecAbstract with ZIOSpecVersionSpecific[R] { self =>
  /**
   * The environment that `bootstrap` builds: `R`.
   */
  type Environment = R

  /**
   * The `EnvironmentTag` of `R`.
   */
  final val environmentTag: EnvironmentTag[R] = EnvironmentTag[R]

  /**
   * Builds a spec with a single test.
   */
  def test[In](label: String)(
    assertion: => In
  )(implicit
    testConstructor: TestConstructor[Nothing, In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): testConstructor.Out =
    zio.test.test(label)(assertion)

  /**
   * Builds a suite labeled `label` from `specs`, the same as `zio.test.suite`.
   * The members can be specs, collections of specs, or effects that produce
   * them.
   */
  def suite[In](label: String)(specs: In*)(implicit
    suiteConstructor: SuiteConstructor[In],
    sourceLocation: SourceLocation,
    trace: Trace
  ): Spec[suiteConstructor.OutEnvironment, suiteConstructor.OutError] =
    zio.test.suite(label)(specs: _*)

}
