package zio.internal

object Sync {
  def apply[A](anyRef: AnyRef)(f: => A): A = {
    assert(anyRef != null)
    f
  }
}
