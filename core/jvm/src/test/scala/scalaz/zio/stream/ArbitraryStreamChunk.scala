package scalaz.zio.stream

import org.scalacheck.Arbitrary
import scala.reflect.ClassTag
import ArbitraryChunk._
import ArbitraryStream._
import org.scalacheck.Gen
import scalaz.zio.Chunk

object ArbitraryStreamChunk {

  implicit def arbStreamChunk[T: ClassTag: Arbitrary]: Arbitrary[StreamChunk[Any, String, T]] =
    Arbitrary {
      Gen.oneOf(
        genFailingStream[Chunk[T]].map(StreamChunk(_)),
        genPureStream[Chunk[T]].map(StreamChunkPure(_)),
        genSucceededStream[Chunk[T]].map(StreamChunk(_))
      )
    }

  implicit def arbSucceededStreamChunk[T: ClassTag: Arbitrary]: Arbitrary[StreamChunk[Any, Nothing, T]] =
    Arbitrary {
      Gen.oneOf(
        genPureStream[Chunk[T]].map(StreamChunkPure(_)),
        genSucceededStream[Chunk[T]].map(StreamChunk(_))
      )
    }
}
