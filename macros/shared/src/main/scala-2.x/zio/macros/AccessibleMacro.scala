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
    }).getOrElse(abort("@accessible macro can only be applied to objects containing `Service` trait."))

    val anyTree: Tree       = q"_root_.scala.Any"
    val throwableTree: Tree = q"_root_.java.lang.Throwable"

    def typeInfo(tree: AppliedTypeTree): (Boolean, Tree, Tree, Tree) =
      tree match {
        case tq"$typeName[..$typeParams]" =>
          val typeArgs  = typeParams.map(t => c.typecheck(tq"$t", c.TYPEmode, silent = true).tpe)
          val tpe       = c.typecheck(tq"$typeName[..$typeArgs]", c.TYPEmode).tpe
          val dealiased = tpe.dealias
          val isZio     = dealiased.typeSymbol.fullName == "zio.ZIO"
          val replacements: List[Tree] = (tpe.typeArgs zip typeParams).collect {
            case (NoType, t) => q"$t"
          }

          val (typeArgTrees, _) = dealiased.typeArgs.foldLeft(List.empty[Tree] -> replacements) {
            case ((acc, x :: xs), NoType) => (acc :+ x)     -> xs
            case ((acc, xs), t)           => (acc :+ q"$t") -> xs
          }

          (isZio, typeArgTrees) match {
            case (true, r :: e :: a :: Nil) => (true, r, e, a)
            case _                          => (false, anyTree, throwableTree, tree)
          }
      }

    def makeAccessor(
      name: TermName,
      impl: Tree,
      typeParams: List[TypeDef],
      r: Tree,
      e: Tree,
      a: Tree,
      paramLists: List[List[ValDef]],
      isVal: Boolean
    ): Tree = {
      val mods =
        if (impl == EmptyTree) Modifiers()
        else Modifiers(Flag.OVERRIDE)

      val returnType =
        if (r == anyTree) tq"_root_.zio.ZIO[_root_.zio.Has[Service], $e, $a]"
        else tq"_root_.zio.ZIO[_root_.zio.Has[Service] with $r, $e, $a]"

      val typeArgs = typeParams.map(_.name)

      val returnValue = paramLists match {
        case argLists if argLists.flatten.nonEmpty =>
          val argNames = argLists.map(_.map(_.name))
          q"_root_.zio.ZIO.accessM(_.get[Service].$name[..$typeArgs](...$argNames))"
        case _ =>
          q"_root_.zio.ZIO.accessM(_.get[Service].$name)"
      }

      if (isVal) q"$mods val $name: $returnType = $returnValue"
      else
        paramLists match {
          case Nil       => q"$mods def $name[..$typeParams]: $returnType = $returnValue"
          case List(Nil) => q"$mods def $name[..$typeParams](): $returnType = $returnValue"
          case _         => q"$mods def $name[..$typeParams](...$paramLists): $returnType = $returnValue"
        }
    }

    val accessors =
      moduleInfo.service.impl.body.collect {
        case DefDef(_, termName, tparams, argLists, tree: AppliedTypeTree, impl) =>
          val (isZio, r, e, a) = typeInfo(tree)
          if (isZio) Some(makeAccessor(termName, impl, tparams, r, e, a, argLists, false))
          else None

        case ValDef(_, termName, tree: AppliedTypeTree, impl) =>
          val (isZio, r, e, a) = typeInfo(tree)
          if (isZio) Some(makeAccessor(termName, impl, Nil, r, e, a, Nil, true))
          else None
      }.flatten

    moduleInfo.module match {
      case q"$mods object $tname extends { ..$earlydefns } with ..$parents { $self => ..$body }" =>
        q"""
           $mods object $tname extends { ..$earlydefns } with ..$parents { $self =>
             ..$body
             ..$accessors
           }
         """
      case _ => abort("@accesible macro failure - could not unquote annotated object.")
    }
  }
}
