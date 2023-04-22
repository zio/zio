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
    def forwarders(cls: Symbol) = tpe.typeSymbol.declaredMethods.flatMap { m =>
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

    val parents = List(TypeTree.of[Object], TypeTree.of[A])

    val cls = Symbol.newClass(
      parent = Symbol.spliceOwner ,  
      name = s"_ZIOProxy_${tpe.typeSymbol.name}",
      parents = parents.map(_.tpe),
      decls = forwarders,
      selfType = None
    )

    val body = cls.declaredMethods.flatMap { m =>
      m.tree match {
        case d @ DefDef(name, paramss, returnTpt, rhs) =>
          val `ScopedRef#get` = 
            TypeRepr.of[ScopedRef[_]].typeSymbol.declaredMethod("get").head

          val `ZIO#flatMap` =
            TypeRepr.of[ZIO[_, _, _]].typeSymbol.declaredMethod("flatMap").head

          val trace =
            Implicits.search(TypeRepr.of[Trace]) match {
              case s: ImplicitSearchSuccess => s.tree
              case s: ImplicitSearchFailure => report.errorAndAbort("Implicit zio.Trace not found")
            }

          val body = 
            Apply(
              TypeApply(
                Select(
                  Apply(
                    Select(
                      service.asTerm, 
                      `ScopedRef#get`
                    ),
                    List(trace)
                  ),
                  `ZIO#flatMap`
                ),
                returnTpt.tpe.dealias.typeArgs.map(t => TypeIdent(t.typeSymbol))
              ),
              List( 
                Lambda(
                  owner = m,
                  tpe = MethodType(List("_$1"))(_ => List(tpe), _ => returnTpt.tpe),
                  rhsFn = (sym, params) => {
                    val svc = Select(params.head.asInstanceOf[Term], m)

                    val withTypeParams = d.leadingTypeParams match {
                      case Nil => svc
                      case ts => TypeApply(svc, ts.map(t => TypeIdent(t.symbol)))
                    }

                    d.termParamss
                      .foldLeft[Term](withTypeParams) { (acc, ps) =>
                        Apply(
                          acc, 
                          ps.params.map(p => Ident(p.symbol.termRef))
                        )
                      }
                  }
                )
              )
            )

          val tree = 
            DefDef(
              d.symbol,
              _ => Some(body)
            )

          Some(tree)

        case _ => None
      }
    }

    val clsDef = ClassDef(cls, parents, body = body)
    val newCls = Typed(Apply(Select(New(TypeIdent(cls)), cls.primaryConstructor), Nil), TypeTree.of[A])
    
    Block(List(clsDef), newCls).asExprOf[A]
  }
}
