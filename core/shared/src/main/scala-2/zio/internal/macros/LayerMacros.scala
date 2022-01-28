package zio.internal.macros

import zio._
import zio.internal.TerminalRendering
import zio.internal.ansi.AnsiStringOps

import scala.reflect.macros.blackbox

private[zio] class LayerMacros(val c: blackbox.Context) extends LayerMacroUtils {
  import c.universe._

  def validate[Provided: WeakTypeTag, Required: WeakTypeTag](zio: c.Tree): c.Tree = {

    val required = getRequirements[Required]
    val provided = getRequirements[Provided]

    val missing =
      required.toSet -- provided.toSet

    if (missing.nonEmpty) {
      val message = TerminalRendering.missingLayersForZIOApp(missing.map(_.toString))
      c.abort(c.enclosingPosition, message)
    }

    zio
  }

  def provideImpl[F[_, _, _], R: c.WeakTypeTag, E, A](
    layer: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[F[Any, E, A]] =
    provideBaseImpl[F, Any, R, E, A](layer, "provideLayer")

  // ZIO[R, E, A]
  // ZManaged[R, E, A]
  // ZStream[R, E, A]
  // R0 remainder
  def provideSomeImpl[F[_, _, _], R0: c.WeakTypeTag, R: c.WeakTypeTag, E, A](
    layer: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[F[R0, E, A]] =
//    assertEnvIsNotNothing[R0]()
    provideBaseImpl[F, R0, R, E, A](layer, "provideLayer", true)

  def debugGetRequirements[R: c.WeakTypeTag]: c.Expr[List[String]] =
    c.Expr[List[String]](q"${getRequirements[R]}")

  def debugShowTree(any: c.Tree): c.Expr[String] = {
    val string = CleanCodePrinter.show(c)(any)
    c.Expr[String](q"$string")
  }

}

private[zio] object MacroUnitTestUtils {
  def getRequirements[R]: List[String] = macro LayerMacros.debugGetRequirements[R]

  def showTree(any: Any): String = macro LayerMacros.debugShowTree
}
