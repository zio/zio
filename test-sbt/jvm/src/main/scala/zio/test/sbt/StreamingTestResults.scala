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
import zio.stream.UStream

case class TestCollectorState[Line, Result](name: String, output: TRef[Chunk[Line]], result: TRef[Option[Result]])

case class ResultT()

class TestResultManager[Line, Result](state: TRef[Chunk[TestCollectorState[Line, Result]]]) {

  def newCollector: UIO[TestResultCollector[Line, Result]] =
    (for {
      outputRef <- TRef.make(Chunk[Line]())
      result <- TRef.make[Option[Result]](None)
      _ <- {
      val collector = TestCollectorState[Line, Result]("Name", outputRef, result)
      (for {
        tref: TRef[TestCollectorState[Line,Result]] <- TRef.make(collector)
        trefV <- tref.get
        chunk <- state.get
        _ <- state.set(chunk:+trefV)
      } yield new TestResultCollector[Line, Result] {
        // TODO Check my adding `.commit` to satisfy the compiler
        def appendOutput(line: Line): UIO[Unit] =  {
          (for {
            trefState <- tref.get
            _ <- trefState.output.update( outputSoFar => outputSoFar :+ line)
          } yield ()).commit
          // tref.update(state => state.copy(output = state.output :+ line)).commit
        }
        def appendResult(succeeded: Result): UIO[Unit] = 
          (
            for {
              trefState <- tref.get
              _ <- trefState.result.update( _ => Some(succeeded))
          // tref.update(state => state.copy(result = Some(succeeded)))
            } yield ()
      ).commit
      }
      )
      }
    } yield ()).commit
  // Outter stream is Specs, Inner stream is result of a single test
//   def subscribe: UStream[UStream[Either[Line, Result]]] =
  def runAll(onStart: (String)=>UIO[Unit], onProgress: (Line) => UIO[Unit], onComplete: Result => UIO[Unit]): UIO[Unit] = {
    for {
      currentIndex <- TRef.make(0)
    } yield ???
    ???
  }

  

  def complete: UIO[Unit] = ???
}

trait TestResultCollector[Line, Result] {
  def start(name: String): UIO[Unit]
  def progress(line: Line): UIO[Unit]
  def complete(succeeded: Result): UIO[Unit]
}