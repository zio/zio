package zio.concurrent

import zio.ZIOBaseSpec
import zio.test._
import zio.test.Assertion._
import zio.{Chunk, UIO}

object ConcurrentMapSpec extends ZIOBaseSpec {
  def spec: ZSpec[Environment, Failure] =
    suite("ConcurrentMap")(
      suite("constructors")(
        testM("empty") {
          for {
            map   <- ConcurrentMap.empty[Int, String]
            items <- map.toChunk
          } yield assert(items)(isEmpty)
        },
        testM("fromIterable") {
          for {
            data  <- UIO(Chunk(1 -> "a", 2 -> "b", 3 -> "c"))
            map   <- ConcurrentMap.fromIterable(data)
            items <- map.toChunk
          } yield assert(items)(equalTo(data))
        },
        testM("make") {
          for {
            data  <- UIO(Chunk(1 -> "a", 2 -> "b", 3 -> "c"))
            map   <- ConcurrentMap.make(data: _*)
            items <- map.toChunk
          } yield assert(items)(equalTo(data))
        }
      ),
      suite("get")(
        testM("retrieves an existing key") {
          for {
            map <- ConcurrentMap.make(1 -> "a", 2 -> "b")
            res <- map.get(1)
          } yield assert(res)(isSome(equalTo("a")))
        },
        testM("returns None when retrieving a non-existing key") {
          for {
            map <- ConcurrentMap.empty[Int, String]
            res <- map.get(1)
          } yield assert(res)(isNone)
        }
      ),
      suite("putIfAbsent")(
        testM("associates the non-existing key with a given value") {
          for {
            map    <- ConcurrentMap.make(1 -> "a")
            putRes <- map.putIfAbsent(2, "b")
            getRes <- map.get(2)
          } yield assert(putRes)(isNone) && assert(getRes)(isSome(equalTo("b")))
        },
        testM("preserves the existing mappings") {
          for {
            map    <- ConcurrentMap.make(1 -> "a")
            putRes <- map.putIfAbsent(1, "b")
            getRes <- map.get(1)
          } yield assert(putRes)(isSome(equalTo("a"))) && assert(getRes)(isSome(equalTo("a")))
        }
      ),
      suite("remove")(
        testM("returns the value associated with removed key") {
          for {
            map    <- ConcurrentMap.make(1 -> "a")
            remRes <- map.remove(1)
            getRes <- map.get(1)
          } yield assert(remRes)(isSome(equalTo("a"))) && assert(getRes)(isNone)
        },
        testM("returns None if map didn't contain the given key") {
          for {
            map    <- ConcurrentMap.empty[Int, String]
            remRes <- map.remove(1)
            getRes <- map.get(1)
          } yield assert(remRes)(isNone) && assert(getRes)(isNone)
        },
        testM("succeeds if the key was mapped to the given value") {
          for {
            map    <- ConcurrentMap.make(1 -> "a")
            remRes <- map.remove(1, "a")
            getRes <- map.get(1)
          } yield assert(remRes)(isTrue) && assert(getRes)(isNone)
        },
        testM("fails if the key wasn't mapped to the given value") {
          for {
            map    <- ConcurrentMap.make(1 -> "a")
            remRes <- map.remove(1, "b")
            getRes <- map.get(1)
          } yield assert(remRes)(isFalse) && assert(getRes)(isSome(equalTo("a")))
        }
      )
    )
}
