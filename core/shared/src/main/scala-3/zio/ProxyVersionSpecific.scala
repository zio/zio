package zio

import scala.quoted.*

trait ProxyVersionSpecific {
  // inline def generate[A <: AnyRef](service: A): A = ${ ProxyMacros.makeImpl('service) }
  inline def generate[A <: AnyRef](service: A): String = ${ ProxyMacros.makeImpl('service) }
}

private object ProxyMacros {

  def makeImpl[A <: AnyRef : Type](service: Expr[A])(using Quotes): Expr[String] = {
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

    val tree = TypeRepr.of[A].typeSymbol.methodMembers.map { m =>
      val methodType = m.typeRef.asType
      // val paramSymbols = methodType.paramTypes.zip(methodSymbol.paramSymss.flatten).map(_._2)
      // val methodReturnType = methodType.finalResultType.asType

      val m2 = m.tree match {
        case d @ DefDef(name, params, _, _) => Some((name, params, d.returnTpt.show)) 
        case _ => None
      }

      s"""|typeRef: ${m.typeRef}
          |tree: ${m2}
          |""".stripMargin
    }.mkString("\n")
    Expr(tree)
  }
}
