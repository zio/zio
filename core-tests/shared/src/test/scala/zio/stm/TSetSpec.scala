package zio.stm

import zio.{Chunk, ZIOBaseSpec}
import zio.test.Assertion._
import zio.test._

object TSetSpec extends ZIOBaseSpec {

  def spec = suite("TSet")(
    suite("factories")(
      test("apply") {
        val tx = TSet.make(1, 2, 2, 3).flatMap[Any, Nothing, List[Int]](_.toList)
        assertZIO(tx.commit)(hasSameElements(List(1, 2, 3)))
      },
      test("empty") {
        val tx = TSet.empty[Int].flatMap[Any, Nothing, List[Int]](_.toList)
        assertZIO(tx.commit)(isEmpty)
      },
      test("fromIterable") {
        val tx = TSet.fromIterable(List(1, 2, 2, 3)).flatMap[Any, Nothing, List[Int]](_.toList)
        assertZIO(tx.commit)(hasSameElements(List(1, 2, 3)))
      }
    ),
    suite("lookups")(
      test("contains existing element") {
        val tx = TSet.make(1, 2, 3, 4).flatMap[Any, Nothing, Boolean](_.contains(1))
        assertZIO(tx.commit)(isTrue)
      },
      test("contains non-existing element") {
        val tx = TSet.make(1, 2, 3, 4).flatMap[Any, Nothing, Boolean](_.contains(0))
        assertZIO(tx.commit)(isFalse)
      },
      test("collect all elements") {
        val tx = TSet.make(1, 2, 3, 4).flatMap[Any, Nothing, List[Int]](_.toList)
        assertZIO(tx.commit)(hasSameElements(List(1, 2, 3, 4)))
      },
      test("cardinality") {
        val tx = TSet.make(1, 2, 3, 4).flatMap[Any, Nothing, Int](_.size)
        assertZIO(tx.commit)(equalTo(4))
      }
    ),
    suite("insertion and removal")(
      test("add new element") {
        val tx =
          for {
            tset <- TSet.empty[Int]
            _    <- tset.put(1)
            res  <- tset.toList
          } yield res

        assertZIO(tx.commit)(hasSameElements(List(1)))
      },
      test("add duplicate element") {
        val tx =
          for {
            tset <- TSet.make(1)
            _    <- tset.put(1)
            res  <- tset.toList
          } yield res

        assertZIO(tx.commit)(hasSameElements(List(1)))
      },
      test("remove existing element") {
        val tx =
          for {
            tset <- TSet.make(1, 2)
            _    <- tset.delete(1)
            res  <- tset.toList
          } yield res

        assertZIO(tx.commit)(hasSameElements(List(2)))
      },
      test("remove non-existing element") {
        val tx =
          for {
            tset <- TSet.make(1, 2)
            _    <- tset.delete(3)
            res  <- tset.toList
          } yield res

        assertZIO(tx.commit)(hasSameElements(List(1, 2)))
      }
    ),
    suite("transformations")(
      test("retainIf") {
        val tx =
          for {
            tset    <- TSet.make("a", "aa", "aaa")
            removed <- tset.retainIf(_ == "aa")
            a       <- tset.contains("a")
            aa      <- tset.contains("aa")
            aaa     <- tset.contains("aaa")
          } yield (removed, a, aa, aaa)

        assertZIO(tx.commit)(equalTo((Chunk("aaa", "a"), false, true, false)))
      },
      test("retainIfDiscard") {
        val tx =
          for {
            tset <- TSet.make("a", "aa", "aaa")
            _    <- tset.retainIfDiscard(_ == "aa")
            a    <- tset.contains("a")
            aa   <- tset.contains("aa")
            aaa  <- tset.contains("aaa")
          } yield (a, aa, aaa)

        assertZIO(tx.commit)(equalTo((false, true, false)))
      },
      test("removeIf") {
        val tx =
          for {
            tset    <- TSet.make("a", "aa", "aaa")
            removed <- tset.removeIf(_ == "aa")
            a       <- tset.contains("a")
            aa      <- tset.contains("aa")
            aaa     <- tset.contains("aaa")
          } yield (removed, a, aa, aaa)

        assertZIO(tx.commit)(equalTo((Chunk("aa"), true, false, true)))
      },
      test("removeIfDiscard") {
        val tx =
          for {
            tset <- TSet.make("a", "aa", "aaa")
            _    <- tset.removeIfDiscard(_ == "aa")
            a    <- tset.contains("a")
            aa   <- tset.contains("aa")
            aaa  <- tset.contains("aaa")
          } yield (a, aa, aaa)

        assertZIO(tx.commit)(equalTo((true, false, true)))
      },
      test("transform") {
        val tx =
          for {
            tset <- TSet.make(1, 2, 3)
            _    <- tset.transform(_ * 2)
            res  <- tset.toList
          } yield res

        assertZIO(tx.commit)(hasSameElements(List(2, 4, 6)))
      },
      test("transform and shrink") {
        val tx =
          for {
            tset <- TSet.make(1, 2, 3)
            _    <- tset.transform(_ => 1)
            res  <- tset.toList
          } yield res

        assertZIO(tx.commit)(hasSameElements(List(1)))
      },
      test("transformSTM") {
        val tx =
          for {
            tset <- TSet.make(1, 2, 3)
            _    <- tset.transformSTM(a => STM.succeed(a * 2))
            res  <- tset.toList
          } yield res

        assertZIO(tx.commit)(hasSameElements(List(2, 4, 6)))
      },
      test("transformSTM and shrink") {
        val tx =
          for {
            tset <- TSet.make(1, 2, 3)
            _    <- tset.transformSTM(_ => STM.succeed(1))
            res  <- tset.toList
          } yield res

        assertZIO(tx.commit)(hasSameElements(List(1)))
      }
    ),
    suite("folds")(
      test("fold on non-empty set") {
        val tx =
          for {
            tset <- TSet.make(1, 2, 3)
            res  <- tset.fold(0)(_ + _)
          } yield res

        assertZIO(tx.commit)(equalTo(6))
      },
      test("fold on empty set") {
        val tx =
          for {
            tset <- TSet.empty[Int]
            res  <- tset.fold(0)(_ + _)
          } yield res

        assertZIO(tx.commit)(equalTo(0))
      },
      test("foldSTM on non-empty set") {
        val tx =
          for {
            tset <- TSet.make(1, 2, 3)
            res  <- tset.foldSTM(0)((acc, a) => STM.succeed(acc + a))
          } yield res

        assertZIO(tx.commit)(equalTo(6))
      },
      test("foldSTM on empty set") {
        val tx =
          for {
            tset <- TSet.empty[Int]
            res  <- tset.foldSTM(0)((acc, a) => STM.succeed(acc + a))
          } yield res

        assertZIO(tx.commit)(equalTo(0))
      },
      test("toSet") {
        val set = Set(1, 2, 3)

        val tx =
          for {
            tset <- TSet.fromIterable(set)
            res  <- tset.toSet
          } yield res

        assertZIO(tx.commit)(hasSameElements(set))
      }
    ),
    suite("set operations")(
      test("diff") {
        val tx =
          for {
            tset1 <- TSet.make(1, 2, 3)
            tset2 <- TSet.make(1, 4, 5)
            _     <- tset1.diff(tset2)
            res   <- tset1.toList
          } yield res

        assertZIO(tx.commit)(hasSameElements(List(2, 3)))
      },
      test("intersect") {
        val tx =
          for {
            tset1 <- TSet.make(1, 2, 3)
            tset2 <- TSet.make(1, 4, 5)
            _     <- tset1.intersect(tset2)
            res   <- tset1.toList
          } yield res

        assertZIO(tx.commit)(hasSameElements(List(1)))
      },
      test("union") {
        val tx =
          for {
            tset1 <- TSet.make(1, 2, 3)
            tset2 <- TSet.make(1, 4, 5)
            _     <- tset1.union(tset2)
            res   <- tset1.toList
          } yield res

        assertZIO(tx.commit)(hasSameElements(List(1, 2, 3, 4, 5)))
      }
    )
  ) @@ TestAspect.exceptNative
}
