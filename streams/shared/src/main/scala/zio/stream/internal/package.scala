package zio.stream

package object internal {
  type HashFunction[A]     = A => Long
  type NodeHashFunction[T] = HashFunction[(T, Int)]
}
