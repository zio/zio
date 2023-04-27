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
      val name = m.name

      if (!(m.returnType <:< c.weakTypeOf[zio.ZIO[_, _, _]])) {
        c.abort(
          c.enclosingPosition,
          s"Cannot generate a proxy for ${weakTypeOf[A]} due to a non-ZIO method ${name}(...): ${m.returnType}"
        )
      }
      val tpe  = weakTypeTag[A].tpe
      val from = tpe.typeConstructor.typeParams
      val to   = tpe.typeArgs
      val bar  = tpe.members.find(_.isAbstract).get

//      println(m.typeParams.map(_.typeSignature))


      val t = bar.asMethod.typeSignature.substituteTypes(from, to).typeParams.map(_.asType)
      val typeParameter = t.map(x =>
        TypeDef(Modifiers(), TypeName(x.name.toTermName.toString), List(), TypeBoundsTree(EmptyTree, EmptyTree))
      )
      val params = List((t zip m.paramLists).map { a =>
        ValDef(Modifiers(), TermName(a._2.head.name.toTermName.toString), Ident(TypeName(a._1.name.toTermName.toString)), EmptyTree)
      })

      val paramsUse = m.paramLists.flatMap(_.map(p => q"${p.name.toTermName}"))

      val re = bar.asMethod.returnType.substituteTypes(from, to)
      val t1 = re.typeConstructor.toString.split("\\.")
      val constructor = Select(Ident(TermName(t1.head)), TypeName(t1(1)))
      val A2 = t.map(x => Ident(TypeName(x.name.toTermName.toString)))
      val returnType = AppliedTypeTree(constructor, A2)

      q"def $name[..$typeParameter](...$params): $returnType = ${service.tree}.get.flatMap(_.$name(...$paramsUse))"
    }

    val tree =
      q"""
      new ${weakTypeOf[A]} {
        ..$proxyMethods
      }
    """

    c.Expr[A](tree)
  }
// List(Typed(Ident(TermName("a")), TypeTree()))

  //

//  DefDef(
//    Modifiers(),
//    TermName("bar"),
//    List(TypeDef(Modifiers(PARAM), TypeName("A"), List(), TypeBoundsTree(EmptyTree, EmptyTree))),
//    List(List(ValDef(Modifiers(PARAM), TermName("a"), Ident(TypeName("A")), EmptyTree))),
//    AppliedTypeTree(Select(Ident(TermName("zio")), TypeName("UIO")), List(Ident(TypeName("A")))),
//    Apply(
//      Select(Select(Ident(TermName("ref")), TermName("get")), TermName("flatMap")),
//      List(
//        Function(
//          List(ValDef(Modifiers(PARAM | SYNTHETIC), TermName("x$1"), TypeTree(), EmptyTree)),
//          Apply(
//            TypeApply(Select(Ident(TermName("x$1")), TermName("bar")), List(Ident(TypeName("A")))),
//            List(Ident(TermName("a")))
//          )
//        )
//      )
//    )
//  )

}
