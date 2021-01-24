package zio

import scala.collection.IterableOps

private[zio] trait BuildFromCompat {

  type BuildFrom[-From, -A, +C] = scala.collection.BuildFrom[From, A, C]

  implicit def buildFromAny[Element, Collection[+Element] <: Iterable[Element] with IterableOps[Any, Collection, Any]]
    : BuildFrom[Collection[Any], Element, Collection[Element]] =
    scala.collection.BuildFrom.buildFromIterableOps[Collection, Any, Element]
}
