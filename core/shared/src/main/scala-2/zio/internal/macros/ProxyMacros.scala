package zio.internal.macros

import zio.ScopedRef

import scala.reflect.internal.Names
import scala.reflect.macros.blackbox
class ProxyMacros(val c: blackbox.Context) {
  import c.universe._

  def makeImpl[A: c.WeakTypeTag](service: c.Expr[ScopedRef[A]]): c.Expr[A] = {
    val debug = c.inferImplicitValue(c.typeOf[Boolean])

    def log(xs: Any*): Unit =
      debug match {
        case q"true" => println(xs.mkString(", "))
        case _       => ()
      }

    val tpe = c.weakTypeOf[A]

    tpe.members
      .find(m => m.isConstructor)
      .foreach { constructor =>
        if (constructor.asMethod.paramLists.flatten.nonEmpty) {
          c.abort(
            c.enclosingPosition,
            s"Cannot generate a proxy for ${weakTypeOf[A]} due to requiring constructor: ${constructor.name}"
          )
        }
      }

    tpe.members
      .find(m => m.isType && m.isAbstract)
      .map(abstractType =>
        c.abort(
          c.enclosingPosition,
          s"Cannot generate a proxy for ${weakTypeOf[A]} due to Abstract type members: ${abstractType.asType}"
        )
      )

    tpe.members
      .find(m => m.isMethod && m.isAbstract && !(m.asMethod.returnType <:< c.weakTypeOf[zio.ZIO[_, _, _]]))
      .map(nonZIO =>
        c.abort(
          c.enclosingPosition,
          s"Cannot generate a proxy for ${weakTypeOf[A]} due to a non-ZIO method ${nonZIO.name}(...): ${nonZIO.asMethod.returnType}"
        )
      )

    val resultType = appliedType(tpe.typeConstructor, tpe.typeArgs)
    val forwarders = tpe.members.view
      .filter(m => m.isTerm && (m.asMethod.returnType <:< c.weakTypeOf[zio.ZIO[_, _, _]]))
      .map { sym =>
        val m       = sym.asMethod.typeSignatureIn(resultType)
        val tparams = m.typeParams.map(c.internal.typeDef)
        val params = m.paramLists.map(
          _.map { p =>
            val mods = if (p.isImplicit) Modifiers(Flag.IMPLICIT) else Modifiers()
            val tpe  = TypeTree(p.typeSignatureIn(m))
            ValDef(mods, p.name.toTermName, tpe, EmptyTree)
          }
        )

        val args = m.paramLists.map(_.map(p => p.name.toTermName))

        sym.asTerm match {
          case t if t.isVal && t.isAbstract =>
            q"val ${sym.name.toTermName}: ${m.finalResultType} = ${service.tree}.get.flatMap(_.${sym.name.toTermName}(...$args))"
          case t if t.isVal =>
            q"override val ${sym.name.toTermName}: ${m.finalResultType} = ${service.tree}.get.flatMap(_.${sym.name.toTermName}(...$args))"
          case t if t.isAbstract =>
            q"def ${sym.name.toTermName}[..$tparams](...$params): ${m.finalResultType} = ${service.tree}.get.flatMap(_.${sym.name.toTermName}(...$args))"
          case _ =>
            q"override def ${sym.name.toTermName}[..$tparams](...$params): ${m.finalResultType} = ${service.tree}.get.flatMap(_.${sym.name.toTermName}(...$args))"
        }
      }
      .toList

    c.Expr(q"new $resultType { ..$forwarders }")
  }

}
