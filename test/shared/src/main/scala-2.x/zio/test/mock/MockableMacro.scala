/*
 * Copyright 2019-2021 John A. De Goes and the ZIO Contributors
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

import com.github.ghik.silencer.silent
import zio.test.TestVersion

import scala.reflect.macros.whitebox.Context

/**
 * Generates method tags for a service into annotated object.
 */
private[mock] object MockableMacro {

  @silent
  def impl(c: Context)(annottees: c.Tree*): c.Expr[c.Tree] = {
    import c.universe._

    def log(msg: String)   = c.info(c.enclosingPosition, msg, true)
    def abort(msg: String) = c.abort(c.enclosingPosition, msg)

    val (mockName: TermName, body: List[Tree]) = annottees.head match {
      case m: ModuleDef => (m.name, m.impl.body)
      case _            => abort("@mockable macro should only be applied to objects.")
    }

    val service: Type = c.typecheck(q"(??? : ${c.prefix.tree})").tpe.typeArgs.head
    if (service == definitions.NothingTpe)
      abort(s"@mockable macro requires type parameter: @mockable[Module.Random]")

    val serviceBaseTypeParameters         = service.baseType(service.typeSymbol).typeConstructor.typeParams.map(_.asType)
    val serviceTypeParameterSubstitutions = serviceBaseTypeParameters.zip(service.typeArgs).toMap

    val env: Type        = c.typecheck(tq"_root_.zio.Has[$service]", c.TYPEmode).tpe
    val any: Type        = definitions.AnyTpe
    val throwable: Type  = c.typecheck(q"(??? : _root_.java.lang.Throwable)").tpe
    val unit: Type       = definitions.UnitTpe
    val composeAsc: Tree = tq"_root_.zio.URLayer[_root_.zio.Has[_root_.zio.test.mock.Proxy], $env]"
    val taggedFcqns      = List("izumi.reflect.Tag")

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

    sealed trait Capability
    object Capability {
      case class Method(a: Type)                                    extends Capability
      case class Effect(r: Type, e: Type, a: Type)                  extends Capability
      case class Stream(r: Type, e: Type, a: Type)                  extends Capability
      case class Sink(r: Type, e: Type, a0: Type, a: Type, b: Type) extends Capability
    }

    case class MethodInfo(
      symbol: MethodSymbol,
      capability: Capability,
      params: List[Symbol],
      typeParams: List[TypeSymbol],
      i: Type
    ) {

      val r: Type = capability match {
        case Capability.Effect(r, _, _)     => r
        case Capability.Sink(r, _, _, _, _) => r
        case Capability.Stream(r, _, _)     => r
        case Capability.Method(_)           => any
      }

      val e: Type = capability match {
        case Capability.Effect(_, e, _)     => e
        case Capability.Sink(_, e, _, _, _) => e
        case Capability.Stream(_, e, _)     => e
        case Capability.Method(_)           => throwable
      }

      val a: Type = capability match {
        case Capability.Effect(_, _, a)      => a
        case Capability.Sink(_, e, a0, a, b) => tq"_root_.zio.stream.ZSink[$any, $e, $a0, $a, $b]".tpe
        case Capability.Stream(_, e, a)      => tq"_root_.zio.stream.ZStream[$any, $e, $a]".tpe
        case Capability.Method(a)            => a
      }

      val polyI: Boolean = typeParams.exists(ts => i.contains(ts))
      val polyE: Boolean = typeParams.exists(ts => e.contains(ts))
      val polyA: Boolean = typeParams.exists(ts => a.contains(ts))
    }

    object MethodInfo {

      def apply(symbol: MethodSymbol): MethodInfo = {
        val name = symbol.name
        val params =
          symbol.paramLists.flatten
            .filterNot(isTagged)

        if (symbol.isVar) abort(s"Error generating tag for $name. Variables are not supported by @mockable macro.")
        if (params.size > 22) abort(s"Unable to generate tag for method $name with more than 22 arguments.")

        def paramTypeToTupleType(symbol: Symbol) = {
          val ts = symbol.typeSignature
          if (ts.typeSymbol == definitions.RepeatedParamClass) tq"_root_.scala.Seq[${ts.typeArgs.head}]"
          else tq"$ts"
        }

        val i =
          if (symbol.isVal) unit
          else c.typecheck(tq"(..${params.map(paramTypeToTupleType(_))})", c.TYPEmode).tpe

        def substituteServiceTypeParam(t: Type) = serviceTypeParameterSubstitutions.getOrElse(t.typeSymbol.asType, t)

        val dealiased = symbol.returnType.dealias
        val capability =
          (dealiased.typeArgs.map(substituteServiceTypeParam(_)), dealiased.typeSymbol.fullName) match {
            case (r :: e :: a :: Nil, "zio.ZIO")                     => Capability.Effect(r, e, a)
            case (r :: e :: a0 :: a :: b :: Nil, "zio.stream.ZSink") => Capability.Sink(r, e, a0, a, b)
            case (r :: e :: a :: Nil, "zio.stream.ZStream")          => Capability.Stream(r, e, a)
            case _                                                   => Capability.Method(symbol.returnType)
          }

        val typeParams = symbol.typeParams.map(_.asType)

        MethodInfo(symbol, capability, params, typeParams, i)
      }
    }

    def makeTag(name: TermName, info: MethodInfo): Tree = {
      val tagName   = capitalize(name)
      val (i, e, a) = (info.i, info.e, info.a)

      (info.capability, info.polyI, info.polyE, info.polyA) match {
        case (_: Capability.Method, false, false, false) =>
          q"case object $tagName extends Method[$i, $e, $a]"
        case (_: Capability.Method, true, false, false) =>
          q"case object $tagName extends Poly.Method.Input[$e, $a]"
        case (_: Capability.Method, false, true, false) =>
          q"case object $tagName extends Poly.Method.Error[$i, $a]"
        case (_: Capability.Method, false, false, true) =>
          q"case object $tagName extends Poly.Method.Output[$i, $e]"
        case (_: Capability.Method, true, true, false) =>
          q"case object $tagName extends Poly.Method.InputError[$a]"
        case (_: Capability.Method, true, false, true) =>
          q"case object $tagName extends Poly.Method.InputOutput[$e]"
        case (_: Capability.Method, false, true, true) =>
          q"case object $tagName extends Poly.Method.ErrorOutput[$i]"
        case (_: Capability.Method, true, true, true) =>
          q"case object $tagName extends Poly.Method.InputErrorOutput"
        case (_: Capability.Effect, false, false, false) =>
          q"case object $tagName extends Effect[$i, $e, $a]"
        case (_: Capability.Effect, true, false, false) =>
          q"case object $tagName extends Poly.Effect.Input[$e, $a]"
        case (_: Capability.Effect, false, true, false) =>
          q"case object $tagName extends Poly.Effect.Error[$i, $a]"
        case (_: Capability.Effect, false, false, true) =>
          q"case object $tagName extends Poly.Effect.Output[$i, $e]"
        case (_: Capability.Effect, true, true, false) =>
          q"case object $tagName extends Poly.Effect.InputError[$a]"
        case (_: Capability.Effect, true, false, true) =>
          q"case object $tagName extends Poly.Effect.InputOutput[$e]"
        case (_: Capability.Effect, false, true, true) =>
          q"case object $tagName extends Poly.Effect.ErrorOutput[$i]"
        case (_: Capability.Effect, true, true, true) =>
          q"case object $tagName extends Poly.Effect.InputErrorOutput"
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

      val returnType = info.capability match {
        case Capability.Method(t) => tq"$t"
        case _                    => tq"_root_.zio.ZIO[$r, $e, $a]"
      }
      val returnValue =
        (info.capability, info.params.map(_.name)) match {
          case (_: Capability.Effect, Nil)        => q"proxy($tag)"
          case (_: Capability.Effect, paramNames) => q"proxy($tag, ..$paramNames)"
          case (_: Capability.Method, Nil)        => q"rts.unsafeRunTask(proxy($tag))"
          case (_: Capability.Method, paramNames) => q"rts.unsafeRunTask(proxy($tag, ..$paramNames))"
          case (_: Capability.Sink, Nil) =>
            q"rts.unsafeRun(proxy($tag).catchAll(error => _root_.zio.UIO(_root_.zio.stream.ZSink.fail(error))))"
          case (_: Capability.Sink, paramNames) =>
            q"rts.unsafeRun(proxy($tag, ..$paramNames).catchAll(error => _root_.zio.UIO(_root_.zio.stream.ZSink.fail(error))))"
          case (_: Capability.Stream, Nil)        => q"rts.unsafeRun(proxy($tag))"
          case (_: Capability.Stream, paramNames) => q"rts.unsafeRun(proxy($tag, ..$paramNames))"
        }

      val noParams = info.symbol.paramLists.isEmpty // Scala 2.11 workaround. For some reason isVal == false in 2.11
      if (info.symbol.isVal || (noParams && TestVersion.isScala211)) q"$mods val $name: $returnType = $returnValue"
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
      .filter(member => member.owner == service.typeSymbol && member.name.toTermName != TermName("$init$"))
      .map(symbol => MethodInfo(symbol.asMethod))
      .toList
      .groupBy(_.symbol.name)

    def sortOverloads(infos: List[MethodInfo]): List[MethodInfo] = {
      import scala.math.Ordering.Implicits._
      infos.sortBy(_.symbol.paramLists.flatten.map(_.info.toString))
    }

    val tags =
      methods.collect {
        case (name, info :: Nil) =>
          makeTag(name.toTermName, info)
        case (name, infos) =>
          val tagName = capitalize(name.toTermName)
          val overloadedTags = sortOverloads(infos).zipWithIndex.map { case (info, idx) =>
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
          sortOverloads(infos).zipWithIndex.map { case (info, idx) =>
            val idxName = TermName(s"_$idx")
            makeMock(name.toTermName, info, Some(idxName))
          }
      }.toList.flatten

    val serviceClassName = TypeName(c.freshName())

    val structure =
      q"""
        object $mockName extends _root_.zio.test.mock.Mock[$env] {

          ..$tags

          val compose: $composeAsc =
            _root_.zio.ZLayer.fromServiceM { proxy =>
              withRuntime.map { rts =>
                class $serviceClassName extends $service {
                  ..$mocks
                }
                new $serviceClassName
              }
            }

          ..$body
        }
      """

    c.Expr[c.Tree](
      c.parse(
        s"$structure"
          .replaceAll(
            "\\r?\\n\\s+def \\<init\\>\\(\\) = \\{\\r?\\n\\s+super\\.\\<init\\>\\(\\);\\r?\\n\\s+\\(\\)\\r?\\n\\s+\\};?",
            ""
          )
          .replaceAll("\\{[\\r?\\n\\s]*\\};?", "")
          .replaceAll("final class \\$anon extends", "new")
          .replaceAll("\\};\\r?\\n\\s+new \\$anon\\(\\)", "\\}")
          .replaceAll("(object .+) extends scala.AnyRef", "$1")
          .replaceAll("(object .+) with scala.Product with scala.Serializable", "$1")
      )
    )
  }
}
