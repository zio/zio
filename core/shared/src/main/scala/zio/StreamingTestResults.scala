package zio.test.sbt

/*
  Datastructure that accepts results from Test Suites; only 1 spec can acquire at any time.
  Opens ports for each Spec, and accepts strings from 1 at a time.
  sealed trait SpecResult
  object SpecSummary  extends SpecResult
  object SpecsComplete  extends SpecResult
 */

import zio.stm._
import zio._
// import zio.stream.UStream

case class TestCollectorState(name: String, output: TRef[Chunk[String]], result: TRef[Option[ResultT]])

case class ResultT()

class TestResultManager(state: TRef[Chunk[TestCollectorState]]) {

  def newCollector: UIO[TestResultCollector] =
    (for {
      outputRef <- TRef.make(Chunk[String]())
      result    <- TRef.make[Option[ResultT]](None)
      collector <- {
        val collector = TestCollectorState("Name", outputRef, result)
        (for {
          tref: TRef[TestCollectorState] <- TRef.make(collector)
          trefV                          <- tref.get
          chunk                          <- state.get
          _                              <- state.set(chunk :+ trefV)
        } yield new TestResultCollector {
          def start(name: String): UIO[Unit] = ???
          // TODO Check my adding `.commit` to satisfy the compiler
          def progress(line: String): UIO[Unit] =
            (for {
              trefState <- tref.get
              _         <- trefState.output.update(outputSoFar => outputSoFar :+ line)
            } yield ()).commit
          // tref.update(state => state.copy(output = state.output :+ line)).commit
          def complete(succeeded: ResultT): UIO[Unit] =
            (
              for {
                trefState <- tref.get
                _         <- trefState.result.update(_ => Some(succeeded))
                // tref.update(state => state.copy(result = Some(succeeded)))
              } yield ()
            ).commit
        })
      }
    } yield (collector)).commit
  // Outter stream is Specs, Inner stream is result of a single test
//   def subscribe: UStream[UStream[Either[Line, Result]]] =
  def runAll(
    onStart: (String) => UIO[Unit],
    onProgress: (String) => UIO[Unit],
    onComplete: ResultT => UIO[Unit]
  ): UIO[Unit] = {
    println(onStart)
    println(onProgress)
    println(onComplete)
    for {
      currentIndex <- TRef.make(0).commit
      _            <- ZIO.debug(currentIndex)
    } yield ???
  }

  def complete: UIO[Unit] = ???
}

trait TestResultCollector {
  def start(name: String): UIO[Unit]
  def progress(line: String): UIO[Unit]
  def complete(succeeded: ResultT): UIO[Unit]
}
