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

    // TODO replace with `ValOrDefDef` https://github.com/lampepfl/dotty/pull/16974/
    def nameAndReturnType(t: Tree): Option[(String, TypeTree)] =
      t match {
        case DefDef(name, _, tpt, _) => Some((name, tpt))
        case ValDef(name, tpt, _) => Some((name, tpt))
        case _ => None
      }

    val tpe = TypeRepr.of[A]
    def forwarders(cls: Symbol) =
      (tpe.typeSymbol.methodMembers.view ++ tpe.typeSymbol.fieldMembers.view).flatMap { m =>
        nameAndReturnType(m.tree).flatMap { (name, tpt) =>
          val returnsZIO = tpt.tpe <:< TypeRepr.of[ZIO[_, _, _]]

          if (returnsZIO) {
            val privateWithin = m.privateWithin.map(_.typeSymbol).getOrElse(Symbol.noSymbol)
            if (m.isDefDef)
              Some(Symbol.newMethod(cls, name, tpe.memberType(m), Flags.Override, privateWithin))
            else if (m.isValDef)
              Some(Symbol.newVal(cls, name, tpe.memberType(m), Flags.Override, privateWithin))
            else
              report.errorAndAbort(s"Unexpected member tree: ${m.tree}")
          } else if (m.flags.is(Flags.Deferred)) {
            report.errorAndAbort(
              s"Cannot generate a proxy for ${tpe.typeSymbol.name} due to a non-ZIO member ${name}(...): ${tpt.symbol.name}"
            )
          } else None
        }
      }.toList

    val parents = TypeTree.of[Object] :: TypeTree.of[A] :: Nil

    val cls = Symbol.newClass(
      parent = Symbol.spliceOwner ,  
      name = s"_ZIOProxy_${tpe.typeSymbol.name}",
      parents = parents.map(_.tpe),
      decls = forwarders,
      selfType = None
    )

    val trace =
      Expr.summon[Trace]
        .getOrElse(report.errorAndAbort("Implicit zio.Trace not found"))
        .asTerm

    // TODO replace with `ValOrDefDef` https://github.com/lampepfl/dotty/pull/16974/
    def typeAndParams(m: Symbol): Option[(TypeTree, List[TypeDef], List[TermParamClause])] =
      m.tree match {
        case d: DefDef if !m.isClassConstructor =>
          Some((d.returnTpt, d.leadingTypeParams, d.termParamss))
        case v: ValDef =>
          Some((v.tpt, Nil, Nil))
        case _ => 
          None
      }

    val body = cls.declarations.flatMap { member =>
      typeAndParams(member).flatMap { (tpt, typeParams, termParamss) =>
        val body = 
          service.asTerm
            .select(TypeRepr.of[ScopedRef[_]].typeSymbol.methodMember("get").head)
            .appliedTo(trace)
            .select(TypeRepr.of[ZIO[_, _, _]].typeSymbol.methodMember("flatMap").head)
            .appliedToTypes(tpt.tpe.dealias.typeArgs)
            .appliedTo(
              Lambda(
                owner = member,
                tpe = MethodType("_$1" :: Nil)(_ => tpe :: Nil, _ => tpt.tpe),
                rhsFn = { 
                  case (_, _$1 :: Nil) =>
                    _$1.asInstanceOf[Term]
                      .select(member)
                      .appliedToTypes(typeParams.map(_.symbol.typeRef))
                      .appliedToArgss(termParamss.map(_.params.map(p => Ident(p.symbol.termRef))))

                  case (_, ps) =>
                    report.errorAndAbort(s"Unexpected lambda params: $ps")
                }
              )
            )
            .appliedTo(trace)

        if (member.isDefDef)
          Some(DefDef(member, _ => Some(body)))
        else if (member.isValDef)
          Some(ValDef(member, Some(body)))
        else
          report.errorAndAbort(s"Unexpected member tree: ${member.tree}")
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
