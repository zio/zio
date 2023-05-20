package zio.internal.macros

import scala.reflect.macros.whitebox

private[zio] class ZLayerDerivationMacros(val c: whitebox.Context) {
  import c.universe._

  def deriveImpl[A: WeakTypeTag] = {
    import c.universe._

    val tpe = weakTypeOf[A]
    val ctor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.get

    val params = ctor.paramLists.head.map { sym =>
      val serviceName = sym.typeSignature.typeSymbol.name
      val serviceCall = q"_root_.zio.ZIO.service[${sym.typeSignature}]"
      (sym.name.toTermName, serviceCall)
    }

    val assignments = params.map { case (name, serviceCall) =>
      fq"$name <- $serviceCall"
    }

    val constructorArgs = params.map { case (name, _) => q"$name" }

    val envType = ctor.paramLists.head match {
      case Nil => tq"Any"
      case ps  => ps.view.map(sym => tq"${sym.typeSignature}").reduce((a, b) => tq"$a with $b")
    }

    q"""
      _root_.zio.ZLayer[$envType, Nothing, $tpe] {
        for (..$assignments) yield new $tpe(..$constructorArgs)
      }
    """
  }

}
