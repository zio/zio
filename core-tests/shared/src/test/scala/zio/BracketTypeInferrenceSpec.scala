package zio

object AcquireReleaseWithTypeInferenceSpec {
  class A
  class B
  class R1
  class R2
  class R3
  class E
  class E1 extends E
  class E2 extends E

  def infersRType3: ZIO[R1 with R2 with R3, E, B] = {
    val acquire: ZIO[R1, E1, A]             = ???
    val release: A => ZIO[R2, Nothing, Any] = ???
    val use: A => ZIO[R3, E2, B]            = ???
    ZIO.acquireReleaseWith(acquire)(release)(use)
  }
}
