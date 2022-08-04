package zio.test

//import zio.Console.printLine

import zio._

import java.util.concurrent.atomic.AtomicReference
//import zio.test.{TestFailure, TestSuccess}

/*
  Fiber Refs
    - Current Path
      - List of Strings
      - Empty initially, add new value each time you go deeper
    - Output
 */

case class TestOutputZ(lines: Chunk[String]) {
  println("Constructing TestOutputZ")
}

object  TestOutputZ {
  def log(ref: FiberRef[TestOutputZ])(string: String): UIO[Unit] = {
    println("Should add this output: " + string)

    ref.update{log =>
      println("lines before update: " + log.lines.mkString(","))
      log.copy(lines = log.lines :+ string)
    }
  }
}

case class TestOutputAppender(
//                               output: Ref[Seq[String]]
                             ) {
  println("Made an appender")
  var output: AtomicReference[Seq[String]] = new AtomicReference(Seq())
  def add(line: String) = {
    output.updateAndGet(x => x.appended(line))
//    output.set(output.get().appended(line))
//    output.update(_.appended(line)) *>
    println("Should store this: " + line)
    this
  }

  val getOutput =
    ZIO.succeed(output.get)

}
object TestOutputAppender {
//  def make() =
//    for {
//      output <- Ref.make[Seq[String]](Seq())
//    } yield TestOutputAppender(output)
}

case class TFailure()
case class TSuccess()

object FiberRefTestOutput {
  val appenderRef = FiberRef.unsafe.make[TestOutputAppender](
    TestOutputAppender(),
    x => {
      println("Forking/copying Appender")
      x
    },
//    ZIO.identityFn,
    { case (old, newX) =>
      println("Joining appender")
      old
    }
  )(Unsafe.unsafe)

  val outputRef: FiberRef[TestOutputZ] = FiberRef.unsafe.make[TestOutputZ](
    TestOutputZ(Chunk.empty)
  )(Unsafe.unsafe)

}


object RefDemo extends ZIOAppDefault {
  def run = {
    for {
      _ <- ZIO.unit

    } yield ()
  }
}