package zio.stream

import org.specs2.ScalaCheck
import zio._

import scala.{Stream => _}

class SteamWithChunksCombinationsSpec(implicit ee: org.specs2.concurrent.ExecutionEnv) extends TestRuntime with GenIO with ScalaCheck {

  def is = "SteamWithChunksCombinationsSpec".title ^ s2"""
  Streams with StreamChunks combinations
    interleave  $interleaveChunk
    merge       $mergeChunk
    mergeEither $mergeEitherChunk
    zip         $zipChunk
    cross       $crossChunk
    """

  private def interleaveChunk = unsafeRun {
    val s1 = Stream(2, 3)
    val s2 = Stream(5, 6, 7)
    val s3 = StreamChunk.fromChunks(Chunk(5, 6), Chunk(7))

    for {
      res1 <- s1.interleave(s2).runCollect
      res2 <- s1.interleave(s3).runCollect
    } yield res1 must_=== res2
  }

  private def mergeChunk = {
    val s1 = Stream(2, 3)
    val s2 = Stream(5, 6, 7)
    val s3 = StreamChunk.fromChunks(Chunk(5, 6), Chunk(7))

    for {
      res1 <- s1.merge(s2).runCollect
      res2 <- s1.merge(s3).runCollect
    } yield res1.toSet must_=== res2.toSet
  }

  private def mergeEitherChunk = {
    val s1 = Stream(2, 3)
    val s2 = Stream(5, 6, 7)
    val s3 = StreamChunk.fromChunks(Chunk(5, 6), Chunk(7))

    for {
      res1 <- s1.mergeEither(s2).runCollect
      res2 <- s1.mergeEither(s3).runCollect
    } yield res1.toSet must_=== res2.toSet
  }

  private def zipChunk = {
    val s1 = Stream(2, 3)
    val s2 = Stream(5, 6, 7)
    val s3 = StreamChunk.fromChunks(Chunk(5), Chunk(6, 7))

    for {
      res1 <- s1.zip(s2).runCollect
      res2 <- s1.zip(s3).runCollect
    } yield res1 must_=== res2
  }

  private def crossChunk = {
    val s1 = Stream(2, 3)
    val s2 = Stream(5, 6, 7)
    val s3 = StreamChunk.fromChunks(Chunk(5), Chunk(6, 7))

    for {
      res1 <- s1.cross(s2).runCollect
      res2 <- s1.cross(s3).runCollect
    } yield res1 must_=== res2
  }
}
