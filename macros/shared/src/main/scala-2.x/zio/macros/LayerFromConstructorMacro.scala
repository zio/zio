package zio.macros

import scala.annotation.nowarn
import scala.reflect.macros.whitebox

object LayerFromConstructorMacro {
  @nowarn
  def materialize[From <: Out: c.WeakTypeTag, Out: c.WeakTypeTag, In: c.WeakTypeTag](
    c: whitebox.Context
  ): c.universe.Tree = {
    import c.universe._

    def abort(msg: String) =
      c.abort(c.enclosingPosition, msg)

    val fromTpe = weakTypeOf[From]
    val outTpe  = weakTypeOf[Out]

    val constructor = fromTpe.decls.collectFirst {
      case x if x.isMethod && x.isConstructor => x.asMethod
    }
      .getOrElse(abort(s"Can't find constructor of $fromTpe"))

    val nameTypePairs = constructor.paramLists.headOption.getOrElse(List.empty).map { p =>
      (p.name, p.asTerm.typeSignature)
    }

    val inTpe =
      if (nameTypePairs.nonEmpty)
        tq"..${nameTypePairs.map(nt => tq"_root_.zio.Has[${nt._2}]")} { }"
      else
        tq"Any"

    val extractors =
      if (nameTypePairs.nonEmpty)
        nameTypePairs.map { case (name, tpe) =>
          fq"$name <- _root_.zio.ZIO.service[$tpe]"
        }
      else
        List(fq"_ <- _root_.zio.UIO.unit")

    val constructorParams = nameTypePairs.map(_._1)

    val clsName = TypeName(c.freshName("anon$"))
    q"""
      final class $clsName extends _root_.zio.macros.LayerFromConstructor[$fromTpe, $outTpe] {
        type In = $inTpe
        def layer: _root_.zio.ZLayer[In, Nothing, _root_.zio.Has[$outTpe]] = {
          for (..$extractors) yield new $fromTpe(..$constructorParams)
        }.toLayer
      }
      new $clsName(): _root_.zio.macros.LayerFromConstructor.Aux[$fromTpe, $outTpe, $inTpe]
    """
  }
}
