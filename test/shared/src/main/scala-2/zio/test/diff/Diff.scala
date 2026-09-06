package zio.test.diff

trait Diff[A] { self =>
  def diff(x: A, y: A): DiffResult

  final def contramap[B](f: B => A): Diff[B] =
    (x, y) => self.diff(f(x), f(y))

  def isLowPriority: Boolean = false
}

object Diff extends DiffInstances {
  def apply[A](implicit diff: Diff[A]): Diff[A] = diff

  def render[A: Diff](oldValue: A, newValue: A): String =
    (oldValue diffed newValue).render

  implicit final class DiffOps[A](private val self: A)(implicit diff: Diff[A]) {
    def diffed(that: A): DiffResult = diff.diff(self, that)
  }
}
