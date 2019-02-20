package scalaz.zio

class PromiseSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime {

  def is = "PromiseSpec".title ^ s2"""
        Make a promise and retrieve its value correctly after complete it with:
          `complete` to complete that promise with a specified value. $e1
          `done` to complete that promise with a completed result.    $e2

        Make a promise and retrieve its fail value after complete it with:
          `error` to fail that promise with a specified error.  $e3
          `done` to complete that promise with a failed result. $e4

        Given a completed promise `done` returns false and get should return the first completed value. $e5

        Make a promise and retrieve its Throwable value after interruption calling:
          `done` to complete that promise with a terminated result.              $e6
          `interrupt` and interrupt all other fibers.                            $e7

        poll retrieves the final status immediately
          it `fails' with Unit ` if the promise is not done yet.  $e8
          Otherwise, it returns the `Exit`, whether
            `succeeded`                                           $e9
            `failed`                                              $e10
            `interrupted`.                                         $e11
     """

  def e1 =
    unsafeRun(
      for {
        p <- Promise.make[Nothing, Int]
        s <- p.succeed(32)
        v <- p.await
      } yield s must beTrue and (v must_=== 32)
    )

  def e2 =
    unsafeRun(
      for {
        p <- Promise.make[Nothing, Int]
        s <- p.done(IO.succeed(14))
        v <- p.await
      } yield s must beTrue and (v must_=== 14)
    )

  def e3 =
    unsafeRun(
      for {
        p <- Promise.make[String, Int]
        s <- p.fail("error in e3")
        v <- p.await.attempt
      } yield s must beTrue and (v must_=== Left("error in e3"))
    )

  def e4 =
    unsafeRun(
      for {
        p <- Promise.make[String, Int]
        s <- p.done(IO.fail("error in e4"))
        v <- p.await.attempt
      } yield s must beTrue and (v must_=== Left("error in e4"))
    )

  def e5 =
    unsafeRun(
      for {
        p <- Promise.make[Nothing, Int]
        _ <- p.succeed(1)
        s <- p.done(IO.succeed(9))
        v <- p.await
      } yield s must beFalse and (v must_=== 1)
    )

  def e6 =
    unsafeRun(
      for {
        p <- Promise.make[Exception, Int]
        s <- p.interrupt
      } yield s must beTrue
    )
  def e7 =
    unsafeRun(
      for {
        p <- Promise.make[Exception, Int]
        s <- p.interrupt
      } yield s must beTrue
    )

  def e8 =
    unsafeRun(
      for {
        p       <- Promise.make[String, Int]
        attempt <- p.poll.get.attempt
      } yield attempt must beLeft(())
    )

  def e9 =
    unsafeRun {
      for {
        p      <- Promise.make[String, Int]
        _      <- p.succeed(12)
        result <- p.poll.get.flatMap(_.run)
      } yield result must_=== Exit.succeed(12)
    }

  def e10 =
    unsafeRun {
      for {
        p      <- Promise.make[String, Int]
        _      <- p.fail("failure")
        result <- p.poll.get.flatMap(_.run)
      } yield result must_=== Exit.fail("failure")
    }

  def e11 =
    unsafeRun {
      for {
        p             <- Promise.make[String, Int]
        _             <- p.interrupt
        attemptResult <- p.poll.get.flatMap(_.run)
      } yield attemptResult must_=== Exit.interrupt
    }

}
