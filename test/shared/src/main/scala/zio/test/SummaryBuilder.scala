package zio.test

import zio.{ UIO, ZIO }
import zio.test.Spec._

object SummaryBuilder {
  def buildSummary[E, L, S](executedSpec: ExecutedSpec[E, L, S]): UIO[Summary] =
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
      failures <- extractFailures(executedSpec).map(_.map(_.mapLabel(_.toString)))
      rendered <- ZIO.foreach(failures)(DefaultTestReporter.render(_))
    } yield Summary(success, fail, ignore, rendered.flatten.flatMap(_.rendered).mkString("\n"))

  private def countTestResults[E, L, S](
    executedSpec: ExecutedSpec[E, L, S]
  )(pred: Either[TestFailure[E], TestSuccess[S]] => Boolean): UIO[Int] =
    executedSpec.fold[UIO[Int]] {
      case SuiteCase(_, counts, _) => counts.flatMap(ZIO.collectAll(_).map(_.sum))
      case TestCase(_, test) =>
        test.map { r =>
          if (pred(r)) 1 else 0
        }
    }

  private def extractFailures[E, L, S](executedSpec: ExecutedSpec[E, L, S]): UIO[Seq[ExecutedSpec[E, L, S]]] = {
    def ifM[A](condition: UIO[Boolean])(success: UIO[A])(failure: UIO[A]): UIO[A] =
      condition.flatMap(result => if (result) success else failure)

    def append[A](collection: UIO[Seq[A]], item: A): UIO[Seq[A]] = collection.map(_ :+ item)

    def hasFailures(spec: ExecutedSpec[E, L, S]): UIO[Boolean] = spec.exists {
      case Spec.TestCase(_, test) => test.map(_.isLeft)
      case _                      => UIO.succeed(false)
    }

    def loop(current: ExecutedSpec[E, L, S], accM: UIO[Seq[ExecutedSpec[E, L, S]]]): UIO[Seq[ExecutedSpec[E, L, S]]] =
      ifM(hasFailures(current)) {
        current.caseValue match {
          case suite @ Spec.SuiteCase(_, specs, _) =>
            val newSpecs = specs.flatMap(ZIO.foreach(_)(extractFailures).map(_.flatten.toVector))
            append(accM, Spec(suite.copy(specs = newSpecs)))

          case Spec.TestCase(_, _) =>
            append(accM, current)
        }
      } {
        accM
      }

    loop(executedSpec, UIO.succeed(Vector.empty[ExecutedSpec[E, L, S]]))
  }
}
