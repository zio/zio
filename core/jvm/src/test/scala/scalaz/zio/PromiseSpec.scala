package scalaz.zio

class PromiseSpec extends AbstractRTSSpec {

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
          Otherwise, it returns the `ExitResult`, whether
            `Completed`                                           $e9
            `Failed`                                              $e10
            `Terminated`.                                         $e11
     """

  def e1 =
    unsafeRun(
      for {
        p <- Promise.make[Nothing, Int]
        s <- p.complete(32)
        v <- p.get
      } yield s must beTrue and (v must_=== 32)
    )

  def e2 =
    unsafeRun(
      for {
        p <- Promise.make[Nothing, Int]
        s <- p.done(ExitResult.Completed(14))
        v <- p.get
      } yield s must beTrue and (v must_=== 14)
    )

  def e3 =
    unsafeRun(
      for {
        p <- Promise.make[String, Int]
        s <- p.error("error in e3")
        v <- p.get.attempt
      } yield s must beTrue and (v must_=== Left("error in e3"))
    )

  def e4 =
    unsafeRun(
      for {
        p <- Promise.make[String, Int]
        s <- p.done(ExitResult.checked("error in e4"))
        v <- p.get.attempt
      } yield s must beTrue and (v must_=== Left("error in e4"))
    )

  def e5 =
    unsafeRun(
      for {
        p <- Promise.make[Nothing, Int]
        _ <- p.complete(1)
        s <- p.done(ExitResult.Completed(9))
        v <- p.get
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
        attempt <- p.poll.attempt
      } yield attempt must beLeft(())
    )

  def e9 =
    unsafeRun {
      for {
        p      <- Promise.make[String, Int]
        _      <- p.complete(12)
        result <- p.poll
      } yield result must_=== ExitResult.Completed(12)
    }

  def e10 =
    unsafeRun {
      for {
        p      <- Promise.make[String, Int]
        _      <- p.error("failure")
        result <- p.poll
      } yield result must_=== ExitResult.checked("failure")
    }

  def e11 =
    unsafeRun {
      val error1 = new IllegalArgumentException("arg")
      val error2 = new ArrayIndexOutOfBoundsException("index")
      for {
        p             <- Promise.make[String, Int]
        _             <- p.interrupt(error1, error2)
        attemptResult <- p.poll
      } yield attemptResult must_=== ExitResult.interrupted(List(error1, error2))
    }

}
