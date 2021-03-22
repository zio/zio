package zio

import scala.collection.IterableOps

private[zio] trait BuildFromCompat {

  type BuildFrom[-From, -A, +C] = scala.collection.BuildFrom[From, A, C]

  @deprecated("Use BuildFrom.buildFromIterableOps or buildFromNothing instead", "1.0.6")
  def buildFromAny[Element, Collection[+Element] <: Iterable[Element] with IterableOps[Any, Collection, Any]]
    : BuildFrom[Collection[Any], Element, Collection[Element]] =
    scala.collection.BuildFrom.buildFromIterableOps[Collection, Any, Element]

  implicit def buildFromNothing[A, Collection[+Element] <: Iterable[Element] with IterableOps[A, Collection, _]]
    : BuildFrom[Collection[A], Nothing, Collection[Nothing]] =
    scala.collection.BuildFrom.buildFromIterableOps[Collection, A, Nothing]
}
