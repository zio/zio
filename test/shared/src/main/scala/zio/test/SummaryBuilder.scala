package zio.test

import zio.{ UIO, ZIO }

object SummaryBuilder {
  def buildSummary[L, E, S](executedSpec: ExecutedSpec[L, E, S]): UIO[String] =
    for {
      failures <- extractFailures(executedSpec).map(_.map(_.mapLabel(_.toString)))
      rendered <- ZIO.foreach(failures)(DefaultTestReporter.render(_))
    } yield rendered.flatten.flatMap(_.rendered).mkString("\n")

  private def extractFailures[L, E, S](executedSpec: ExecutedSpec[L, E, S]): UIO[Seq[ExecutedSpec[L, E, S]]] = {
    def ifM[A](condition: UIO[Boolean])(success: UIO[A])(failure: UIO[A]): UIO[A] =
      condition.flatMap(result => if (result) success else failure)

    def append[A](collection: UIO[Seq[A]], item: A): UIO[Seq[A]] = collection.map(_ :+ item)

    def hasFailures(spec: ExecutedSpec[L, E, S]): UIO[Boolean] = spec.exists {
      case Spec.TestCase(_, test) => test.map(_.isLeft)
      case _                      => UIO.succeed(false)
    }

    def loop(current: ExecutedSpec[L, E, S], accM: UIO[Seq[ExecutedSpec[L, E, S]]]): UIO[Seq[ExecutedSpec[L, E, S]]] =
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

    loop(executedSpec, UIO.succeed(Vector.empty[ExecutedSpec[L, E, S]]))
  }
}
