package zio

object BracketTypeInferenceSpec {
  class A
  class B
  class R
  class R1 extends R
  class R2 extends R1
  class E
  class E1 extends E

  def infersEType1: ZIO[R, E, B] = {
    val acquire: ZIO[R, E, A]      = ???
    val release: A => URIO[R, Any] = ???
    val use: A => ZIO[R, E1, B]    = ???
    acquire.bracket(release)(use)
  }

  def infersEType2: ZIO[R, E, B] = {
    val acquire: ZIO[R, E1, A]     = ???
    val release: A => URIO[R, Any] = ???
    val use: A => ZIO[R, E, B]     = ???
    acquire.bracket(release, use)
  }

  def infersRType1: ZIO[R2, E, B] = {
    val acquire: ZIO[R, E, A]               = ???
    val release: A => ZIO[R1, Nothing, Any] = ???
    val use: A => ZIO[R2, E, B]             = ???
    acquire.bracket(release)(use)
  }

  def infersRType2: ZIO[R2, E, B] = {
    val acquire: ZIO[R2, E, A]              = ???
    val release: A => ZIO[R1, Nothing, Any] = ???
    val use: A => ZIO[R, E, B]              = ???
    acquire.bracket(release, use)
  }

  def infersRType3: ZIO[R2, E, B] = {
    val acquire: ZIO[R1, E, A]              = ???
    val release: A => ZIO[R2, Nothing, Any] = ???
    val use: A => ZIO[R, E, B]              = ???
    ZIO.bracket(acquire, release, use)
  }

  def infersRType4: ZIO[R2, E, B] = {
    val acquire: ZIO[R2, E, A]              = ???
    val release: A => ZIO[R1, Nothing, Any] = ???
    val use: A => ZIO[R, E, B]              = ???
    acquire.bracket(release)(use)
  }
}
