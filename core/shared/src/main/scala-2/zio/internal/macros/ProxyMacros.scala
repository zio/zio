package zio.internal.macros

import zio.ScopedRef

import scala.reflect.macros.blackbox
class ProxyMacros(val c: blackbox.Context) {
  import c.universe._

  def makeImpl[A: c.WeakTypeTag](service: c.Expr[ScopedRef[A]]): c.Expr[A] = {

    val methods = weakTypeOf[A].decls.collect {
      case m: MethodSymbol if m.isPublic && m.isAbstract => m
    }

    val proxyMethods = methods.map { m =>
      val name       = m.name

      if (!(m.returnType <:< c.weakTypeOf[zio.ZIO[_, _, _]])) {
        c.abort(
          c.enclosingPosition,
          s"Cannot generate a proxy for ${weakTypeOf[A]} due to a non-ZIO method ${name}(...): ${m.returnType}"
        )
      }

      val returnType = if (weakTypeOf[A].typeArgs.nonEmpty) {
        appliedType(m.returnType.typeConstructor, weakTypeOf[A].typeArgs.head.typeSymbol.asType.toType)
      } else {
        m.returnType
      }

      val params = m.paramLists.map(_.map { p =>
        q"${p.name.toTermName}: ${p.typeSignature}"
      })

      q"def $name(...$params): $returnType = ${service.tree}.get.flatMap(_.$name(...${params.map(_.map(_.symbol.name))}))"
    }

    val tree =
      q"""
      new ${weakTypeOf[A]} {
        ..$proxyMethods
      }
    """

    c.Expr[A](tree)
  }

}
