package zio.test

import zio.{Ref, ZIO}

object TestReporters {
  val make: ZIO[Any, Nothing, TestReporters] =
    Ref.make(List.empty[SuiteId]).map(TestReporters(_))
}

case class TestReporters(reportersStack: Ref[List[SuiteId]]) {

  def attemptToGetPrintingControl(id: SuiteId, ancestors: List[SuiteId]): ZIO[Any, Nothing, Boolean] =
    reportersStack.updateSomeAndGet {
      case Nil =>
        println("No reporters yet. Selecting: " + id)
        List(id)

      case reporters if ancestors.nonEmpty && reporters.head == ancestors.head =>
        println("Getting control from parent. Child id: " + id)
        id :: reporters
    }.map(_.head == id)

  def relinquishPrintingControl(id: SuiteId): ZIO[Any, Nothing, Unit] =
    reportersStack.updateSome {
      case currentReporter :: reporters if currentReporter == id =>
        reporters
    }

}
