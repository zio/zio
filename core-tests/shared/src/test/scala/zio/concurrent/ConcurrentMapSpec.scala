package zio.concurrent

import zio.ZIOBaseSpec
import zio.test._
import zio.test.Assertion._

object ConcurrentMapSpec extends ZIOBaseSpec {
  def spec: ZSpec[Environment, Failure] =
    suite("ConcurrentMap")(
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
      )
    )
}
