package scalaz.zio.stream

import org.scalacheck.{ Arbitrary, Gen }
import scala.reflect.ClassTag

object ArbitraryStep {
  import ArbitraryChunk._

  implicit def arbStep[S: Arbitrary, A0: Arbitrary: ClassTag]: Arbitrary[Sink.Step[S, A0]] =
    Arbitrary {
      Gen.oneOf(
        Arbitrary.arbitrary[S].map(Sink.Step.more(_)),
        Gen
          .zip(Arbitrary.arbitrary[S], Arbitrary.arbitrary[Chunk[A0]])
          .map(tp => Sink.Step.done(tp._1, tp._2))
      )
    }
}
