package scalaz.zio

import org.specs2.ScalaCheck

class RepeatSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends AbstractRTSSpec with GenIO with ScalaCheck {
  def is = "RepeatSpec".title ^ s2"""
   Repeat on success according to a provided strategy
      for 'recurs(a negative number)' repeats 0 additional time $repeatNeg
      for 'recurs(0)' does repeats 0 additional time $repeat0
      for 'recurs(1)' does repeats 1 additional time $repeat1
      for 'once' does repeats 1 additional time $once
      for 'recurs(a positive given number)' repeats that additional number of time $repeatN
   Repeat on failure does not actually repeat $repeatFail
   Repeat a scheduled repeat repeats the whole number $repeatRepeat
    """

  val repeat: Int => IO[Nothing, Int] = (n: Int) =>
    for {
      ref <- Ref(0)
      s   <- ref.update(_ + 1).repeat(Schedule.recurs(n))
    } yield s

  /*
   * A repeat with a negative number of times should not repeat the action at all
   */
  def repeatNeg = {
    val repeated = unsafeRun(repeat(-5))
    repeated must_=== 1
  }

  /*
   * A repeat with 0 number of times should not repeat the action at all
   */
  def repeat0 = {
    val n        = 0
    val repeated = unsafeRun(repeat(n))
    repeated must_=== 1
  }

  def never = {
    val repeated = unsafeRun(for {
      ref <- Ref(0)
      _   <- ref.update(_ + 1).repeat(Schedule.never)
      res <- ref.get
    } yield res)

    repeated must_=== 1
  }

  def repeat1 = {
    val n        = 1
    val repeated = unsafeRun(repeat(n))
    repeated must_=== n + 1
  }

  def once = {
    val repeated = unsafeRun(for {
      ref <- Ref(0)
      _   <- ref.update(_ + 1).repeat(Schedule.once)
      res <- ref.get
    } yield res)

    repeated must_=== 2
  }

  def repeatN = {
    val n        = 42
    val repeated = unsafeRun(repeat(n))
    repeated must_=== n + 1
  }

  def repeatRepeat = {
    val n = 42
    val repeated = unsafeRun(for {
      ref <- Ref(0)
      io  = ref.update(_ + 1).repeat(Schedule.recurs(n))
      _   <- io.repeat(Schedule.recurs(1))
      res <- ref.get
    } yield res)
    repeated must_=== (n + 1) * 2
  }

  def repeatFail = {
    // a method that increment ref and fail with the incremented value in error message
    def incr(ref: Ref[Int]): IO[String, Int] =
      for {
        i <- ref.update(_ + 1)
        _ <- IO.fail(s"Error: $i")
      } yield i

    val repeated = unsafeRun(
      (for {
        ref <- Ref(0)
        _   <- incr(ref).repeat(Schedule.recurs(42))
      } yield ()).redeem(
        err => IO.succeed(err),
        _ => IO.succeed("it should not be a success at all")
      )
    )

    repeated must_=== "Error: 1"
  }
}
