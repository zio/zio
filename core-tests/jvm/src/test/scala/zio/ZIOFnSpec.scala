package zio

import zio.internal.stacktracer.ZTraceElement
import zio.internal.stacktracer.ZTraceElement.{ NoLocation, SourceLocation }
import zio.internal.stacktracer.impl.AkkaLineNumbersTracer
import zio.internal.tracing.ZIOFn
import zio.test.Assertion._
import zio.test._

object ZIOFnSpec extends ZIOBaseSpec {

  val x: UIO[Int] = UIO.succeed(1)
  val y: UIO[Int] = UIO.succeed(2)

  val tracer = new AkkaLineNumbersTracer

  //wrapper for clickable source locations in test failure reports
  case class Locations[+A <: SourceLocation](underlying: List[A]) extends Iterable[A] {
    override def toString: String = underlying.map(zte => s"    ${zte.prettyPrint}").mkString("\n").trim + "\n"

    override def iterator: Iterator[A] = underlying.iterator
  }

  def findInternal(trace: List[ZTraceElement]): UIO[List[SourceLocation]] =
    ZIO.succeed(trace.collect { case sl: SourceLocation if sl.file != "ZIOFnSpec.scala" => sl })

  def checkTrace[A](zio: => UIO[A]): ZIO[Any, NoLocation, BoolAlgebra[FailureDetails]] =
    for {
      trace <- zio *> ZIO.trace
      callSite <- tracer.traceLocation(ZIOFn.unwrap(zio)) match {
                    case nl: NoLocation => ZIO.fail(nl)
                    case SourceLocation(file, _, _, line) => {
                      val _ = file
                      ZIO.succeed(line)
                    }
                  }
      internalExecution <- findInternal(trace.executionTrace)
      internalStack     <- findInternal(trace.stackTrace)
      externalExecutionLines = trace.executionTrace.collect { case SourceLocation("ZIOFnSpec.scala", _, _, line) =>
                                 line
                               }
    } yield {
      val internalExecutionLocations = Locations(internalExecution)
      val internalStackLocations     = Locations(internalStack)
      assert(internalStackLocations)(contains(callSite)) &&
      assert(internalStackLocations)(isEmpty ?? "Internal stack must be hidden") &&
      assert(internalExecutionLocations)(isEmpty ?? "Internal execution must be hidden")
    }

  def spec: ZSpec[Environment, Failure] = suite("ZIO internals are hidden")(
    testM("&&&")(checkTrace(x &&& y)),
    testM("&>")(checkTrace(x &> y)),
    testM("*>")(checkTrace(x *> y)),
    testM("flatMap")(checkTrace(x flatMap (_ => y)))
  )
}
