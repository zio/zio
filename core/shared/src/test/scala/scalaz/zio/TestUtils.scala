package scalaz.zio

trait TestUtils {
  def withLatch[R, E, A](f: UIO[Unit] => ZIO[R, E, A]): ZIO[R, E, A] =
    Promise.make[Nothing, Unit] >>= (latch => f(latch.succeed(()).unit) <* latch.await)

  def withLatch[R, E, A](f: (UIO[Unit], UIO[Unit]) => ZIO[R, E, A]): ZIO[R, E, A] =
    Promise.make[Nothing, Unit] >>= (latch => f(latch.succeed(()).unit, latch.await))
}
