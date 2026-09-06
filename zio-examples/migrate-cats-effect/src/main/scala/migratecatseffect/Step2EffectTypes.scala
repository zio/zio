package migratecatseffect

import zio._

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Translating Effect Constructors
 *
 * Replaces:
 *   IO(body)      -> ZIO.attempt(body)
 *   IO.pure(a)    -> ZIO.succeed(a)
 *   IO.unit       -> ZIO.unit
 *   IO.never      -> ZIO.never
 *   IO.raiseError -> ZIO.fail
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.Step2EffectTypes"
 */
object Step2EffectTypes extends ZIOAppDefault {

  val fetched: Task[String]   = ZIO.attempt("result from database")
  val constant: UIO[Int]      = ZIO.succeed(42)
  val unit: UIO[Unit]         = ZIO.unit
  val raiseErr: Task[Nothing] = ZIO.fail(new RuntimeException("intentional failure"))

  val program: Task[String] = for {
    a <- ZIO.attempt("hello")
    b <- ZIO.succeed(" world")
  } yield a + b

  def run: Task[Unit] =
    for {
      result  <- program
      _       <- ZIO.succeed(println(s"program: $result"))
      value   <- constant
      _       <- ZIO.succeed(println(s"constant: $value"))
      handled <- raiseErr.catchAll(e => ZIO.succeed(s"caught: ${e.getMessage}"))
      _       <- ZIO.succeed(println(s"raiseErr recovered: $handled"))
    } yield ()
}
