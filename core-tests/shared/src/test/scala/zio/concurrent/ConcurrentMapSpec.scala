package zio.concurrent

import zio._
import zio.test._
import zio.test.Assertion._

object ConcurrentMapSpec extends DefaultRunnableSpec {
  def spec: ZSpec[Environment, Failure] =
    suite("ConcurrentMap")(
      suite("collectFirst")(
        testM("returns Some of first result for which the partial function is defined") {
          for {
            map <- ConcurrentMap.make((1, "A"), (2, "B"), (3, "C"))
            res <- map.collectFirst { case (3, _) => "Three" }
          } yield assertTrue(res.get == "Three")
        },
        testM("returns None when the partial function is defined for no values") {
          for {
            map <- ConcurrentMap.make((1, "A"), (2, "B"), (3, "C"))
            res <- map.collectFirst { case (4, _) => "Four" }
          } yield assertTrue(res.isEmpty)
        }
      ),
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
      suite("exists")(
        testM("returns true when element which satisfies given predicate exists within the map") {
          for {
            map    <- ConcurrentMap.make((1, "A"), (2, "B"), (3, "C"))
            exists <- map.exists((k, _) => k % 2 == 0)
          } yield assertTrue(exists)
        },
        testM("returns false when no elements in the map satisfy the predicate") {
          for {
            map    <- ConcurrentMap.make((1, "A"), (2, "B"), (3, "C"))
            exists <- map.exists((k, _) => k == 4)
          } yield assertTrue(!exists)
        }
      ),
      suite("fold")(
        testM("returns the sum of the map's values") {
          for {
            map <- ConcurrentMap.make(("A", 1), ("B", 2), ("C", 3))
            res <- map.fold(0) { case (acc, (_, value)) => acc + value }
          } yield assertTrue(res == 6)
        }
      ),
      suite("forall")(
        testM("returns true when predicate holds for all elements of the map") {
          for {
            map    <- ConcurrentMap.make(("A", 1), ("B", 2), ("C", 3))
            result <- map.forall((_, v) => v < 4)
          } yield assertTrue(result)
        },
        testM("returns false when predicate fails for any element of the map") {
          for {
            map    <- ConcurrentMap.make(("A", 1), ("B", 2), ("C", 3))
            result <- map.forall((_, v) => v < 3)
          } yield assertTrue(!result)
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
      suite("putAll")(
        testM("associates all non-existent keys with given value") {
          for {
            map  <- ConcurrentMap.empty[Int, String]
            _    <- map.putAll((1, "A"), (2, "B"), (3, "C"))
            resA <- map.get(1)
            resB <- map.get(2)
            resC <- map.get(3)
          } yield assertTrue(
            resA.get == "A",
            resB.get == "B",
            resC.get == "C"
          )
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
      suite("removeIf")(
        testM("removes the values that match a given predicate") {
          for {
            map  <- ConcurrentMap.make((1, "A"), (2, "B"), (3, "C"))
            _    <- map.removeIf((k, _) => k != 1)
            aRes <- map.get(1)
            bRes <- map.get(2)
            cRes <- map.get(3)
          } yield assertTrue(
            aRes.get == "A",
            bRes.isEmpty,
            cRes.isEmpty
          )
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
      ),
      suite("retainIf")(
        testM("retains values that satisfy a given predicate") {
          for {
            map  <- ConcurrentMap.make((1, "A"), (2, "B"), (3, "C"))
            _    <- map.retainIf((k, _) => k == 1)
            aRes <- map.get(1)
            bRes <- map.get(2)
            cRes <- map.get(3)
          } yield assertTrue(
            aRes.get == "A",
            bRes.isEmpty,
            cRes.isEmpty
          )
        }
      )
    )
}
