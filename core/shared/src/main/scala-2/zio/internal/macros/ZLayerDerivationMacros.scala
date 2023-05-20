package zio.internal.macros

import scala.reflect.macros.whitebox

private[zio] class ZLayerDerivationMacros(val c: whitebox.Context) {
  import c.universe._

  def deriveImpl[A: WeakTypeTag] = {
    import c.universe._

    val tpe = weakTypeOf[A]
    val ctor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(c.abort(c.enclosingPosition, s"Failed to derive a ZLayer: type $tpe does not have any constructor."))

    val params = ctor.paramLists.head.map { sym =>
      val serviceName = sym.typeSignature.typeSymbol.name
      val serviceCall = q"_root_.zio.ZIO.service[${sym.typeSignature}]"
      (sym.name.toTermName, serviceCall)
    }

    val services = params.map { case (name, serviceCall) =>
      fq"$name <- $serviceCall"
    }

    val constructorArgs = params.map { case (name, _) => q"$name" }

    val envType = ctor.paramLists.head match {
      case Nil => tq"Any"
      case ps  => ps.view.map(sym => tq"${sym.typeSignature}").reduce((a, b) => tq"$a with $b")
    }

    services match {
      case Nil => q"_root_.zio.ZLayer.succeed[$tpe](new $tpe())"
      case ss =>
        q"""
          _root_.zio.ZLayer[$envType, Nothing, $tpe] {
            for (..$ss) yield new $tpe(..$constructorArgs)
          }
        """
    }
  }

}
