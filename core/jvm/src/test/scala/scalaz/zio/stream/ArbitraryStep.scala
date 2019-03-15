package scalaz.zio.stream

import org.scalacheck.{ Arbitrary, Gen }
import scala.reflect.ClassTag
import scalaz.zio.Chunk

object ArbitraryStep {
  import ArbitraryChunk._

  implicit def arbStep[S: Arbitrary, A0: Arbitrary: ClassTag]: Arbitrary[ZSink.Step[S, A0]] =
    Arbitrary {
      Gen.oneOf(
        Arbitrary.arbitrary[S].map(ZSink.Step.more(_)),
        Gen
          .zip(Arbitrary.arbitrary[S], Arbitrary.arbitrary[Chunk[A0]])
          .map(tp => ZSink.Step.done(tp._1, tp._2))
      )
    }
}
