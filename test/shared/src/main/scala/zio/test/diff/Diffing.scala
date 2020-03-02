package zio.test.diff

trait Diffing {
  def diff[A, B](actual: A, expected: B): Option[DiffResult]
}

object Diffing {
  val default = new Diffing {
    override def diff[A, B](actual: A, expected: B): Option[DiffResult] =
      (actual, expected) match {
        case (a: String, e: String) => Some(StringDiffer.default.diff(a, e))
        case _                      => None
      }
  }
  val noDiffing = new Diffing {
    override def diff[A, B](actual: A, expected: B): Option[DiffResult] = None
  }
}
