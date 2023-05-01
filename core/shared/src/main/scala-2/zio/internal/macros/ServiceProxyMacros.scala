package zio.internal.macros

import zio.ScopedRef

import scala.reflect.internal.Names
import scala.reflect.macros.blackbox
class ServiceProxyMacros(val c: blackbox.Context) {
  import c.universe._
  def makeImpl[A: c.WeakTypeTag](service: c.Expr[ScopedRef[A]]): c.Expr[A] = {
    val tpe = c.weakTypeOf[A]

    def unsupported(reason: String): Nothing =
      c.abort(
        c.enclosingPosition,
        s"""Unable to generate a ZIO service proxy for `${tpe.typeSymbol.fullName}` due to the following reason:
           |
           |  $reason
           |
           |To generate a ZIO service proxy, please ensure the following:
           |  1. The type is either a trait or a class with an empty primary constructor.
           |  2. The type includes only ZIO methods or vals.
           |  3. The type does not have any abstract type members.
           |""".stripMargin
      )

    def defect(reason: String): Nothing =
      c.abort(
        c.enclosingPosition,
        s"""Defect in zio.ServiceProxy:
           |
           |  $reason""".stripMargin
      )

    tpe.members
      .find(m => m.isConstructor)
      .foreach { constructor =>
        if (constructor.asMethod.paramLists.flatten.nonEmpty) {
          unsupported("Primary constructor with non-empty parameters detected")
        }
      }

    tpe.members
      .find(m => m.isType && m.isAbstract)
      .map(abstractType => unsupported(s"Abstract type member detected: ${abstractType.asType}"))

    tpe.members
      .find(m => m.isMethod && m.isAbstract && !(m.asMethod.returnType <:< c.weakTypeOf[zio.ZIO[_, _, _]]))
      .map { nonZIO =>
        val memberType = if (!nonZIO.asMethod.isVal) "method" else "field"
        unsupported(
          s"non-ZIO $memberType detected: ${nonZIO.name}"
        )
      }

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
          case t if t.isVal =>
            q"override val ${sym.name.toTermName}: ${m.finalResultType} = ${service.tree}.get.flatMap(_.${sym.name.toTermName}(...$args))"
          case t if t.isMethod && !t.isVal =>
            q"override def ${sym.name.toTermName}[..$tparams](...$params): ${m.finalResultType} = ${service.tree}.get.flatMap(_.${sym.name.toTermName}(...$args))"
          case t =>
            defect(s"Unexpected member declaration: ${t.name}")
        }
      }
      .toList

    c.Expr(q"new $resultType { ..$forwarders }")
  }

}
