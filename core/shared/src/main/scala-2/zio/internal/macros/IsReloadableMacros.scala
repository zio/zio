package zio.internal.macros

import zio.{IsReloadable, ScopedRef}
import scala.reflect.macros.blackbox

class IsReloadableMacros(val c: blackbox.Context) {
  import c.universe._

  def makeImpl[A: c.WeakTypeTag]: c.Expr[IsReloadable[A]] = {
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
        s"""Defect in zio.IsReloadable:
           |
           |  $reason""".stripMargin
      )

    tpe.members.foreach { m =>
      if (m.isConstructor && m.asMethod.paramLists.flatten.nonEmpty)
        unsupported("Primary constructor with non-empty parameters detected")
      if (m.isType && m.isAbstract)
        unsupported(s"Abstract type member detected: ${m.asType}")

      if (m.isMethod && m.isAbstract && !(m.asMethod.returnType <:< c.weakTypeOf[zio.ZIO[_, _, _]])) {
        val memberType = if (!m.asMethod.isVal) "method" else "field"
        unsupported(
          s"non-ZIO $memberType detected: ${m.name}"
        )
      }
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

        val rhs = q"_$$scopedRef.get.flatMap(_.${sym.name.toTermName}(...$args))"
        sym.asTerm match {
          case t if t.isVal =>
            q"override val ${sym.name.toTermName}: ${m.finalResultType} = $rhs"
          case t if t.isMethod && !t.isVal =>
            q"override def ${sym.name.toTermName}[..$tparams](...$params): ${m.finalResultType} = $rhs"
          case t =>
            defect(s"Unexpected member declaration: ${showRaw(t)}")
        }
      }
      .toList

    c.Expr[IsReloadable[A]](
      q"""
        new _root_.zio.IsReloadable[$resultType] {
          def reloadable(_$$scopedRef: _root_.zio.ScopedRef[$resultType]): $resultType =
            new $resultType { ..$forwarders }
        }
      """
    )
  }

}
