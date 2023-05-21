package zio.internal.macros

import zio._
import scala.reflect.macros.whitebox

private[zio] class ZLayerDerivationMacros(val c: whitebox.Context) {
  import c.universe._

  def deriveImpl[A: WeakTypeTag] = {
    import c.universe._

    val tpe = weakTypeOf[A]
    val ctor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }.getOrElse(c.abort(c.enclosingPosition, s"Failed to derive a ZLayer: type $tpe does not have any constructor."))

    val params = 
      ctor.paramLists.head.map { sym =>
        val depType = sym.typeSignature
        val serviceName = depType.typeSymbol.name

        val (serviceCall, envType) = 
          if (depType <:< typeOf[Promise[_, _]])
            (q"_root_.zio.Promise.make[..${depType.typeArgs}]", None)
          else if (depType <:< typeOf[Queue[_]])
            (q"_root_.zio.Queue.unbounded[..${depType.typeArgs}]", None)
          else
            (q"_root_.zio.ZIO.service[$depType]", Some(depType))

        (sym.name.toTermName, serviceCall, envType)
      }

    val dependencies = params.map { case (name, serviceCall, _) =>
      fq"$name <- $serviceCall"
    }

    val constructorArgs = params.map { case (name, _, _) => q"$name" }

    val envType = params.collect { case (_, _, Some(tpe)) => tpe } match {
      case Nil => tq"Any"
      case ts  => ts.view.map(t => tq"$t").reduce((a, b) => tq"$a with $b")
    }

    dependencies match {
      case Nil => q"_root_.zio.ZLayer.succeed[$tpe](new $tpe())"
      case deps =>
        q"""
          _root_.zio.ZLayer[$envType, Nothing, $tpe] {
            for (..$deps) yield new $tpe(..$constructorArgs)
          }
        """
    }
  }

}
