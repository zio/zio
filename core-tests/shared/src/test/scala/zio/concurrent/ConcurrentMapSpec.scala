package zio.concurrent

import zio.ZIOBaseSpec
import zio.test._
import zio.test.Assertion._
import zio.{Chunk, UIO}

object ConcurrentMapSpec extends ZIOBaseSpec {
  def spec: ZSpec[Environment, Failure] =
    suite("ConcurrentMap")(
      suite("compute")(
        testM("computes a new value") {
          for {
            map      <- ConcurrentMap.make(1 -> 100)
            computed <- map.compute(1, _ + _)
            stored   <- map.get(1)
          } yield assert(computed)(isSome(equalTo(101))) && assert(computed)(equalTo(stored))
        },
        testM("returns None if remap produced null (e.g. missing key)") {
          for {
            map      <- ConcurrentMap.empty[Int, String]
            computed <- map.compute(1, (_, v) => v)
            stored   <- map.get(1)
          } yield assert(computed)(isNone) && assert(computed)(equalTo(stored))
        }
      ),
      suite("computeIfAbsent")(
        testM("computes a value of a non-existing key") {
          for {
            map      <- ConcurrentMap.empty[String, Int]
            computed <- map.computeIfAbsent("abc", _.length)
            stored   <- map.get("abc")
          } yield assert(computed)(equalTo(3)) && assert(stored)(isSome(equalTo(computed)))
        },
        testM("preserves the existing bindings") {
          for {
            map      <- ConcurrentMap.make("abc" -> 3)
            computed <- map.computeIfAbsent("abc", _ => 10)
            stored   <- map.get("abc")
          } yield assert(computed)(equalTo(3)) && assert(stored)(isSome(equalTo(computed)))
        }
      ),
      suite("computeIfPresent")(
        testM("computes a value of an existing key") {
          for {
            map      <- ConcurrentMap.make(1 -> 100)
            computed <- map.computeIfPresent(1, _ + _)
            stored   <- map.get(1)
          } yield assert(computed)(isSome(equalTo(101))) && assert(computed)(equalTo(stored))
        },
        testM("returns None if key doesn't exist") {
          for {
            map      <- ConcurrentMap.empty[Int, String]
            computed <- map.computeIfPresent(1, (_, _) => "test")
            stored   <- map.get(1)
          } yield assert(computed)(isNone) && assert(computed)(equalTo(stored))
        }
      ),
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
      suite("put")(
        testM("associates the non-existing key with a given value") {
          for {
            map    <- ConcurrentMap.empty[Int, String]
            putRes <- map.put(1, "a")
            getRes <- map.get(1)
          } yield assert(putRes)(isNone) && assert(getRes)(isSome(equalTo("a")))
        },
        testM("overrides the existing mappings") {
          for {
            map    <- ConcurrentMap.make(1 -> "a")
            putRes <- map.put(1, "b")
            getRes <- map.get(1)
          } yield assert(putRes)(isSome(equalTo("a"))) && assert(getRes)(isSome(equalTo("b")))
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
      ),
      suite("replace")(
        testM("returns the replaced value") {
          for {
            map    <- ConcurrentMap.make(1 -> "a")
            repRes <- map.replace(1, "b")
            getRes <- map.get(1)
          } yield assert(repRes)(isSome(equalTo("a"))) && assert(getRes)(isSome(equalTo("b")))
        },
        testM("returns None if map didn't contain the given key") {
          for {
            map    <- ConcurrentMap.empty[Int, String]
            repRes <- map.replace(1, "b")
            getRes <- map.get(1)
          } yield assert(repRes)(isNone) && assert(getRes)(isNone)
        },
        testM("succeeds if the key was mapped to the given value") {
          for {
            map    <- ConcurrentMap.make(1 -> "a")
            repRes <- map.replace(1, "a", "b")
            getRes <- map.get(1)
          } yield assert(repRes)(isTrue) && assert(getRes)(isSome(equalTo("b")))
        },
        testM("fails if the key wasn't mapped to the given value") {
          for {
            map    <- ConcurrentMap.make(1 -> "a")
            repRes <- map.replace(1, "b", "c")
            getRes <- map.get(1)
          } yield assert(repRes)(isFalse) && assert(getRes)(isSome(equalTo("a")))
        }
      )
    )
}
