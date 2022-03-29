package zio.test
import zio.Scope

import zio._

case class TestEntity(
                       id: SuiteId,
                       ancestors: List[SuiteId],
                     )

object TestOutputSpec extends ZIOSpecDefault {
  private val parent = TestEntity(
    id = SuiteId(1),
    ancestors = List.empty,
  )

  private val child1 =
    TestEntity(
      SuiteId(2),
      List(parent.id)
    )

  private val child2 =
    TestEntity(
      SuiteId(3),
      List(parent.id)
    )


  override def spec: ZSpec[TestEnvironment with Scope, Any] = suite("TestOutputSpec")(
    test("TestOutput.run") {
      for {
        testConsole <- ZIO.service[TestConsole]
        _ <- printOrQueue(child1, Success, List("success"))
        _ <- printOrQueue(child1, Failure,  List("failure"))
        _ <- printOrQueue(child2, Failure,  List("queuedMessage"))
        output <- testConsole.output
        _ <- ZIO.debug(output)
      } yield outputContainsAllOf(output, "success", "failure") &&
        outputContainsNoneOf(output, "queuedMessage")
    }
  ).provideSome[
      TestConsole
      with TestOutput
  ](ExecutionEventSink.live, TestLogger.fromConsole)

  def outputContainsAllOf(output: Seq[String], expected: String*) = {
    expected.map(
      expectedValue =>
        assertTrue(output.exists(_.contains(expectedValue)))
    ).reduce(_ && _)
  }

  def outputContainsNoneOf(output: Seq[String], expected: String*) = {
    expected.map(
      expectedValue =>
        assertTrue(!output.exists(_.contains(expectedValue)))
    ).reduce(_ && _)
  }




  sealed trait TestStatus
  case object Success extends TestStatus
  case object Failure extends TestStatus

  def printOrQueue(testEntity: TestEntity, testStatus: TestStatus, labels: List[String]) =

    for {
      testOutput <-  ZIO.service[TestOutput]
      _ <- testOutput.printOrQueue(
        testStatus match {
          case Success => successfulTest(testEntity.id, "TestOutputSpec" :: labels)
          case Failure => failedTest(testEntity.id,  "TestOutputSpec" :: labels)
        }
      )
    } yield ()

  private def successfulTest(suiteId: SuiteId, labels: List[String], ancestors: List[SuiteId] = List.empty) =
    ExecutionEvent.Test(
      labelsReversed = labels.reverse ,
      test = Right(TestSuccess.Succeeded(BoolAlgebra.unit)),
      annotations = TestAnnotationMap.empty,
      ancestors = ancestors,
      duration = 0L,
      id = suiteId
    )

  private def failedTest(suiteId: SuiteId, labels: List[String], ancestors: List[SuiteId] = List.empty) =
    ExecutionEvent.Test(
      labelsReversed = labels.reverse ,
      test = Left(arbitraryFailure),
      annotations = TestAnnotationMap.empty,
      ancestors,
      duration = 0L,
      id = suiteId
    )

  private val arbitraryFailure =
    TestFailure.Assertion(BoolAlgebra.failure[AssertionResult](AssertionResult.FailureDetailsResult(
      FailureDetails(
        ::(AssertionValue(Assertion.anything, (), Assertion.anything.run(())), Nil)
      )
    )))
}
