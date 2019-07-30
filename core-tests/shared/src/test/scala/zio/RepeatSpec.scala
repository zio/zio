package zio

import zio.clock.Clock

class RepeatSpec extends BaseCrossPlatformSpec {
  def is = "RepeatSpec".title ^ s2"""
   Repeat on success according to a provided strategy
      for 'recurs(a negative number)' repeats 0 additional time $repeatNeg
      for 'recurs(0)' does repeats 0 additional time $repeat0
      for 'recurs(1)' does repeats 1 additional time $repeat1
      for 'once' does repeats 1 additional time $once
      for 'recurs(a positive given number)' repeats that additional number of time $repeatN
      for 'doWhile(cond)' repeats while the cond still holds $repeatWhile
      for 'doWhileM(cond)' repeats while the effectul cond still holds $repeatWhileM
      for 'doUntil(cond)' repeats until the cond is satisfied $repeatUntil
      for 'doUntilM(cond)' repeats until the effectful cond is satisfied $repeatUntilM
   Repeat on failure does not actually repeat $repeatFail
   Repeat a scheduled repeat repeats the whole number $repeatRepeat

   Repeat an action 2 times and call `ensuring` should
      run the specified finalizer as soon as the schedule is complete $ensuring
    """

  val repeat: Int => ZIO[Clock, Nothing, Int] = (n: Int) =>
    for {
      ref <- Ref.make(0)
      s   <- ref.update(_ + 1).repeat(Schedule.recurs(n).immediately)
    } yield s

  /*
   * A repeat with a negative number of times should not repeat the action at all
   */
  def repeatNeg =
    repeat(-5).map(x => x must_=== 1)

  /*
   * A repeat with 0 number of times should not repeat the action at all
   */
  def repeat0 =
    repeat(0).map(x => x must_=== 1)

  def never =
    for {
      ref <- Ref.make(0)
      _   <- ref.update(_ + 1).repeat(Schedule.never.immediately)
      res <- ref.get
    } yield res must_=== 1

  def repeat1 =
    repeat(1).map(x => x must_=== 2)

  def once =
    for {
      ref <- Ref.make(0)
      _   <- ref.update(_ + 1).repeat(Schedule.once.immediately)
      res <- ref.get
    } yield res must_=== 2

  def repeatN =
    repeat(42).map(x => x must_=== 42 + 1)

  def repeatRepeat = {
    val n = 42
    for {
      ref <- Ref.make(0)
      io  = ref.update(_ + 1).repeat(Schedule.recurs(n).immediately)
      _   <- io.repeat(Schedule.recurs(1).immediately)
      res <- ref.get
    } yield res must_=== (n + 1) * 2
  }

  def repeatFail = {
    // a method that increment ref and fail with the incremented value in error message
    def incr(ref: Ref[Int]): IO[String, Int] =
      for {
        i <- ref.update(_ + 1)
        _ <- IO.fail(s"Error: $i")
      } yield i

    val repeated =
      (for {
        ref <- Ref.make(0)
        _   <- incr(ref).repeat(Schedule.recurs(42).immediately)
      } yield ()).foldM(
        err => IO.succeed(err),
        _ => IO.succeed("it should not be a success at all")
      )

    repeated.map(x => x must_=== "Error: 1")
  }

  def repeatUntil = {
    def cond: Int => Boolean = _ < 10
    for {
      ref <- Ref.make(0)
      i   <- ref.update(_ + 1).repeat(Schedule.doUntil(cond).immediately)
    } yield i must_=== 1
  }

  def repeatUntilM = {
    def cond: Int => UIO[Boolean] = x => IO.succeed(x > 10)
    for {
      ref <- Ref.make(0)
      i   <- ref.update(_ + 1).repeat(Schedule.doUntilM(cond).immediately)
    } yield i must_=== 11
  }

  def repeatWhile = {
    def cond: Int => Boolean = _ < 10
    for {
      ref <- Ref.make(0)
      i   <- ref.update(_ + 1).repeat(Schedule.doWhile(cond).immediately)
    } yield i must_=== 10
  }

  def repeatWhileM = {
    def cond: Int => UIO[Boolean] = x => IO.succeed(x > 10)
    for {
      ref <- Ref.make(0)
      i   <- ref.update(_ + 1).repeat(Schedule.doWhileM(cond).immediately)
    } yield i must_=== 1
  }

  def ensuring =
    for {
      p          <- Promise.make[Nothing, Unit]
      r          <- Ref.make(0)
      _          <- r.update(_ + 2).repeat(Schedule.recurs(2).immediately).ensuring(p.succeed(()))
      v          <- r.get
      finalizerV <- p.poll
    } yield (v must_=== 6) and (finalizerV.isDefined must beTrue)
}
