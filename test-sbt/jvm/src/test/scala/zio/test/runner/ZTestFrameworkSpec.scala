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

import java.util.concurrent.atomic.AtomicReference

import sbt.testing._
import zio.TestRuntime
import zio.test.runner.ZTestFrameworkSpec._
import zio.test.{DefaultRunnableSpec, Predicate, TestAspect, assert, suite, test}

import scala.collection.mutable.ArrayBuffer

class ZTestFrameworkSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  override def is = "ZTestFramework".title ^
    s2"""
      return fingerprint for RunnableSpec  $fingerprint
      getTask.execute() should
        Report events for failing spec $reportEvents
        Log messages for failing spec $logMessages
    """

  def fingerprint = {
    new ZTestFramework().fingerprints === Array(RunnableSpecFingerprint)
  }

  def reportEvents = {
    val reported = ArrayBuffer[Event]()
    loadAndExecute(failingSpecFQN, reported.append(_))

    reported should containTheSameElementsAs(Seq(
      ZTestEvent(failingSpecFQN, new TestSelector("failing test"), Status.Failure, None, 0),
      ZTestEvent(failingSpecFQN, new TestSelector("passing test"), Status.Success, None, 0),
      ZTestEvent(failingSpecFQN, new TestSelector("ignored test"), Status.Ignored, None, 0),
    ))
  }

  def logMessages = {
    val loggers = Seq.fill(3)(new MockLogger)

    loadAndExecute(failingSpecFQN, loggers = loggers)

    loggers.map(_.messages) should forall(containTheSameElementsAs(Seq(
      "info: TEST: failing test: FAILURE: 1 did not satisfy equals(2)",
      "info: TEST: passing test: SUCCESS",
      "info: TEST: ignored test: IGNORED",
      "info: SUITE: some suite, passed: 1, failed: 1, ignored: 1"
    )))
  }

  private def loadAndExecute(fqn: String, eventHandler: EventHandler = _ => (), loggers: Seq[Logger] = Nil) = {
    val taskDef = new TaskDef(fqn, RunnableSpecFingerprint, false, Array())
    val task = new ZTestFramework().runner(Array(), Array(), getClass.getClassLoader)
      .tasks(Array(taskDef))
      .head

    task.execute(eventHandler, loggers.toArray)
  }
}

object ZTestFrameworkSpec {
  val failingSpecFQN = SimpleFailingSpec.getClass.getName
  object SimpleFailingSpec extends DefaultRunnableSpec(
    suite("some suite") (
      test("failing test") {
        assert(1, Predicate.equals(2))
      },
      test("passing test") {
        assert(1, Predicate.equals(1))
      },
      test("ignored test") {
        assert(1, Predicate.equals(2))
      } @@ TestAspect.ignore
    )
  )

  class MockLogger extends Logger {
    private val logged = new AtomicReference(Vector.empty[String])
    private def log(str: String) = {
      logged.getAndUpdate(_ :+ str)
      ()
    }
    def messages: Seq[String] = logged.get()

    override def ansiCodesSupported(): Boolean = false
    override def error(msg: String): Unit = log(s"error: $msg")
    override def warn(msg: String): Unit = log(s"warn: $msg")
    override def info(msg: String): Unit = log(s"info: $msg")
    override def debug(msg: String): Unit = log(s"debug: $msg")
    override def trace(t: Throwable): Unit = log(s"trace: $t")
  }
}
