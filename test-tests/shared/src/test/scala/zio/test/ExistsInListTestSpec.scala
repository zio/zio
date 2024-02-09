package zio.test

import zio.test.GenUtils.ExistsFaster

object ExistsInListTestSpec extends ZIOSpecDefault {

  case class ComplexObject(id: String)

  object ComplexObject {
    def of(i: Int): ComplexObject = ComplexObject(id = i.toString)
  }

  def spec = suite("assert exists/not-exists suite")(
    test("test assert exists in empty list fails") {
      val emptyList: List[ComplexObject] = List.empty
      assertTrue(assertTrue(emptyList.existsFast(_.id == "1")).isFailure)
    },
    test("test assert not exists in empty list succeed") {
      val emptyList: List[ComplexObject] = List.empty
      assertTrue(!emptyList.existsFast(_.id == "1"))
    },
    test("test assert exists succeed on single elem list") {
      val list: List[ComplexObject] = List(ComplexObject.of(1))
      assertTrue(list.existsFast(_.id == "1"))
    },
    test("test assert not exists succeed on single elem list") {
      val list: List[ComplexObject] = List(ComplexObject.of(1))
      assertTrue(!list.existsFast(_.id == "0"))
    },
    test("test assert exists fails on single elem list") {
      val list: List[ComplexObject] = List(ComplexObject.of(1))
      assertTrue(assertTrue(list.existsFast(_.id == "2")).isFailure)
    },
    test("test assert not exists fails on single elem list") {
      val list: List[ComplexObject] = List(ComplexObject.of(1))
      assertTrue(assertTrue(!list.existsFast(_.id == "1")).isFailure)
    },
    test("test assert exists succeed on multiple elems list") {
      val list: List[ComplexObject] = List(ComplexObject.of(1), ComplexObject.of(2), ComplexObject.of(3))
      assertTrue(list.existsFast(_.id == "3"))
    },
    test("test assert not exists succeed on multiple elems list") {
      val list: List[ComplexObject] = List(ComplexObject.of(1), ComplexObject.of(2), ComplexObject.of(3))
      assertTrue(!list.existsFast(_.id == "5"))
    },
    test("test assert exists fails on multiple elems list") {
      val list: List[ComplexObject] = List(ComplexObject.of(1), ComplexObject.of(2), ComplexObject.of(3))
      assertTrue(assertTrue(list.existsFast(_.id == "5")).isFailure)
    },
    test("test assert not exists fails on multiple elems list") {
      val list: List[ComplexObject] = List(ComplexObject.of(1), ComplexObject.of(2), ComplexObject.of(3))
      assertTrue(assertTrue(!list.existsFast(_.id == "3")).isFailure)
    },
    test("test assert exists succeed on large 100000 elems list") {
      val hugeList = (1 to 100000).map(i => ComplexObject.of(i)).toList
      assertTrue(hugeList.existsFast(_.id == "100000"))
    },
    test("test assert not exists succeed on large 100000 elems list") {
      val hugeList = (1 to 100000).map(i => ComplexObject.of(i)).toList
      assertTrue(!hugeList.existsFast(_.id == "0"))
    }
  ) @@ TestAspect.timed

}
