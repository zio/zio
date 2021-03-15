package zio.test

import zio._
import zio.internal.macros.{LayerMacroUtils, Node}
import zio.test.environment.TestEnvironment

import scala.reflect.macros.blackbox

class SpecLayerMacros(val c: blackbox.Context) extends LayerMacroUtils {
  import c.universe._

  def injectImpl[R: c.WeakTypeTag, E, A](layers: c.Expr[ZLayer[_, E, _]]*): c.Expr[Spec[Any, E, A]] = {
    assertProperVarArgs(layers)
    val layerExpr = buildMemoizedLayer(generateExprGraph(layers), getRequirements[R])
    c.Expr[Spec[Any, E, A]](q"${c.prefix}.provideLayerManual(${layerExpr.tree})")
  }

  def provideCustomLayerImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[TestEnvironment, E, A]] = {
    assertProperVarArgs(layers)
    val TestEnvRequirements = getRequirements[TestEnvironment]
    val requirements        = getRequirements[R] diff TestEnvRequirements

    val testEnvLayer = Node(List.empty, TestEnvRequirements, reify(TestEnvironment.any))
    val nodes        = (testEnvLayer +: layers.map(getNode)).toList

    val layerExpr = buildMemoizedLayer(generateExprGraph(nodes), requirements)
    c.Expr[Spec[TestEnvironment, E, A]](q"${c.prefix}.provideCustomLayerManual(${layerExpr.tree})")
  }

  def injectSharedImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[Any, E, A]] = {
    assertProperVarArgs(layers)
    val layerExpr = buildMemoizedLayer(generateExprGraph(layers), getRequirements[R])
    c.Expr[Spec[Any, E, A]](q"${c.prefix}.provideLayerManualShared(${layerExpr.tree})")
  }

  def injectCustomSharedImpl[R: c.WeakTypeTag, E, A](
    layers: c.Expr[ZLayer[_, E, _]]*
  ): c.Expr[Spec[TestEnvironment, E, A]] = {
    assertProperVarArgs(layers)
    val TestEnvRequirements = getRequirements[TestEnvironment]
    val requirements        = getRequirements[R] diff TestEnvRequirements

    val testEnvLayer = Node(List.empty, TestEnvRequirements, reify(TestEnvironment.any))
    val nodes        = (testEnvLayer +: layers.map(getNode)).toList

    val layerExpr = buildMemoizedLayer(generateExprGraph(nodes), requirements)
    c.Expr[Spec[TestEnvironment, E, A]](q"${c.prefix}.provideCustomLayerManualShared(${layerExpr.tree})")
  }
}
