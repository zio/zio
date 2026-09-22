package scalanative

import zio._
import zio.stream._

/** Step 3 — Write and Run a ZIO Program
  *
  * Processes a list of jobs through a ZStream, records them via a ZLayer-provided
  * Recorder service, and prints a completion summary.
  *
  * Run with: sbt "runMain scalanative.Main"
  */

case class Job(id: Int, name: String)

trait Recorder {
  def record(name: String): UIO[Unit]
  def total: UIO[Int]
}

object Recorder {
  val inMemory: ZLayer[Any, Nothing, Recorder] =
    ZLayer.fromZIO(
      Ref.make(0).map(counter =>
        new Recorder {
          def record(name: String): UIO[Unit] = counter.update(_ + 1)
          def total: UIO[Int]                 = counter.get
        }
      )
    )
}

object Main extends ZIOAppDefault {

  val jobs: List[Job] = List(
    Job(1, "compile"),
    Job(2, "test"),
    Job(3, "package")
  )

  def processJob(job: Job): ZIO[Recorder, Nothing, Unit] =
    for {
      now      <- Clock.currentDateTime
      _        <- Console.printLine(s"[$now] Processing job ${job.id}: ${job.name}").orDie
      recorder <- ZIO.service[Recorder]
      _        <- recorder.record(job.name)
    } yield ()

  val program: ZIO[Recorder, Nothing, Unit] =
    ZStream
      .fromIterable(jobs)
      .mapZIO(processJob)
      .runDrain *>
      ZIO.serviceWithZIO[Recorder](_.total).flatMap(n =>
        Console.printLine(s"Completed $n jobs").orDie
      )

  def run: ZIO[Any, Any, Any] =
    program.provide(Recorder.inMemory)
}
