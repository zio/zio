package scalanative

import zio._
import zio.stream._

/** Putting It Together — Complete ZIO + Scala Native Example
  *
  * Combines every guide step: the Job type (shared with Main.scala), the
  * Recorder ZLayer service, a ZStream processing pipeline, and a ZIO.foreach
  * result-collection loop.
  *
  * Job and Recorder are defined in Main.scala (same package) and reused here.
  *
  * Run with: sbt "runMain scalanative.CompleteExample"
  */
object CompleteExample extends ZIOAppDefault {

  val jobs: List[Job] = List(
    Job(1, "compile"),
    Job(2, "test"),
    Job(3, "package")
  )

  // ZIO.foreach loop — collects job names in order (mirrors the test spec pattern)
  val collectNames: ZIO[Any, Nothing, List[String]] =
    ZIO.foreach(jobs)(job => ZIO.succeed(job.name))

  def processJob(job: Job): ZIO[Recorder, Nothing, Unit] =
    for {
      now      <- Clock.currentDateTime
      _        <- Console.printLine(s"[$now] Processing job ${job.id}: ${job.name}").orDie
      recorder <- ZIO.service[Recorder]
      _        <- recorder.record(job.name)
    } yield ()

  // ZStream pipeline — processes jobs sequentially via the Recorder ZLayer service
  val pipeline: ZIO[Recorder, Nothing, Unit] =
    ZStream
      .fromIterable(jobs)
      .mapZIO(processJob)
      .runDrain *>
      ZIO.serviceWithZIO[Recorder](_.total).flatMap(n =>
        Console.printLine(s"Completed $n jobs").orDie
      )

  def run: ZIO[Any, Any, Any] =
    for {
      names <- collectNames
      _     <- Console.printLine(s"Jobs to process: ${names.mkString(", ")}").orDie
      _     <- pipeline.provide(Recorder.inMemory)
    } yield ()
}
