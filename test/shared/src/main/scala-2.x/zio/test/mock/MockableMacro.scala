/*
 * Copyright 2019-2020 John A. De Goes and the ZIO Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.test.mock

import scala.reflect.macros.whitebox.Context

/**
 * Generates method tags for a service into annotated object.
 */
private[mock] object MockableMacro {

  def impl(c: Context)(annottees: c.Tree*): c.Expr[c.Tree] = {
    import c.universe._

    def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    val mockName: TermName = annottees.head match {
      case m: ModuleDef => m.name
      case _            => abort("@mockable macro should only be applied to objects.")
    }

    val serviceType: Type = c.typecheck(q"(??? : ${c.prefix.tree})").tpe.typeArgs.head
    val envType: Type     = c.typecheck(q"(??? : _root_.zio.Has[$serviceType])").tpe

    object zio {

      def unapply(tpe: Type): Option[(Type, Type, Type)] = {
        val dealiased = tpe.dealias
        dealiased.typeArgs match {
          case r :: e :: a :: Nil if dealiased.typeSymbol.fullName == "zio.ZIO" => Some((r, e, a))
          case _                                                                => None
        }
      }
    }

    def capitalize(name: TermName): TermName = TermName(name.toString.capitalize)

    def makeTag(name: TermName, symbol: MethodSymbol): Tree = {
      val tagName = capitalize(name)
      val inputType =
        if (symbol.isVal || symbol.isVar) tq"Unit"
        else {
          val typeList = symbol.paramLists.flatten.map(_.typeSignature)
          if (typeList.size > 22) abort(s"Unable to generate tag for method $name with more than 22 arguments.")
          tq"(..$typeList)"
        }
      val outputType = symbol.returnType match {
        case zio(_, _, a) => a
        case a            => a
      }

      q"case object $tagName extends Tag[$inputType, $outputType]"
    }

    def makeMock(name: TermName, symbol: MethodSymbol, overloadIndex: Option[TermName]): Tree = {
      val tagName = capitalize(name)
      val (r: Type, e: Type, a: Type) = symbol.returnType match {
        case zio(r, e, a) => (r, e, a)
        case a            => (tq"Any", tq"Throwable", a)
      }

      val tag = overloadIndex match {
        case Some(index) => q"$mockName.$tagName.$index"
        case None        => q"$mockName.$tagName"
      }

      val mods =
        if (symbol.isAbstract) Modifiers(Flag.FINAL)
        else Modifiers(Flag.FINAL | Flag.OVERRIDE)

      val returnType = tq"_root_.zio.ZIO[$r, $e, $a]"
      val returnValue =
        symbol.paramLists match {
          case argLists if argLists.flatten.nonEmpty =>
            val argNames = argLists.flatten.map(_.name)
            q"invoke($tag, ..$argNames)"
          case _ =>
            q"invoke($tag)"
        }

      if (symbol.isVal || symbol.isVar) q"$mods val $name: $returnType = $returnValue"
      else
        symbol.paramLists match {
          case Nil       => q"$mods def $name: $returnType = $returnValue"
          case List(Nil) => q"$mods def $name(): $returnType = $returnValue"
          case argLists =>
            val paramLists = argLists.map(_.map { param =>
              val paramType = param.typeSignature
              q"$param: $paramType"
            })
            q"$mods def $name(...$paramLists): $returnType = $returnValue"
        }
    }

    val symbols = serviceType.members
      .filter(_.owner == serviceType.typeSymbol)
      .map(_.asMethod)
      .toList

    val tags =
      symbols
        .groupBy(_.name)
        .collect {
          case (name, symbol :: Nil) =>
            makeTag(name.toTermName, symbol)
          case (name, symbols) =>
            val tagName = capitalize(name.toTermName)
            val overloadedTags = symbols.zipWithIndex.map {
              case (symbol, idx) =>
                val idxName = TermName(s"_$idx")
                makeTag(idxName, symbol)
            }

            q"object $tagName { ..$overloadedTags }"
        }

    val mocks =
      symbols
        .groupBy(_.name)
        .collect {
          case (name, symbol :: Nil) =>
            List(makeMock(name.toTermName, symbol, None))
          case (name, symbols) =>
            symbols.zipWithIndex.map {
              case (symbol, idx) =>
                val idxName = TermName(s"_$idx")
                makeMock(name.toTermName, symbol, Some(idxName))
            }
        }
        .toList
        .flatten

    val envBuilderType = tq"_root_.zio.URLayer[_root_.zio.Has[_root_.zio.test.mock.Proxy], $envType]"

    val result =
      q"""
        object $mockName {
          sealed trait Tag[I, A] extends _root_.zio.test.mock.Method[$envType, I, A] {
            def envBuilder: $envBuilderType = $mockName.envBuilder
          }

          ..$tags

          private lazy val envBuilder: $envBuilderType =
            _root_.zio.ZLayer.fromService(invoke =>
              new $serviceType {
                ..$mocks
              }
            )
        }
      """

    c.Expr[c.Tree](result)
  }
}
