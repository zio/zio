package fix

import scalafix.v1._
import scala.meta._

class HaltToFailCause extends SemanticRule("HaltToFailCause") {

  override def fix(implicit doc: SemanticDocument): Patch = {
    val halt = SymbolMatcher.normalized("zio.Exit.halt", "zio.ZIO.halt", "zio.IO.halt", "zio.Managed.halt", "zio.RIO.halt",
      "zio.Task.halt", "zio.UIO.halt", "zio.URIO.halt", "zio.ZManaged.halt", "zio.Fiber.halt", "zio.Promise.halt",
      "zio.ZSink.halt", "zio.ZStream.halt", "zio.stream.experimental.ZChannel.halt", "zio.stream.experimental.Take.halt",
      "zio.stream.experimental.ZPipeline.halt", "zio.test.TestFailure.halt"
    )
    val timeoutHalt = SymbolMatcher.normalized("zio.ZIO.timeoutHalt")
    val haltWith = SymbolMatcher.normalized("zio.ZIO.haltWith", "zio.IO.haltWith", "zio.RIO.haltWith", "zio.Task.haltWith",
      "zio.UIO.haltWith", "zio.URIO.haltWith")

    doc.tree.collect {
      case halt(name@Name(_)) =>
        Patch.renameSymbol(name.symbol, "failCause")

      case haltWith(name @Name(_)) =>
        Patch.renameSymbol(name.symbol, "failCauseWith")

      case timeoutHalt(name@Name(_)) =>
        Patch.renameSymbol(name.symbol, "timeoutFailCause")
    }.asPatch
  }
}
