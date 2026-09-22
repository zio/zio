package migratecatseffect.catseffect

import cats.effect.{IO, IOApp}

/**
 * Guide: Migrate from Cats Effect to ZIO
 * Section: Translating Effect Constructors
 *
 * The "before" side of migratecatseffect.Step2EffectTypes.
 *
 * sbt "migrate-cats-effect/runMain migratecatseffect.catseffect.Step2EffectTypes"
 */
object Step2EffectTypes extends IOApp.Simple {

  val fetched: IO[String]   = IO("result from database")
  val constant: IO[Int]     = IO.pure(42)
  val unit: IO[Unit]        = IO.unit
  val raiseErr: IO[Nothing] = IO.raiseError(new RuntimeException("intentional failure"))

  val program: IO[String] = for {
    a <- IO("hello")
    b <- IO.pure(" world")
  } yield a + b

  def run: IO[Unit] =
    for {
      result  <- program
      _       <- IO(println(s"program: $result"))
      value   <- constant
      _       <- IO(println(s"constant: $value"))
      handled <- raiseErr.handleErrorWith(e => IO(s"caught: ${e.getMessage}"))
      _       <- IO(println(s"raiseErr recovered: $handled"))
    } yield ()
}
