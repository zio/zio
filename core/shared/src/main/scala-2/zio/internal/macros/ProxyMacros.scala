package zio.internal.macros

import zio.ScopedRef

import scala.reflect.macros.blackbox
class ProxyMacros(val c: blackbox.Context) {
  import c.universe._

  def makeImpl[A: c.WeakTypeTag](service: c.Expr[ScopedRef[A]], debug: c.Expr[Boolean]): c.Expr[A] = {
    val tpe = c.weakTypeOf[A]

    def log(xs: Any*): Unit =
      debug.tree match {
        case q"true" => println(xs.mkString(", "))
        case _       => ()
      }

    val resultType = appliedType(tpe.typeConstructor, tpe.typeArgs)
    val forwarders = tpe.members.view
      .filter(m => m.isMethod && (m.asMethod.returnType <:< c.weakTypeOf[zio.ZIO[_, _, _]]))
      .map { sym =>
        if (sym.asMethod.isVal)
          c.abort(
            c.enclosingPosition,
            s"Cannot generate a proxy for ${weakTypeOf[A]} due to a val method ${sym.name}(...)"
          )

        val m = sym.asMethod.typeSignatureIn(resultType)
        log(sym.name)
        val tparams = m.typeParams.map(c.internal.typeDef)
        val params = m.paramLists.map(
          _.map { p =>
            val mods = if (p.isImplicit) Modifiers(Flag.IMPLICIT) else Modifiers()
            val tpe  = TypeTree(p.typeSignatureIn(m))
            ValDef(mods, p.name.toTermName, tpe, EmptyTree)
          }
        )

        val args = m.paramLists.map(_.map(p => p.name.toTermName))

        if (sym.asMethod.isAbstract)
          q"def ${sym.name.toTermName}[..$tparams](...$params): ${m.finalResultType} = ${service.tree}.get.flatMap(_.${sym.name.toTermName}(...$args))"
        else
          q"override def ${sym.name.toTermName}[..$tparams](...$params): ${m.finalResultType} = ${service.tree}.get.flatMap(_.${sym.name.toTermName}(...$args))"
      }
      .toList

    c.Expr(q"new $resultType { ..$forwarders }")
  }

}
