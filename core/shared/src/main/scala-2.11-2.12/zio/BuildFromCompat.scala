package zio

import scala.collection.generic.CanBuildFrom
import scala.collection.mutable.Builder

private[zio] trait BuildFromCompat {

  type BuildFrom[-From, -A, +C] = CanBuildFrom[From, A, C]

  implicit class BuildFromOps[From, A, C](private val self: BuildFrom[From, A, C]) {
    def newBuilder(from: From): Builder[A, C] =
      self.apply(from)
  }
}
