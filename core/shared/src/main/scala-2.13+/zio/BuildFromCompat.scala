package zio

private[zio] trait BuildFromCompat {
  type BuildFrom[-From, -A, +C] = scala.collection.BuildFrom[From, A, C]
}
