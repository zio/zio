package zio.internal

private[zio] object Sync {
  final def apply[A](anyRef: AnyRef)(f: => A): A = anyRef.synchronized { f }
}
