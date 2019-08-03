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


package zio.test.runner

import org.specs2.concurrent.ExecutionEnv
import zio.console._
import zio.test._
import zio.test.mock.{MockConsole, MockEnvironment, mockEnvironmentManaged}
import zio.test.runner.ExecutedSpecStructure.Stats
import zio.{RIO, TestRuntime, ZIO}

class ExecutedSpecStructureSpec(implicit ee: ExecutionEnv) extends TestRuntime {
  def is = "ExecutedSpecStructure".title ^ s2"""
    Traverse failing spec results $failingSpec
  """

  def failingSpec = {
    val output = unsafeRun(withEnvironment {
      for {
        results <- ExecutedSpecStructureSpec.SimpleFailingSpec.run
        _ <- ExecutedSpecStructure.from(results).traverse(handleSuite, handleTest)
        output <- MockConsole.output
      } yield output
    })

    output === Vector(
      "TEST: failing test: FAILURE: 1 did not satisfy equals(2)",
      "TEST: passing test: SUCCESS",
      "SUITE: some suite: Stats(1,1,0)"
    )
  }

  def withEnvironment[E, A](zio: ZIO[MockEnvironment, E, A]): ZIO[Any, E, A] =
    mockEnvironmentManaged.use[Any, E, A](r => zio.provide(r))


  def handleSuite(label: String, stats: Stats): RIO[MockEnvironment, Unit] = {
    for {
      _ <- putStr(s"SUITE: $label: $stats")
    } yield ()
  }

  def handleTest(label: String, testResult: ZTestResult): RIO[MockEnvironment, Unit] = {
    for {
      _ <- putStr(s"TEST: $label: ${testResult.rendered}")
    } yield ()
  }
}

object ExecutedSpecStructureSpec {
  private object SimpleFailingSpec extends DefaultRunnableSpec(
      suite("some suite") (
        test("failing test") {
          assert(1, Predicate.equals(2))
        },
        test("passing test") {
          assert(1, Predicate.equals(1))
        }
      )
    )
}
