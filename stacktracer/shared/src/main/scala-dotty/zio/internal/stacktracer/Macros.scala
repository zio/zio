package zio.internal.stacktracer

import com.github.ghik.silencer.silent
import zio.internal.stacktracer.Tracer.createTrace
import zio.stacktracer.DisableAutoTrace

import scala.quoted._

@silent
object Macros {

  def traceInfo(using ctx: Quotes): String = {

    val location = {
      def loop(current: quotes.reflect.Symbol, acc: List[String] = Nil): List[String] = {
        val currentName = current.name.toString.trim
        if (currentName != "<root>")
          loop(current.owner, if (currentName == "$anonfun") acc else currentName :: acc)
        else acc
      }

      loop(quotes.reflect.Symbol.spliceOwner).mkString(".")
    }

    val pos    = quotes.reflect.Position.ofMacroExpansion
    val file   = pos.sourceFile.jpath.toString
    val line   = pos.startLine
    val column = pos.startColumn
    createTrace(location, file, line, column)
  }

  private def traceExpr(trace: String)(using ctx: Quotes): Expr[Tracer.instance.Type] =
    Expr(trace).asInstanceOf[Expr[Tracer.instance.Type]]

  def newTraceImpl(using ctx: Quotes): Expr[Tracer.instance.Type] =
    traceExpr(traceInfo)

  def autoTraceImpl(using ctx: Quotes): Expr[Tracer.instance.Type] =
    Expr(Tracer.instance.empty.asInstanceOf[String]).asInstanceOf[Expr[Tracer.instance.Type]]
}
