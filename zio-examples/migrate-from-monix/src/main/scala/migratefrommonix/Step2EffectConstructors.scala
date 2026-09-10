package migratefrommonix

import zio._

/**
 * Guide: Migrate from Monix to ZIO
 * Section: Translating Effect Constructors + Coeval
 *
 * sbt "migrate-from-monix/runMain migratefrommonix.Step2EffectConstructors"
 */
object Step2EffectConstructors extends ZIOAppDefault {
  def run: Task[Unit] = {
    val fetched    = ZIO.attempt("result from database")
    val constant   = ZIO.succeed(42)
    val unit       = ZIO.unit
    val raiseErr   = ZIO.fail(new RuntimeException("oops"))
    val fromEither = ZIO.fromEither(Right(1): Either[Throwable, Int])
    val fromTry    = ZIO.fromTry(scala.util.Try(1))
    val slept      = ZIO.sleep(10.millis)
    val deferred   = ZIO.suspend(ZIO.attempt("deferred"))

    // Coeval replacement: ZIO.succeed for pure sync computation
    val syncValue: UIO[Int] = ZIO.succeed(42 * 2)

    for {
      f  <- fetched
      c  <- constant
      _  <- unit
      _  <- fromEither
      _  <- fromTry
      _  <- slept
      d  <- deferred
      cv <- syncValue
      _  <- ZIO.succeed(println(s"fetched=$f constant=$c coeval=$cv deferred=$d"))
    } yield ()
  }
}
