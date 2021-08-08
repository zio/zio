package zio.test.sbt

import zio.stm.TMap
import zio.test.AbstractRunnableSpec
import zio.{Has, UIO, ULayer, URLayer, ZEnv, ZIO}

case class LayerCache[R](
  private val layerMap: TMap[URLayer[R, _], Any]
) {
  def cacheLayers(
    layers: Iterable[URLayer[R, _]],
    env: ULayer[R]
  ): UIO[Unit] = {
    val layersToBeCached: Set[URLayer[R, _]] = layers.toSet
    ZIO.foreachPar_(layersToBeCached) { layer =>
      (env >>> layer).build.use { memoizedLayer =>
        layerMap.put(layer, memoizedLayer).commit
      }
    }
  }

  def getLayer[ROut](layer: URLayer[R, ROut]): UIO[ROut] =
    layerMap
      .get(layer)
      .collect { case Some(memoized) => memoized.asInstanceOf[ROut] }
      .commit

  val release: UIO[Unit] =
    layerMap.removeIf((_, _) => true).commit
}

object LayerCache {

  def make[R]: UIO[LayerCache[R]] =
    TMap
      .make[URLayer[R, _], Any]()
      .commit
      .map(LayerCache(_))
}

case class CustomSpecLayerCache(
  private val layerCache: LayerCache[ZEnv]
) {
  val release: UIO[Unit] = layerCache.release

  def cacheLayers(specs: Iterable[AbstractRunnableSpec]): UIO[Unit] =
    layerCache.cacheLayers(
      specs.map(_.sharedLayer),
      ZEnv.live
    )

  def getEnvironment[R <: Has[_]](layer: URLayer[ZEnv, R]): UIO[R] =
    layerCache.getLayer[R](layer)
}

object CustomSpecLayerCache {
  def make: UIO[CustomSpecLayerCache] =
    LayerCache.make[ZEnv].map(CustomSpecLayerCache.apply)
}
