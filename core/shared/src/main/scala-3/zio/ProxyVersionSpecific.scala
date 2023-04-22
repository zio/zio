package zio

import scala.annotation.experimental
import scala.quoted.*

trait ProxyVersionSpecific {

  @experimental
  inline def generate[A <: AnyRef](service: A): A = ${ ProxyMacros.makeImpl('service) }
}

private object ProxyMacros {

  @experimental
  def makeImpl[A <: AnyRef : Type](service: Expr[A])(using Quotes): Expr[A] = {
    import quotes.reflect.*

    // if (!TypeRepr.of[A].typeSymbol.flags.is(Flags.Trait)) {
    //   report.error("A must be a trait", instance)
    //   return '{ ??? }
    // }

    // val methods = TypeRepr.of[A].typeSymbol.memberMethods

    // val forwarders: List[Expr[DefDef]] = methods.map { methodSymbol =>
    //   val methodType = methodSymbol.typeRef.asType
    //   val paramSymbols = methodType.paramTypes.zip(methodSymbol.paramSymss.flatten).map(_._2)
    //   val methodReturnType = methodType.finalResultType.asType
      
    //   val paramDefs = paramSymbols.map { param =>
    //     '{(${param.name.toString}: ${param.info})
    //   }

    //   '{
    //     def ${methodSymbol.name.toString}(${paramDefs: _*}): ${methodReturnType} = 
    //       $instance.${methodSymbol.name.toString}(${paramDefs.map(p => '{${Expr(using p.name.toString)}}): _*})
    //   }
    // }

    // val proxyImpl = '{
    //   new ${Type.of[A]} {
    //     ${forwarders: _*}
    //   }
    // }

    val tpe = TypeRepr.of[A]
    def forwarders(cls: Symbol) = tpe.typeSymbol.declaredMethods.flatMap { m =>
      m.tree match {
       case d @ DefDef(name, clauses, typedTree,_) =>
          val returnsZIO = d.returnTpt.tpe <:< TypeRepr.of[ZIO[_, _, _]]
          val newMethod = Symbol.newMethod(cls, name, tpe.memberType(m), flags = Flags.Override, privateWithin = m.privateWithin.fold(Symbol.noSymbol)(_.typeSymbol))
          Some(newMethod)

        case _ => None
      }
    }

    val parents = List(TypeTree.of[Object], TypeTree.of[A])
    // val parents = List(TypeTree.of[Object])

    val cls = Symbol.newClass(
      parent = Symbol.spliceOwner ,  
      name = s"_ZIOProxy_${tpe.typeSymbol.name}",
      parents = parents.map(_.tpe),
      decls = forwarders,
      selfType = None
    )

    val body = cls.declaredMethods.flatMap { m =>
      m.tree match {
        case d @ DefDef(name, paramss, tpt, rhs) =>
          val returnsZIO = d.returnTpt.tpe <:< TypeRepr.of[ZIO[_, _, _]]

          val tref = m.typeRef
          val msg =
            s"""|typeRef: ${tref.show(using Printer.TypeReprAnsiCode)}
                |  ${tref}
                |  - translucentSuperType: ${tref.translucentSuperType}
                |  - baseClasses: ${tref.baseClasses}
                |  - classSymbol: ${tref.classSymbol}
                |  - qualifier: ${tref.qualifier}
                |  - isErasedFunctionType: ${tref.isErasedFunctionType}
                |  - qualifier: ${tref.isErasedFunctionType}
                |flags: ${m.flags.show}
                |tree: ${d.show(using Printer.TreeAnsiCode)}
                | - symbol: ${d.symbol}
                | - name: $name
                | - paramss: $paramss
                | - termParamss: ${d.termParamss}
                | - trailingParamss: ${d.trailingParamss}
                | - returnTpt: ${d.returnTpt.show}
                |   - ZIO: ${d.returnTpt.tpe <:< TypeRepr.of[ZIO[_, _, _]]}
                | - rhs: ${d.rhs}
                |signature: ${m.signature}
                |""".stripMargin

          // println(msg)

          val impl = {
                  // val svc = Select(service.asTerm, m)

                  // val withTypeParams = d.leadingTypeParams match {
                  //   case Nil => svc
                  //   case ts => TypeApply(svc, ts.map(t => TypeIdent(t.symbol)))
                  // }

                  // d.termParamss.foldLeft[Term](withTypeParams) { (acc, ps) =>
                  //   Apply(acc, ps.params.map(p => Ident(p.symbol.termRef)))
                  // }

                  '{ ??? }.asTerm
                }

          val tree = 
            DefDef(
              d.symbol,
              _ => Some(impl)
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
