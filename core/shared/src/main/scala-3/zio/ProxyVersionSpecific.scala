package zio

import scala.annotation.experimental
import scala.quoted.*

trait ProxyVersionSpecific {

  @experimental
  inline def generate[A](service: ScopedRef[A]): A = ${ ProxyMacros.makeImpl('service) }
}

private object ProxyMacros {

  @experimental
  def makeImpl[A: Type](service: Expr[ScopedRef[A]])(using Quotes): Expr[A] = {
    import quotes.reflect.*

    val tpe = TypeRepr.of[A]
    def forwarders(cls: Symbol) = tpe.typeSymbol.methodMembers.flatMap { m =>
      m.tree match {
       case DefDef(name, clauses, returnTpt, rhs) if m.flags.is(Flags.Deferred) =>
          val returnsZIO = returnTpt.tpe <:< TypeRepr.of[ZIO[_, _, _]]
          if (!returnsZIO) {
            report.errorAndAbort(s"Cannot generate a proxy for ${tpe.typeSymbol.name} due to a non-ZIO method ${name}(...): ${returnTpt.symbol.name}")
          }
          Some(Symbol.newMethod(cls, name, tpe.memberType(m)))

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

    val trace =
      Implicits.search(TypeRepr.of[Trace]) match {
        case s: ImplicitSearchSuccess => s.tree
        case s: ImplicitSearchFailure => report.errorAndAbort("Implicit zio.Trace not found")
      }

    val body = cls.declaredMethods.map { method =>
      method.tree match {
        case d: DefDef =>
          val body = 
            service.asTerm
              .select(TypeRepr.of[ScopedRef[_]].typeSymbol.methodMember("get").head)
              .appliedTo(trace)
              .select(TypeRepr.of[ZIO[_, _, _]].typeSymbol.methodMember("flatMap").head)
              .appliedToTypes(d.returnTpt.tpe.dealias.typeArgs)
              .appliedTo(
                // ((_$1: A) => _$1.$method(...$paramss))
                Lambda(
                  owner = method,
                  tpe = MethodType("_$1" :: Nil)(_ => tpe :: Nil, _ => d.returnTpt.tpe),
                  rhsFn = { 
                    case (_, _$1 :: Nil) =>
                      _$1.asInstanceOf[Term]
                        .select(method)
                        .appliedToTypes(d.leadingTypeParams.map(_.symbol.typeRef))
                        .appliedToArgss(d.termParamss.map(_.params.map(p => Ident(p.symbol.termRef))))

                    case (_, ps) =>
                      report.errorAndAbort(s"Unexpected lambda params: $ps")
                  }
                )
              )

          DefDef(d.symbol, _ => Some(body))

        case t => 
          report.errorAndAbort(s"Unexpected def: $t")
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
