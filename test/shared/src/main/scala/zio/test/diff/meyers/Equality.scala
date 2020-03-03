package zio.test.diff.meyers

trait Equality[T] {
  def equals(original: T, revised: T): Boolean
}
object Equality {
  def default[T]: Equality[T] = _ == _
}
