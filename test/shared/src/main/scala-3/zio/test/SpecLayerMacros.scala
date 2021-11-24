package zio.test

import zio.internal.macros.LayerMacroUtils._

import scala.quoted._
import zio.internal.macros._
import zio._
import zio.test.Spec.{ExecCase, LabeledCase, ManagedCase, MultipleCase, TestCase}

object SpecLayerMacros {
  def provideImpl[R0: Type, R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R, E, T]], layer: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[Spec[R0, E, T]] = {
    val expr = LayerMacros.fromAutoImpl[R0, R, E](layer).asInstanceOf[Expr[ZLayer[R0, E, R]]]
    '{$spec.provide($expr)}
  }

  def provideSharedImpl[R0: Type, R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R,E,T]], layer: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[Spec[R0,E,T]] = {
    val expr = LayerMacros.fromAutoImpl[R0, R, E](layer)
    '{$spec.provideShared($expr)}
  }

  def provideSomeSharedImpl[R0: Type, R: Type, E: Type, T: Type]
  (spec: Expr[Spec[R,E,T]], layer: Expr[Seq[ZLayer[_,E,_]]])(using Quotes): Expr[Spec[R0,E,T]] = {
    import quotes.reflect._
    val expr = LayerMacros.fromAutoImpl[R0, R, E](layer)
    '{SpecOps($spec).provideSomeShared($expr)}
  }
}

final class SpecOps[R0, -R, +E, +T](val self: Spec[R, E, T]) extends AnyVal {

  def provideSomeShared[E1 >: E, R1](
    layer: ZLayer[R0, E1, R1]
  )(implicit ev: R0 with R1 <:< R, tagged: Tag[R0], tagged1: Tag[R1], trace: ZTraceElement): Spec[R0, E1, T] =
    self.caseValue match {
      case ExecCase(exec, spec)     => Spec.exec(exec, SpecOps(spec).provideSomeShared(layer))
      case LabeledCase(label, spec) => Spec.labeled(label, SpecOps(spec).provideSomeShared(layer))
      case ManagedCase(managed) =>
        Spec.managed(
          layer.build.flatMap { r =>
            ev.liftEnv(managed)
              .map(spec => ev.liftEnvSpec(spec).provide(r.toLayer ++ ZLayer.environment[R0]))
              .provide[E1, R0](r.toLayer ++ ZLayer.environment[R0])
          }
        )
      case MultipleCase(specs) =>
        Spec.managed(
          layer.build.map(r =>
            Spec.multiple(
              specs.map(spec => ev.liftEnvSpec(spec).provide(r.toLayer ++ ZLayer.environment[R0]))
            )
          )
        )
      case TestCase(test, annotations) =>
        Spec.test(ev.liftEnv(test).provide(layer ++ ZLayer.environment[R0]), annotations)
    }

}

