package zio.internal

private[zio] object Sync {
  @inline def apply[A](anyRef: AnyRef)(f: => A): A = {
    val _ = anyRef
    f
  }
}
