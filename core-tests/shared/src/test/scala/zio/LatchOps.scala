package zio

trait LatchOps {
  def withLatch[R, E, A](f: UIO[Unit] => ZIO[R, E, A]): ZIO[R, E, A] =
    Promise.make[Nothing, Unit] >>= (latch => f(latch.succeed(()).unit) <* latch.await)

  def withLatch[R, E, A](f: (UIO[Unit], UIO[Unit]) => ZIO[R, E, A]): ZIO[R, E, A] =
    for {
      ref   <- Ref.make(true)
      latch <- Promise.make[Nothing, Unit]
      a     <- f(latch.succeed(()).unit, ZIO.uninterruptibleMask(restore => ref.set(false) *> restore(latch.await)))
      _     <- UIO.whenM(ref.get)(latch.await)
    } yield a
}

object LatchOps extends LatchOps
