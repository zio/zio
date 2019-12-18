package zio

/**
 * Simple [[Ref]] and [[Promise]] backed countdown latch.
 */
private[zio] class CountdownLatch private (count: Ref[Int], done: Promise[Nothing, Unit]) {

  val countDown: UIO[Boolean] = count.modify { n =>
    if (n <= 0) (UIO.succeed(false), 0)
    else if (n == 1) (done.succeed(()).as(true), 0)
    else (UIO.succeed(true), n - 1)
  }.flatten.uninterruptible

  val await: IO[Nothing, Unit] = done.await

}

private[zio] object CountdownLatch {

  def make(n: Int): UIO[CountdownLatch] = {
    val count = Math.max(n, 0)
    Promise.make[Nothing, Unit].flatMap { done =>
      val latch = Ref.make(count).map(counter => new CountdownLatch(counter, done))
      if (count == 0) latch.tap(_ => done.succeed(())) else latch
    }
  }

}
