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

package zio.macros

import scala.reflect.macros.whitebox.Context

import com.github.ghik.silencer.silent

/**
 * Generates method accessors for a service into annotated object.
 */
private[macros] class AccessibleMacro(val c: Context) {
  import c.universe._

  protected case class ModuleInfo(
    module: ModuleDef,
    service: ClassDef
  )

  def abort(msg: String) = c.abort(c.enclosingPosition, msg)

  @silent("pattern var [^\\s]+ in method unapply is never used")
  def apply(annottees: c.Tree*): c.Tree = {

    val moduleInfo = (annottees match {
      case (module: ModuleDef) :: Nil =>
        module.impl.body.collectFirst {
          case service @ ClassDef(_, name, _, _) if name.toTermName.toString == "Service" =>
            ModuleInfo(module, service)
        }
      case _ => None
    }).getOrElse(abort("Accessible macro can only be applied to objects containing `Service` trait."))

    val anyType: Type       = c.typecheck(q"(??? : _root_.scala.Any)").tpe
    val throwableType: Type = c.typecheck(q"(??? : _root_.java.lang.Throwable)").tpe

    object typeInfo {
      def unapply(tree: AppliedTypeTree): Option[(Boolean, Type, Type, Type)] = {
        val typeName = TypeName(c.freshName())
        val tpe      = c.typecheck(tq"({ type $typeName = $tree })#$typeName", c.TYPEmode).tpe.dealias
        tpe.typeArgs match {
          case r :: e :: a :: Nil if tpe.typeSymbol.fullName == "zio.ZIO" => Some((true, r, e, a))
          case _                                                          => Some((false, anyType, throwableType, tpe))
        }
      }
    }

    def makeAccessor(
      name: TermName,
      impl: Tree,
      r: Type,
      e: Type,
      a: Type,
      paramLists: List[List[ValDef]],
      isVal: Boolean
    ): Tree = {
      val mods =
        if (impl == EmptyTree) Modifiers()
        else Modifiers(Flag.OVERRIDE)

      val returnType =
        if (r == anyType) tq"_root_.zio.ZIO[_root_.zio.Has[Service], $e, $a]"
        else tq"_root_.zio.ZIO[_root_.zio.Has[Service] with $r, $e, $a]"

      val returnValue = paramLists match {
        case argLists if argLists.flatten.nonEmpty =>
          val argNames = argLists.map(_.map(_.name))
          q"_root_.zio.ZIO.accessM(_.get[Service].$name(...$argNames))"
        case _ =>
          q"_root_.zio.ZIO.accessM(_.get[Service].$name)"
      }

      if (isVal) q"$mods val $name: $returnType = $returnValue"
      else
        paramLists match {
          case Nil       => q"$mods def $name: $returnType = $returnValue"
          case List(Nil) => q"$mods def $name(): $returnType = $returnValue"
          case _         => q"$mods def $name(...$paramLists): $returnType = $returnValue"
        }
    }

    val accessors =
      moduleInfo.service.impl.body.collect {
        case DefDef(_, termName, _, argLists, typeInfo(isZio, r, e, a), impl) if isZio =>
          makeAccessor(termName, impl, r, e, a, argLists, false)

        case ValDef(_, termName, typeInfo(isZio, r, e, a), impl) if isZio =>
          makeAccessor(termName, impl, r, e, a, Nil, true)
      }

    moduleInfo.module match {
      case q"$mods object $tname extends { ..$earlydefns } with ..$parents { $self => ..$body }" =>
        q"""
           $mods object $tname extends { ..$earlydefns } with ..$parents { $self =>
             ..$body
             ..$accessors
           }
         """
      case _ => abort("Accesible macro failure - could not unquote annotated object.")
    }
  }
}
