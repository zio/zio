package zio.internal.macros

import zio._
import scala.reflect.macros.whitebox

private[zio] class ZLayerDerivationMacros(val c: whitebox.Context) {
  import c.universe._

  case class Param(tpe: Type, name: TermName, defaultOrService: Either[Type, Tree], rType: Type, eType: Option[Type])

  def deriveImpl[A: WeakTypeTag] = {

    val tpe = weakTypeOf[A]

    if (tpe.typeSymbol.isAbstract) {
      c.abort(c.enclosingPosition, s"Failed to derive a ZLayer: type `$tpe` is not a concrete class.")
    }

    if (tpe.typeSymbol.isJava) {
      c.abort(c.enclosingPosition, s"Failed to derive a ZLayer: Java type `$tpe` is not supported.")
    }

    val ctor = tpe.decls.collectFirst {
      case m: MethodSymbol if m.isPrimaryConstructor => m
    }
      .getOrElse(c.abort(c.enclosingPosition, s"Failed to derive a ZLayer: type `$tpe` does not have any constructor."))

    def findDefaultExpr(tpe: Type) = {
      val defaultType = appliedType(
        weakTypeOf[ZLayer.Derive.Default.WithContext[_, _, _]].typeConstructor,
        WildcardType,
        WildcardType,
        tpe
      )
      c.inferImplicitValue(defaultType)
    }

    val params: List[Param] =
      ctor.paramLists.flatMap(_.map { sym =>
        val name    = sym.name.toTermName
        val depType = sym.info.asSeenFrom(tpe, tpe.typeSymbol)

        val defaultExpr = findDefaultExpr(depType)

        if (defaultExpr.isEmpty)
          Param(depType, name, Left(depType), depType, None)
        else {
          val memType = defaultExpr.tpe.dealias
          val rType   = memType.member(TypeName("R")).info
          val eType   = memType.member(TypeName("E")).info

          (rType.typeSymbol, eType.typeSymbol) match {
            case (NoSymbol, _) | (_, NoSymbol) =>
              c.abort(
                sym.pos,
                s"""|Failed to derive a ZLayer for `${tpe.typeSymbol.fullName}`.
                    |
                    |The type information `R`, `E` in `ZLayer.Derive.Default[A]` for the parameter
                    |`$name` is missing. The resolved default instance is:
                    |
                    |  ${defaultExpr}: ${defaultExpr.tpe} 
                    |
                    |A frequent reason for this issue is using an incomplete type annotation like
                    |`implicit val defaultA: ZLayer.Derive.Default[A] = ???`.  This can lead to the
                    |loss of specific type details.
                    |
                    |To resolve, replace it with `ZLayer.Derive.Default.WithContext[R, E, A]`.
                    |If you're using an IDE, remove the type annotations and add the inferred type
                    |annotation using the IDE's assistant feature.
                    |""".stripMargin
              )

            case _ =>
          }

          Param(depType, name, Right(defaultExpr), rType, Some(eType))
        }
      })

    val serviceCalls = params.collect { case Param(_, name, Left(depType), _, _) =>
      fq"$name <- _root_.zio.ZIO.service[$depType]"
    }

    val defaultLayers = params.collect { case Param(_, name, Right(defaultExpr), _, _) =>
      fq"$name <- $defaultExpr.layer"
    }

    val argMap = params.map { p =>
      p.defaultOrService match {
        case Left(_)  => p.name -> q"${p.name}"
        case Right(_) => p.name -> q"${p.name}.get[${p.tpe}]"
      }
    }.toMap

    val constructorArgss =
      ctor.paramLists.map(_.map(p => argMap(p.name.toTermName)))

    val newInstance = q"new $tpe(...$constructorArgss)"

    val (zlayerCtor, make) =
      if (tpe <:< typeOf[ZLayer.Derive.Scoped[_, _]]) {
        q"_root_.zio.ZLayer.scoped" ->
          q"""{
            val instance = $newInstance
            instance.scoped.as(instance)
          }"""
      } else
        q"_root_.zio.ZLayer.apply" ->
          q"_root_.zio.ZIO.succeed[$tpe]($newInstance)"

    q"""
    for {
      ..$defaultLayers
      result <- $zlayerCtor {
        for {
          ..$serviceCalls
          impl <- $make
        } yield impl
      }
    } yield result
    """
  }

}
