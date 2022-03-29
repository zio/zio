package zio.test
import zio.Scope
import zio._
import zio.test.ExecutionEvent.{SectionEnd, SectionStart}

case class TestEntity(
  id: SuiteId,
  ancestors: List[SuiteId]
)

object TestOutputSpec extends ZIOSpecDefault {
  /*
    1 -> 2 -> 4
           -> 5

      -> 3 -> 6
           -> 7
   */
  private val parent = TestEntity(
    id = SuiteId(1),
    ancestors = List.empty
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

  private val grandchild4 =
    TestEntity(
      SuiteId(4),
      List(child1.id, parent.id)
    )

  private val grandchild5 =
    TestEntity(
      SuiteId(5),
      List(child1.id, parent.id)
    )

  private val grandChild6 =
    TestEntity(
      SuiteId(6),
      List(child2.id, parent.id)
    )

  private val grandChild7 =
    TestEntity(
      SuiteId(7),
      List(child2.id, parent.id)
    )

  val allEntities = List(parent, child1, child2, grandchild4, grandchild5, grandChild6, grandChild7)

  class ExecutionEventHolder(events: Ref[List[ExecutionEvent]]) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[TestLogger, Nothing, Unit] =
      events.update(_ :+ event)

    def getEvents: ZIO[TestLogger, Nothing, List[ExecutionEvent]] =
      events.get
  }

  val makeFakePrinter: ZIO[Any, Nothing, ExecutionEventHolder] =
    for {
      output <- Ref.make[List[ExecutionEvent]](List.empty)
    } yield
      new ExecutionEventHolder(output)

  val fakePrinterLayer = ZLayer.fromZIO(makeFakePrinter)

  override def spec: ZSpec[TestEnvironment with Scope, Any] = suite("TestOutputSpec")(
    test("nested events without flushing") {
      for {
        parentSectionStart <- sectionStart(parent)
        child1SectionStart <- sectionStart(child1)
        _ <- sectionStart(child2)
        child1test1 <- submitSuccessfulTest(child1)
        child1test2 <- submitSuccessfulTest(child1)
        _ <- submitSuccessfulTest(child2)
        _ <- sectionEnd(child2) // This output is sent to the parent, so if we don't close the parent section, we will not see this event
        child1end <- sectionEnd(child1)
        outputEvents <- ZIO.serviceWithZIO[ExecutionEventHolder](_.getEvents)
      } yield assertTrue(outputEvents ==
          List(
            parentSectionStart,
            child1SectionStart,
            child1test1,
            child1test2,
            child1end
          )
        )
    },
    test("nested events with flushing") {
      for {
        parentSectionStart <- sectionStart(parent)
        child1SectionStart <- sectionStart(child1)
        child2SectionStart <- sectionStart(child2)
        child1test1 <- submitSuccessfulTest(child1)
        child1test2 <- submitSuccessfulTest(child1)
        child2test1 <- submitSuccessfulTest(child2)
        child2end <- sectionEnd(child2) // This output is sent to the parent, so if we don't close the parent section, we will not see this event
        child1end <- sectionEnd(child1)
        parentEnd <- sectionEnd(parent)
        outputEvents <- ZIO.serviceWithZIO[ExecutionEventHolder](_.getEvents)
      } yield assertTrue(outputEvents ==
        List(
          parentSectionStart,
          child1SectionStart,
          child1test1,
          child1test2,
          child1end,
          child2SectionStart,
          child2test1,
          child2end,
          parentEnd
        )
      )
    }
  ).provideSome[
    TestConsole with TestOutput
  ](ExecutionEventSink.live, TestLogger.fromConsole, fakePrinterLayer)

  private def sectionStart(testEntity: TestEntity) =
    for {
      testOutput <- ZIO.service[TestOutput]
      sectionStart =
        SectionStart(
          List("label"),
          testEntity.id,
          testEntity.ancestors,
        )
      _ <- testOutput.print(
        sectionStart
      )
    } yield sectionStart

  private def sectionEnd(testEntity: TestEntity) =
    for {
      testOutput <- ZIO.service[TestOutput]
      end =         SectionEnd(
        List("section: " + testEntity.id.id),
        testEntity.id,
        testEntity.ancestors,
      )
      _ <- testOutput.print(
        end
      )
    } yield end

  private def submitSuccessfulTest(testEntity: TestEntity) = {
    for {
      testOutput <- ZIO.service[TestOutput]
      test =
        ExecutionEvent.Test(
          labelsReversed = List("label"),
          test = Right(TestSuccess.Succeeded(BoolAlgebra.unit)),
          annotations = TestAnnotationMap.empty,
          ancestors = testEntity.ancestors,
          duration = 0L,
          id = testEntity.id
        )
      _ <- testOutput.print(test)

    } yield test
  }


}
