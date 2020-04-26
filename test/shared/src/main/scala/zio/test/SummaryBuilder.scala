package zio.test

import zio.test.Spec._
import zio.{ UIO, ZIO }

object SummaryBuilder {
  def buildSummary[E](executedSpec: ExecutedSpec[E]): UIO[Summary] =
    for {
      success <- countTestResults(executedSpec) {
                  case Right(TestSuccess.Succeeded(_)) => true
                  case _                               => false
                }
      fail <- countTestResults(executedSpec)(_.isLeft)
      ignore <- countTestResults(executedSpec) {
                 case Right(TestSuccess.Ignored) => true
                 case _                          => false
               }
      failures <- extractFailures(executedSpec)
      rendered <- ZIO.foreach(failures)(DefaultTestReporter.render(_, TestAnnotationRenderer.silent))
    } yield Summary(success, fail, ignore, rendered.flatten.flatMap(_.rendered).mkString("\n"))

  private def countTestResults[E](
    executedSpec: ExecutedSpec[E]
  )(pred: Either[TestFailure[E], TestSuccess] => Boolean): UIO[Int] =
    executedSpec.fold[UIO[Int]] {
      case SuiteCase(_, counts, _) => counts.use(ZIO.collectAll(_).map(_.sum))
      case TestCase(_, test, _) =>
        test.map(r => if (pred(r)) 1 else 0)
    }

  private def extractFailures[E](executedSpec: ExecutedSpec[E]): UIO[Seq[ExecutedSpec[E]]] = {
    def ifM[A](condition: UIO[Boolean])(success: UIO[A])(failure: UIO[A]): UIO[A] =
      condition.flatMap(result => if (result) success else failure)

    def append[A](collection: UIO[Seq[A]], item: A): UIO[Seq[A]] = collection.map(_ :+ item)

    def hasFailures(spec: ExecutedSpec[E]): UIO[Boolean] =
      spec.exists {
        case Spec.TestCase(_, test, _) => test.map(_.isLeft)
        case _                         => UIO.succeedNow(false)
      }.use(UIO.succeedNow)

    def loop(current: ExecutedSpec[E], accM: UIO[Seq[ExecutedSpec[E]]]): UIO[Seq[ExecutedSpec[E]]] =
      ifM(hasFailures(current)) {
        current.caseValue match {
          case suite @ Spec.SuiteCase(_, specs, _) =>
            val newSpecs = specs.use(ZIO.foreach(_)(extractFailures).map(_.flatten.toVector))
            append(accM, Spec(suite.copy(specs = newSpecs.toManaged_)))

          case Spec.TestCase(_, _, _) =>
            append(accM, current)
        }
      } {
        accM
      }

    loop(executedSpec, UIO.succeedNow(Vector.empty[ExecutedSpec[E]]))
  }
}
