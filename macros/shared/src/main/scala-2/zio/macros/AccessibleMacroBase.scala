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

package zio.macros

import com.github.ghik.silencer.silent

import scala.reflect.macros.whitebox

private[macros] abstract class AccessibleMacroBase(val c: whitebox.Context) {

  import c.universe._

  protected val any: Tree       = tq"_root_.scala.Any"
  protected val throwable: Tree = tq"_root_.java.lang.Throwable"
  protected val nothing: Tree   = tq"_root_.scala.Nothing"

  protected val zioServiceName: TermName  = TermName("Service")
  protected val constructorName: TermName = TermName("$init$")

  sealed trait Info {
    def service: ClassDef

    def serviceTypeParams: List[TypeDef]
  }

  protected case class PlainModuleInfo(
    module: Option[ModuleDef],
    service: ClassDef,
    serviceTypeParams: List[TypeDef]
  ) extends Info

  protected case class ModuleInfo(
    module: ModuleDef,
    service: ClassDef,
    serviceTypeParams: List[TypeDef]
  ) extends Info

  protected sealed trait Capability

  object Capability {
    case class Effect(r: Tree, e: Tree, a: Tree)                               extends Capability
    case class Managed(r: Tree, e: Tree, a: Tree)                              extends Capability
    case class Method(a: Tree)                                                 extends Capability
    case class Sink(r: Tree, ine: Tree, a: Tree, oute: Tree, l: Tree, b: Tree) extends Capability
    case class Stream(r: Tree, e: Tree, a: Tree)                               extends Capability
    case class ThrowingMethod(a: Tree)                                         extends Capability
  }

  protected case class TypeInfo(capability: Capability) {

    val r: Tree = capability match {
      case Capability.Effect(r, _, _)        => r
      case Capability.Managed(r, _, _)       => r
      case Capability.Sink(r, _, _, _, _, _) => r
      case Capability.Stream(r, _, _)        => r
      case Capability.Method(_)              => any
      case Capability.ThrowingMethod(_)      => any
    }

    val e: Tree = capability match {
      case Capability.Effect(_, e, _)        => e
      case Capability.Managed(_, e, _)       => e
      case Capability.Sink(_, _, _, e, _, _) => e
      case Capability.Stream(_, e, _)        => e
      case Capability.Method(_)              => nothing
      case Capability.ThrowingMethod(_)      => throwable
    }

    val a: Tree = capability match {
      case Capability.Effect(_, _, a)             => a
      case Capability.Managed(_, _, a)            => a
      case Capability.Sink(_, ine, a, oute, l, b) => tq"_root_.zio.stream.ZSink[$any, $ine, $a, $oute, $l, $b]"
      case Capability.Stream(_, e, a)             => tq"_root_.zio.stream.ZStream[$any, $e, $a]"
      case Capability.Method(a)                   => a
      case Capability.ThrowingMethod(a)           => a
    }
  }

  protected val macroName: String

  protected def macroApply(annottees: Seq[c.Tree]): MacroApply

  final def apply(annottees: c.Tree*): c.Tree = macroApply(annottees)()

  protected def abort(msg: String): Nothing = c.abort(c.enclosingPosition, s"@$macroName macro failure - $msg")

  protected abstract class MacroApply(annottees: Seq[c.Tree]) {

    protected def treeTpe(tree: Tree): Type

    protected def typeArgsForService(serviceTypeParams: List[TypeDef]): List[TypeName]

    protected def typeParamsForAccessors(serviceTypeParams: List[TypeDef]): List[TypeDef]

    protected lazy val moduleInfo: Info = (annottees match {
      case (module: ModuleDef) :: Nil =>
        module.impl.body.collectFirst {
          case service @ ClassDef(_, name, tparams, _) if name.toTermName == zioServiceName =>
            ModuleInfo(module, service, tparams)
        }
      case (module: ModuleDef) :: (service @ ClassDef(_, _, tparams, _)) :: Nil =>
        Some(PlainModuleInfo(Some(module), service, tparams))
      case (service @ ClassDef(_, _, tparams, _)) :: (module: ModuleDef) :: Nil =>
        Some(PlainModuleInfo(Some(module), service, tparams))
      case (service @ ClassDef(_, _, tparams, _)) :: Nil =>
        Some(PlainModuleInfo(None, service, tparams))
      case _ => None
    }).getOrElse(abort(s"@$macroName macro can only be applied to objects containing `Service` trait."))

    @silent("pattern var [^\\s]+ in method unapply is never used")
    private def typeInfo(tree: Tree): TypeInfo =
      (tree: @unchecked) match {
        case tq"$_[..$typeParams]" =>
          val tpe       = treeTpe(tree)
          val dealiased = tpe.dealias
          val replacements: List[Tree] =
            (tpe.typeArgs zip typeParams).collect { case (NoType, t) => q"$t" }

          val (typeArgTrees, _) = dealiased.typeArgs.foldLeft(List.empty[Tree] -> replacements) {
            case ((acc, x :: xs), NoType) => (acc :+ x)     -> xs
            case ((acc, xs), t)           => (acc :+ q"$t") -> xs
          }

          (dealiased.typeSymbol.fullName, typeArgTrees) match {
            case ("zio.ZIO", r :: e :: a :: Nil)      => TypeInfo(Capability.Effect(r, e, a))
            case ("zio.ZManaged", r :: e :: a :: Nil) => TypeInfo(Capability.Managed(r, e, a))
            case ("zio.stream.ZSink", r :: ine :: a :: oute :: l :: b :: Nil) =>
              TypeInfo(Capability.Sink(r, ine, a, oute, l, b))
            case ("zio.stream.ZStream", r :: e :: a :: Nil) => TypeInfo(Capability.Stream(r, e, a))
            case _                                          => TypeInfo(Capability.Method(tree))
          }
      }

    private def makeAccessor(
      name: TermName,
      info: TypeInfo,
      serviceTypeParams: List[TypeDef],
      typeParams: List[TypeDef],
      paramLists: List[List[ValDef]],
      isVal: Boolean,
      serviceName: TypeName
    ): Tree = {

      val serviceTypeArgs = typeArgsForService(serviceTypeParams)

      val returnType = info.capability match {
        case Capability.Effect(r, e, a) =>
          if (r != any) tq"_root_.zio.ZIO[$serviceName[..$serviceTypeArgs] with $r, $e, $a]"
          else tq"_root_.zio.ZIO[$serviceName[..$serviceTypeArgs], $e, $a]"
        case Capability.Managed(r, e, a) =>
          if (r != any) tq"_root_.zio.ZManaged[$serviceName[..$serviceTypeArgs] with $r, $e, $a]"
          else tq"_root_.zio.ZManaged[$serviceName[..$serviceTypeArgs], $e, $a]"
        case Capability.Stream(r, e, a) =>
          if (r != any) tq"_root_.zio.stream.ZStream[$serviceName[..$serviceTypeArgs] with $r, $e, $a]"
          else tq"_root_.zio.stream.ZStream[$serviceName[..$serviceTypeArgs], $e, $a]"
        case Capability.Sink(r, ine, a, oute, l, b) =>
          if (r != any)
            tq"_root_.zio.stream.ZSink[$serviceName[..$serviceTypeArgs] with $r, $ine, $a, $oute, $l, $b]"
          else tq"_root_.zio.stream.ZSink[$serviceName[..$serviceTypeArgs], $ine, $a, $oute, $l, $b]"
        case Capability.Method(a) =>
          tq"_root_.zio.ZIO[$serviceName[..$serviceTypeArgs], $nothing, $a]"
        case Capability.ThrowingMethod(a) =>
          tq"_root_.zio.ZIO[$serviceName[..$serviceTypeArgs], $throwable, $a]"
      }

      val typeArgs = typeParams.map(_.name)

      def isRepeatedParamType(vd: ValDef) = vd.tpt match {
        case AppliedTypeTree(Select(_, nme), _) => nme == definitions.RepeatedParamClass.name
        case _                                  => false
      }

      val argNames = paramLists.map(_.map { arg =>
        if (isRepeatedParamType(arg)) q"${arg.name}: _*"
        else q"${arg.name}"
      })

      val returnValue = (info.capability, paramLists) match {
        case (_: Capability.Effect, argLists) if argLists.flatten.nonEmpty || argLists.size == 1 =>
          q"_root_.zio.ZIO.environmentWithZIO(_.get[$serviceName[..$serviceTypeArgs]].$name[..$typeArgs](...$argNames))"
        case (_: Capability.Effect, _) =>
          q"_root_.zio.ZIO.environmentWithZIO(_.get[$serviceName[..$serviceTypeArgs]].$name)"
        case (_: Capability.Managed, argLists) if argLists.flatten.nonEmpty || argLists.size == 1 =>
          q"_root_.zio.ZManaged.environmentWithZIOManaged(_.get[$serviceName[..$serviceTypeArgs]].$name[..$typeArgs](...$argNames))"
        case (_: Capability.Managed, _) =>
          q"_root_.zio.ZManaged.environmentWithZIOManaged(_.get[$serviceName[..$serviceTypeArgs]].$name[..$typeArgs])"
        case (_: Capability.Stream, argLists) if argLists.flatten.nonEmpty || argLists.size == 1 =>
          q"_root_.zio.stream.ZStream.environmentWithZIOStream(_.get[$serviceName[..$serviceTypeArgs]].$name[..$typeArgs](...$argNames))"
        case (_: Capability.Stream, _) =>
          q"_root_.zio.stream.ZStream.environmentWithZIOStream(_.get[$serviceName[..$serviceTypeArgs]].$name)"
        case (Capability.Sink(r, ine, a, oute, l, b), argLists) if argLists.flatten.nonEmpty || argLists.size == 1 =>
          q"_root_.zio.stream.ZSink.environmentWithZIOSink[$serviceName[..$serviceTypeArgs]][$serviceName[..$serviceTypeArgs] with $r, $ine, $a, $oute, $l, $b](_.get[$serviceName[..$serviceTypeArgs]].$name[..$typeArgs](...$argNames))"
        case (Capability.Sink(r, ine, a, oute, l, b), _) =>
          q"_root_.zio.stream.ZSink.environmentWithZIOSink[$serviceName[..$serviceTypeArgs]][$serviceName[..$serviceTypeArgs] with $r, $ine, $a, $oute, $l, $b](_.get[$serviceName[..$serviceTypeArgs]].$name)"
        case (_: Capability.ThrowingMethod, argLists) if argLists.flatten.nonEmpty || argLists.size == 1 =>
          val argNames = argLists.map(_.map(_.name))
          q"_root_.zio.ZIO.environmentWithZIO(s => ZIO(s.get[$serviceName[..$serviceTypeArgs]].$name[..$typeArgs](...$argNames)))"
        case (_: Capability.ThrowingMethod, _) =>
          q"_root_.zio.ZIO.environmentWithZIO(s => ZIO(s.get[$serviceName[..$serviceTypeArgs]].$name))"
        case (_, argLists) if argLists.flatten.nonEmpty || argLists.size == 1 =>
          val argNames = argLists.map(_.map(_.name))
          q"_root_.zio.ZIO.service[$serviceName[..$serviceTypeArgs]].map(_.$name[..$typeArgs](...$argNames))"
        case (_, _) =>
          q"_root_.zio.ZIO.service[$serviceName[..$serviceTypeArgs]].map(_.$name)"
      }

      val accessorTypeParams = typeParamsForAccessors(serviceTypeParams)

      if (isVal && accessorTypeParams.isEmpty) q"val $name: $returnType = $returnValue"
      else {
        val allTypeParams =
          accessorTypeParams.map(tp => TypeDef(Modifiers(Flag.PARAM), tp.name, tp.tparams, tp.rhs)) ::: typeParams
        paramLists match {
          case Nil =>
            q"def $name[..$allTypeParams](implicit ev: _root_.izumi.reflect.Tag[$serviceName[..$serviceTypeArgs]]): $returnType = $returnValue"
          case List(Nil) =>
            q"def $name[..$allTypeParams]()(implicit ev: _root_.izumi.reflect.Tag[$serviceName[..$serviceTypeArgs]]): $returnType = $returnValue"
          case _ =>
            q"def $name[..$allTypeParams](...$paramLists)(implicit ev: _root_.izumi.reflect.Tag[$serviceName[..$serviceTypeArgs]]): $returnType = $returnValue"
        }
      }
    }

    @silent("pattern var [^\\s]+ in method unapply is never used")
    private def withThrowing(mods: Modifiers, tree: Tree) = {
      val isThrowing = mods.annotations.exists {
        case q"new $name" => name.toString == classOf[throwing].getSimpleName()
        case _            => true
      }
      val info = typeInfo(tree)
      info.capability match {
        case Capability.Method(v) if isThrowing => info.copy(capability = Capability.ThrowingMethod(v))
        case _                                  => info
      }
    }

    @silent("pattern var [^\\s]+ in method unapply is never used")
    final def apply(): c.Tree = {

      val accessors =
        moduleInfo.service.impl.body.collect {
          case DefDef(mods, termName, tparams, argLists, tree: Tree, _) if termName != constructorName =>
            makeAccessor(
              termName,
              withThrowing(mods, tree),
              moduleInfo.serviceTypeParams,
              tparams,
              argLists,
              isVal = false,
              moduleInfo.service.name
            )

          case ValDef(_, termName, tree: Tree, _) =>
            makeAccessor(
              termName,
              typeInfo(tree),
              moduleInfo.serviceTypeParams,
              Nil,
              Nil,
              isVal = true,
              moduleInfo.service.name
            )
        }

      moduleInfo match {
        case ModuleInfo(q"$mods object $tname extends { ..$earlydefns } with ..$parents { $self => ..$body }", _, _) =>
          q"""
           $mods object $tname extends { ..$earlydefns } with ..$parents { $self =>
             ..$body
             ..$accessors
           }
         """
        case PlainModuleInfo(
              Some(q"$mods object $tname extends { ..$earlydefns } with ..$parents { $self => ..$body }"),
              service,
              _
            ) =>
          q"""
          $service
          $mods object $tname extends { ..$earlydefns } with ..$parents { $self =>
            ..$body
            ..$accessors
          }
        """
        case PlainModuleInfo(None, service, _) =>
          q"""
          $service
          object ${service.name.toTermName} {
            ..$accessors
          }
        """
        case _ => abort("could not unquote annotated object")
      }
    }
  }

}
