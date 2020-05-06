package zio.internal

private[zio] object Sync {
  def apply[A](anyRef: AnyRef)(f: => A): A = anyRef.synchronized(f)
}
