package zio.test
import zio.Scope
import zio._
import zio.test.ExecutionEvent.{SectionEnd, SectionStart}

case class TestEntity(
  id: SuiteId,
  ancestors: List[SuiteId]
) {
  def child(newId: Int): TestEntity =
    TestEntity(SuiteId(newId), id :: ancestors)
}

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
    parent.child(2)

  private val child2 =
    parent.child(3)

  private val child1child1 =
    child1.child(4)

  private val child1child2 =
    child1.child(5)

  private val child2child1 =
    child2.child(6)

  private val child2child2 =
    child2.child(7)

  val allEntities = List(parent, child1, child2, child1child1, child1child2, child2child1, child2child2)

  class ExecutionEventHolder(events: Ref[List[ExecutionEvent]]) extends ExecutionEventPrinter {
    override def print(event: ExecutionEvent): ZIO[Any, Nothing, Unit] =
      events.update(_ :+ event)

    def getEvents: ZIO[Any, Nothing, List[ExecutionEvent]] =
      events.get
  }

  val makeFakePrinter: ZIO[Any, Nothing, ExecutionEventHolder] =
    for {
      output <- Ref.make[List[ExecutionEvent]](List.empty)
    } yield new ExecutionEventHolder(output)

  val fakePrinterLayer: ZLayer[Any, Nothing, ExecutionEventHolder] = ZLayer.fromZIO(makeFakePrinter)

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("TestOutputSpec")(
    test("nested events without flushing") {
      val events =
        List(
          Start(parent),
          Start(child1),
          Start(child2),
          Test(child1),
          Test(child2),
          Test(child1),
          End(child2),
          End(child1)
        )
      for {
        _            <- ZIO.foreach(events)(event => TestOutput.print(event))
        outputEvents <- ZIO.serviceWithZIO[ExecutionEventHolder](_.getEvents)
        _            <- ZIO.service[ExecutionEventPrinter].debug("Printer in test")
      } yield
        assertTrue(
        outputEvents ==
          List(
            Start(parent),
            Start(child1),
            Test(child1),
            Test(child1),
            End(child1)
          )
      ) &&
      sane(outputEvents)
    },
    test("nested events with flushing") {
      val events =
        List(
          Start(parent),
          Start(child1),
          Start(child2),
          Test(child1),
          Test(child2),
          Test(child1),
          End(child2),
          End(child1),
          End(parent)
        )
      for {
        _            <- ZIO.foreach(events)(event => TestOutput.print(event))
        outputEvents <- ZIO.serviceWithZIO[ExecutionEventHolder](_.getEvents)
      } yield assertTrue(
        outputEvents ==
          List(
            Start(parent),
            Start(child1),
            Test(child1),
            Test(child1),
            End(child1),
            Start(child2),
            Test(child2),
            End(child2),
            End(parent)
          )
      ) && sane(outputEvents)
    },
    test("mix of suites and individual tests") {
      val events =
        List(
          Start(parent),
          Start(child1),
          Test(child1, "a"),
          Start(child1child1),
          Test(child1, "b"),
          Test(child1child1),
          Test(child1, "c"),
          End(child1child1),
          End(child1),
          End(parent)
        )
      for {
        _            <- ZIO.foreach(events)(event => TestOutput.print(event))
        outputEvents <- ZIO.serviceWithZIO[ExecutionEventHolder](_.getEvents)
      } yield assertTrue(
        outputEvents ==
          List(
            Start(parent),
            Start(child1),
            Test(child1, "a"), // This child1 test came before the child1child1 section, so it was printed immediately
            Start(child1child1),
            Test(child1child1),
            End(child1child1),
            Test(child1, "b"), // These other child1 tests were queued until child1child1 ended
            Test(child1, "c"),
            End(child1),
            End(parent)
          )
      ) && sane(outputEvents)
    },
    test("more complex mix of suites and individual tests") {
      val events =
        List(
          Start(parent),
          Start(child1),
          Start(child2),
          Test(child1, "a"),
          Start(child2child1),
          Test(child2, "j"),
          Test(child2child1, "k"),
          End(child2child1),
          Start(child1child1),
          Test(child1, "b"),
          Test(child2, "m"),
          Test(child1child1),
          End(child2),
          Test(child1, "c"),
          End(child1child1),
          End(child1),
          End(parent)
        )
      for {
        _            <- ZIO.foreach(events)(event => TestOutput.print(event))
        outputEvents <- ZIO.serviceWithZIO[ExecutionEventHolder](_.getEvents)
      } yield assertTrue(
        outputEvents ==
          List(
            Start(parent),
            Start(child1),
            Test(child1, "a"), // This child1 test came before the child1child1 section, so it was printed immediately
            Start(child1child1),
            Test(child1child1),
            End(child1child1),
            Test(child1, "b"), // These other child1 tests were queued until child1child1 ended
            Test(child1, "c"),
            End(child1),
            Start(child2),
            Test(child2, "j"),
            Start(child2child1),
            Test(child2child1, "k"),
            End(child2child1),
            Test(child2, "m"),
            End(child2),
            End(parent)
          )
      )
    }
  ).provide(fakePrinterLayer >+> TestOutput.live) @@ TestAspect.ignore

  def sane(events: Seq[ExecutionEvent]): Assert = {
    type CompleteSuites   = List[SuiteId]
    type ActiveSuiteStack = List[SuiteId]
    case class InvalidEvent(event: ExecutionEvent, reason: String)

    case class ProcessingState(
      completeSuites: CompleteSuites,
      activeSuiteStack: ActiveSuiteStack
    )
    object ProcessingState {
      def empty: ProcessingState = ProcessingState(Nil, Nil)
    }

    def process(event: ExecutionEvent, state: ProcessingState): Either[InvalidEvent, ProcessingState] = {
      val eventCanStartPrinting =
        state.activeSuiteStack.isEmpty || (event.ancestors.nonEmpty && event.ancestors.head == state.activeSuiteStack.head)

      if (state.completeSuites.contains(event.id)) {
        Left(InvalidEvent(event, "Suite is already complete. Should not see any more events for this suite."))
      } else if (eventCanStartPrinting) {
        event match {
          case start: SectionStart =>
            Right(state.copy(activeSuiteStack = start.id :: state.activeSuiteStack))
          case end: SectionEnd =>
            Left(InvalidEvent(end, "Cannot end before ever starting"))
          case _ =>
            Right(state)
        }
      } else if (event.id == state.activeSuiteStack.head) {
        event match {
          case start: SectionStart =>
            Left(InvalidEvent(start, "SectionStart event with active suite stack."))
          case end: SectionEnd =>
            Right(
              state.copy(
                activeSuiteStack = state.activeSuiteStack.tail,
                completeSuites = end.id :: state.completeSuites
              )
            )
          case _ =>
            Right(state)
        }
      } else {
        Left(InvalidEvent(event, "Event is not in active suite stack"))
      }
    }

    val result: Either[InvalidEvent, ProcessingState] =
      events.foldLeft[Either[InvalidEvent, ProcessingState]](Right(ProcessingState.empty)) {
        case (failure @ Left(_), _) =>
          failure
        case (Right(state), event) =>
          process(event, state)
      }

    assertTrue(result.isRight)

  }

  private def Start(testEntity: TestEntity) =
    SectionStart(
      List("label"),
      testEntity.id,
      testEntity.ancestors
    )

  private def End(testEntity: TestEntity) =
    SectionEnd(
      List("section: " + testEntity.id.id),
      testEntity.id,
      testEntity.ancestors
    )

  private def Test(testEntity: TestEntity, label: String = "label") =
    ExecutionEvent.Test(
      labelsReversed = List(label),
      test = Right(TestSuccess.Succeeded(BoolAlgebra.unit)),
      annotations = TestAnnotationMap.empty,
      ancestors = testEntity.ancestors,
      duration = 0L,
      id = testEntity.id
    )

}
