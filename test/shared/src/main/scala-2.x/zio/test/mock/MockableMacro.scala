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

import com.github.ghik.silencer.silent

/**
 * Generates method tags for a service into annotated object.
 */
private[mock] object MockableMacro {

  @silent
  def impl(c: Context)(annottees: c.Tree*): c.Expr[c.Tree] = {
    import c.universe._

    def log(msg: String)   = c.info(c.enclosingPosition, msg, true)
    def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    val mockName: TermName = annottees.head match {
      case m: ModuleDef => m.name
      case _            => abort("@mockable macro should only be applied to objects.")
    }

    val service: Type    = c.typecheck(q"(??? : ${c.prefix.tree})").tpe.typeArgs.head
    val env: Type        = c.typecheck(tq"_root_.zio.Has[$service]", c.TYPEmode).tpe
    val any: Type        = tq"_root_.scala.Any".tpe
    val throwable: Type  = tq"_root_.java.lang.Throwable".tpe
    val unit: Type       = tq"_root_.scala.Unit".tpe
    val composeAsc: Tree = tq"_root_.zio.URLayer[_root_.zio.Has[_root_.zio.test.mock.Proxy], $env]"
    val taggedFcqns      = List("izumi.reflect.Tags.Tag", "scala.reflect.ClassTag")

    def bound(tpe: Type): Tree =
      tq"$tpe" match {
        case TypeTree() => EmptyTree
        case nonEmpty   => nonEmpty
      }

    def capitalize(name: TermName): TermName = TermName(name.toString.capitalize)

    def isTagged(s: Symbol): Boolean =
      s.typeSignature.baseClasses.exists(ts => taggedFcqns.contains(ts.fullName.toString))

    def toTypeDef(symbol: Symbol): TypeDef = {
      val tpe = symbol.asType

      val (lo, hi) = symbol.typeSignature match {
        case TypeBounds(lo, hi) => bound(lo) -> bound(hi)
        case _                  => EmptyTree -> EmptyTree
      }

      val tpeParams: List[TypeDef] = tpe.typeParams.map(s => toTypeDef(s))
      TypeDef(Modifiers(Flag.PARAM), tpe.name, tpeParams, TypeBoundsTree(lo, hi))
    }

    case class MethodInfo(
      symbol: MethodSymbol,
      isZio: Boolean,
      params: List[Symbol],
      typeParams: List[TypeSymbol],
      i: Type,
      r: Type,
      e: Type,
      a: Type,
      polyI: Boolean,
      polyE: Boolean,
      polyA: Boolean
    )

    object MethodInfo {

      def apply(symbol: MethodSymbol): MethodInfo = {
        val name = symbol.name
        val params =
          symbol.paramLists.flatten
            .filterNot(isTagged)

        if (symbol.isVar) abort(s"Error generating tag for $name. Variables are not supported by @mockable macro.")
        if (params.size > 22) abort(s"Unable to generate tag for method $name with more than 22 arguments.")

        val i =
          if (symbol.isVal) unit
          else c.typecheck(tq"(..${params.map(_.typeSignature)})", c.TYPEmode).tpe

        val dealiased = symbol.returnType.dealias
        val (isZio, r, e, a) =
          dealiased.typeArgs match {
            case r :: e :: a :: Nil if dealiased.typeSymbol.fullName == "zio.ZIO" => (true, r, e, a)
            case _                                                                => (false, any, throwable, symbol.returnType)
          }

        val typeParams = symbol.typeParams.map(_.asType)
        val polyI      = typeParams.exists(ts => i.contains(ts))
        val polyE      = typeParams.exists(ts => e.contains(ts))
        val polyA      = typeParams.exists(ts => a.contains(ts))

        MethodInfo(symbol, isZio, params, typeParams, i, r, e, a, polyI, polyE, polyA)
      }
    }

    def makeTag(name: TermName, info: MethodInfo): Tree = {
      val tagName   = capitalize(name)
      val (i, e, a) = (info.i, info.e, info.a)

      (info.polyI, info.polyE, info.polyA) match {
        case (false, false, false) =>
          q"case object $tagName extends _root_.zio.test.mock.Method[$env, $i, $e, $a](compose)"
        case (true, false, false) =>
          q"case object $tagName extends _root_.zio.test.mock.Method.Poly.Input[$env, $e, $a](compose)"
        case (false, true, false) =>
          q"case object $tagName extends _root_.zio.test.mock.Method.Poly.Error[$env, $i, $a](compose)"
        case (false, false, true) =>
          q"case object $tagName extends _root_.zio.test.mock.Method.Poly.Output[$env, $i, $e](compose)"
        case (true, true, false) =>
          q"case object $tagName extends _root_.zio.test.mock.Method.Poly.InputError[$env, $a](compose)"
        case (true, false, true) =>
          q"case object $tagName extends _root_.zio.test.mock.Method.Poly.InputOutput[$env, $e](compose)"
        case (false, true, true) =>
          q"case object $tagName extends _root_.zio.test.mock.Method.Poly.ErrorOutput[$env, $i](compose)"
        case (true, true, true) =>
          q"case object $tagName extends _root_.zio.test.mock.Method.Poly.InputErrorOutput[$env](compose)"
      }
    }

    def makeMock(name: TermName, info: MethodInfo, overloadIndex: Option[TermName]): Tree = {
      val tagName      = capitalize(name)
      val (r, i, e, a) = (info.r, info.i, info.e, info.a)

      val typeParamArgs = info.symbol.typeParams.map(s => toTypeDef(s))
      val tag = (info.polyI, info.polyE, info.polyA, overloadIndex) match {
        case (false, false, false, None)        => q"$mockName.$tagName"
        case (true, false, false, None)         => q"$mockName.$tagName.of[$i]"
        case (false, true, false, None)         => q"$mockName.$tagName.of[$e]"
        case (false, false, true, None)         => q"$mockName.$tagName.of[$a]"
        case (true, true, false, None)          => q"$mockName.$tagName.of[$i, $e]"
        case (true, false, true, None)          => q"$mockName.$tagName.of[$i, $a]"
        case (false, true, true, None)          => q"$mockName.$tagName.of[$e, $a]"
        case (true, true, true, None)           => q"$mockName.$tagName.of[$i, $e, $a]"
        case (false, false, false, Some(index)) => q"$mockName.$tagName.$index"
        case (true, false, false, Some(index))  => q"$mockName.$tagName.$index.of[$i]"
        case (false, true, false, Some(index))  => q"$mockName.$tagName.$index.of[$e]"
        case (false, false, true, Some(index))  => q"$mockName.$tagName.$index.of[$a]"
        case (true, true, false, Some(index))   => q"$mockName.$tagName.$index.of[$i, $e]"
        case (true, false, true, Some(index))   => q"$mockName.$tagName.$index.of[$i, $a]"
        case (false, true, true, Some(index))   => q"$mockName.$tagName.$index.of[$e, $a]"
        case (true, true, true, Some(index))    => q"$mockName.$tagName.$index.of[$i, $e, $a]"
      }

      val mods =
        if (info.symbol.isAbstract) Modifiers(Flag.FINAL)
        else Modifiers(Flag.FINAL | Flag.OVERRIDE)

      val returnType = tq"_root_.zio.ZIO[$r, $e, $a]"
      val returnValue =
        info.params.map(_.name) match {
          case Nil        => q"invoke($tag)"
          case paramNames => q"invoke($tag, ..$paramNames)"
        }

      if (info.symbol.isVal) q"$mods val $name: $returnType = $returnValue"
      else {
        info.symbol.paramLists.map(_.map { ts =>
          val name = ts.asTerm.name
          val mods =
            if (ts.isImplicit) Modifiers(Flag.PARAM | Flag.IMPLICIT)
            else Modifiers(Flag.PARAM)

          ValDef(mods, name, tq"${ts.info}", EmptyTree)
        }) match {
          case Nil       => q"$mods def $name[..$typeParamArgs]: $returnType = $returnValue"
          case List(Nil) => q"$mods def $name[..$typeParamArgs](): $returnType = $returnValue"
          case params =>
            q"$mods def $name[..$typeParamArgs](...$params): $returnType = $returnValue"
        }
      }
    }

    val methods = service.members
      .filter(_.owner == service.typeSymbol)
      .map(symbol => MethodInfo(symbol.asMethod))
      .toList
      .groupBy(_.symbol.name)

    val tags =
      methods.collect {
        case (name, info :: Nil) =>
          makeTag(name.toTermName, info)
        case (name, infos) =>
          val tagName = capitalize(name.toTermName)
          val overloadedTags = infos.zipWithIndex.map {
            case (info, idx) =>
              val idxName = TermName(s"_$idx")
              makeTag(idxName, info)
          }

          q"object $tagName { ..$overloadedTags }"
      }

    val mocks =
      methods.collect {
        case (name, info :: Nil) =>
          List(makeMock(name.toTermName, info, None))
        case (name, infos) =>
          infos.zipWithIndex.map {
            case (info, idx) =>
              val idxName = TermName(s"_$idx")
              makeMock(name.toTermName, info, Some(idxName))
          }
      }.toList.flatten

    val structure =
      q"""
        object $mockName {

          ..$tags

          private lazy val compose: $composeAsc =
            _root_.zio.ZLayer.fromService(invoke =>
              new $service {
                ..$mocks
              }
            )
        }
      """

    c.Expr[c.Tree](
      c.parse(
        s"$structure"
          .replaceAll("\\n\\s+def \\<init\\>\\(\\) = \\{\\n\\s+super\\.\\<init\\>\\(\\);\\n\\s+\\(\\)\\n\\s+\\};?", "")
          .replaceAll("\\{[\\n\\s]*\\};?", "")
          .replaceAll("final class \\$anon extends", "new")
          .replaceAll("\\};\\n\\s+new \\$anon\\(\\)", "\\}")
          .replaceAll("(object .+) extends scala.AnyRef", "$1")
          .replaceAll("(object .+) with scala.Product with scala.Serializable", "$1")
      )
    )
  }
}
