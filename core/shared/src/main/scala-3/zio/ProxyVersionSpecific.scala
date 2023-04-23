package zio

import scala.annotation.experimental
import scala.quoted.*

trait ProxyVersionSpecific {

  @experimental
  inline def generate[A <: AnyRef](service: ScopedRef[A]): A = ${ ProxyMacros.makeImpl('service) }
}

private object ProxyMacros {

  @experimental
  def makeImpl[A <: AnyRef : Type](service: Expr[ScopedRef[A]])(using Quotes): Expr[A] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[A]
    def forwarders(cls: Symbol) = tpe.typeSymbol.methodMembers.flatMap { m =>
      m.tree match {
       case DefDef(name, clauses, returnTpt, rhs) if m.flags.is(Flags.Deferred) =>
          val returnsZIO = returnTpt.tpe <:< TypeRepr.of[ZIO[_, _, _]]
          if (!returnsZIO) {
            report.errorAndAbort(s"Cannot generate a proxy for ${tpe.typeSymbol.name} due to a non-ZIO method ${name}(...): ${returnTpt.symbol.name}")
          }
          
          val newMethod = Symbol.newMethod(cls, name, tpe.memberType(m))
          Some(newMethod)

        case _ => None
      }
    }

    val parents = TypeTree.of[Object] :: TypeTree.of[A] :: Nil

    val cls = Symbol.newClass(
      parent = Symbol.spliceOwner ,  
      name = s"_ZIOProxy_${tpe.typeSymbol.name}",
      parents = parents.map(_.tpe),
      decls = forwarders,
      selfType = None
    )

    val body = cls.declaredMethods.flatMap { method =>
      method.tree match {
        case d @ DefDef(name, paramss, returnTpt, rhs) =>
          val `ScopedRef#get` = 
            TypeRepr.of[ScopedRef[_]].typeSymbol.methodMember("get").head

          val `ZIO#flatMap` =
            TypeRepr.of[ZIO[_, _, _]].typeSymbol.methodMember("flatMap").head

          val trace =
            Implicits.search(TypeRepr.of[Trace]) match {
              case s: ImplicitSearchSuccess => s.tree
              case s: ImplicitSearchFailure => report.errorAndAbort("Implicit zio.Trace not found")
            }

          val body = 
            service.asTerm
              .select(`ScopedRef#get`)
              .appliedTo(trace)
              .select(`ZIO#flatMap`)
              .appliedToTypes(returnTpt.tpe.dealias.typeArgs)
              .appliedTo(
                // ((_$1: A) => _$1.$method(...$params))
                Lambda(
                  owner = method,
                  tpe = MethodType("_$1" :: Nil)(_ => tpe :: Nil, _ => returnTpt.tpe),
                  rhsFn = (_, params) => {
                    Select(params.head.asInstanceOf[Term], method)
                      .appliedToTypes(d.leadingTypeParams.map(_.symbol.typeRef))
                      .appliedToArgss(d.termParamss.map(_.params.map(p => Ident(p.symbol.termRef))))
                  }
                )
              )

          Some(DefDef(d.symbol, _ => Some(body)))

        case _ => None
      }
    }

    val clsDef = ClassDef(cls, parents, body = body)
    val newCls = 
      Typed(
        New(TypeIdent(cls))
          .select(cls.primaryConstructor)
          .appliedToNone,
        TypeTree.of[A]
      )
    
    Block(List(clsDef), newCls).asExprOf[A]
  }
}
