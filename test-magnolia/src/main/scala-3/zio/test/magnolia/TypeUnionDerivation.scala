package zio.test.magnolia

import scala.quoted.*

import zio.test.Gen

/**
 * Derives `DeriveGen` instances for union types at compile time.
 * `DeriveGen.unionType` calls it.
 *
 * Implementation copied/adapted from Caliban
 *
 * See:
 *   - https://github.com/ghostdogpr/caliban/pull/2215
 *   - https://github.com/ghostdogpr/caliban/blob/v2.10.0/core/src/main/scala-3/caliban/schema/TypeUnionDerivation.scala
 */
object TypeUnionDerivation {

  /**
   * Derives a `DeriveGen[T]` for the union type alias `T`. Same as
   * `DeriveGen.unionType`.
   */
  inline def derived[T]: DeriveGen[T] = ${ typeUnionDeriveGen[T] }

  /**
   * The macro behind `derived` and `DeriveGen.unionType`. Flattens the union
   * `T` into its member types, dealiasing type aliases, summons a `DeriveGen`
   * for each member, and builds a `DeriveGen` that picks one member's generator
   * with equal probability. It reports a compile error when `T` is written as
   * an inline union instead of a type alias, and when a member type has no
   * `DeriveGen`.
   */
  def typeUnionDeriveGen[T](using Quotes, Type[T]): Expr[DeriveGen[T]] = {
    import quotes.reflect.*

    val typeName = TypeRepr.of[T].show

    if (typeName.contains("|")) {
      report.error(
        s"You must explicitly add type parameter to derive DeriveGen for a union type in order to capture the name of the type alias"
      )
    }

    class TypeAndDeriveGen[A](val deriveGen: Expr[DeriveGen[A]], val tpe: Type[A])

    def rec[A](using tpe: Type[A]): List[TypeAndDeriveGen[?]] =
      TypeRepr.of(using tpe).dealias match {
        case OrType(l, r) =>
          rec(using l.asType.asInstanceOf[Type[Any]]) ++ rec(using r.asType.asInstanceOf[Type[Any]])
        case otherRepr =>
          val expr: TypeAndDeriveGen[A] =
            Expr.summon[DeriveGen[A]] match {
              case Some(foundDeriveGen) =>
                TypeAndDeriveGen[A](foundDeriveGen, otherRepr.asType.asInstanceOf[Type[A]])
              case None =>
                val otherString: String = otherRepr.show
                quotes.reflect.report.errorAndAbort(s"Couldn't resolve DeriveGen[$otherString]")
            }

          List(expr)
      }

    val typeAndDeriveGens: List[TypeAndDeriveGen[?]] = rec[T]

    val deriveGenByTypeNameList: Expr[List[DeriveGen[Any]]] = Expr.ofList(
      typeAndDeriveGens.map { case (t: TypeAndDeriveGen[a]) =>
        given Type[a] = t.tpe
        '{ ${ t.deriveGen }.asInstanceOf[DeriveGen[Any]] }
      }
    )

    '{
      new DeriveGen[T] {
        private lazy val gen: Gen[Any, T] =
          Gen.oneOf[Any, T](${ deriveGenByTypeNameList }.map(_.derive).asInstanceOf[List[Gen[Any, T]]]*)

        def derive: Gen[Any, T] = gen
      }
    }
  }
}
