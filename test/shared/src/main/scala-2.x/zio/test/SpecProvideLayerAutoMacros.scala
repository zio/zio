package zio.test

import zio._
import zio.internal.macros.{AutoLayerMacroUtils, Node}
import zio.test.environment.TestEnvironment

import scala.reflect.macros.blackbox

class SpecProvideLayerAutoMacros(val c: blackbox.Context) extends AutoLayerMacroUtils {
  import c.universe._

  def provideLayerAutoImpl[R: c.WeakTypeTag, E, A](layers: c.Expr[ZLayer[_, E, _]]*): c.Expr[Spec[Any, E, A]] = {
    assertProperVarArgs(layers)
    val layerExpr = generateExprGraph(layers).buildLayerFor(getRequirements[R])
    c.Expr[Spec[Any, E, A]](q"${c.prefix}.provideLayer(${layerExpr.tree})")
  }

  def provideCustomLayerAutoImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[TestEnvironment, E, A]] = {
    assertProperVarArgs(layers)
    val TestEnvRequirements = getRequirements[TestEnvironment]
    val requirements        = getRequirements[R] diff TestEnvRequirements

    val testEnvLayer = Node(List.empty, TestEnvRequirements, reify(TestEnvironment.any))
    val nodes        = (testEnvLayer +: layers.map(getNode)).toList

    val layerExpr = generateExprGraph(nodes).buildLayerFor(requirements)
    c.Expr[Spec[TestEnvironment, E, A]](q"${c.prefix}.provideCustomLayer(${layerExpr.tree})")
  }

  def provideLayerSharedAutoImpl[R: c.WeakTypeTag, E, A](layers: c.Expr[ZLayer[_, E, _]]*): c.Expr[Spec[Any, E, A]] = {
    assertProperVarArgs(layers)
    val layerExpr = generateExprGraph(layers).buildLayerFor(getRequirements[R])
    c.Expr[Spec[Any, E, A]](q"${c.prefix}.provideLayerShared(${layerExpr.tree})")
  }

  def provideCustomLayerSharedAutoImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[TestEnvironment, E, A]] = {
    assertProperVarArgs(layers)
    val TestEnvRequirements = getRequirements[TestEnvironment]
    val requirements        = getRequirements[R] diff TestEnvRequirements

    val testEnvLayer = Node(List.empty, TestEnvRequirements, reify(TestEnvironment.any))
    val nodes        = (testEnvLayer +: layers.map(getNode)).toList

    val layerExpr = generateExprGraph(nodes).buildLayerFor(requirements)
    c.Expr[Spec[TestEnvironment, E, A]](q"${c.prefix}.provideCustomLayerShared(${layerExpr.tree})")
  }
}
