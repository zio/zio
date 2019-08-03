package examples


import examples.Suites._
import zio.test.{test, _}




private object Suites {

  val operationsSuite = suite("Basic Operations")(
    test("Addition operator") {
      assert(1 + 1, Predicate.equals(2))
    }
    ,
    test("Subtraction operator") {
      assert(10 - 5, Predicate.equals(5))
    },
    test("Multiplication operator") {
      assert(10 * 2, Predicate.equals(20))
    },
    test("Division operator") {
      assert(25 / 5, Predicate.equals(5))
    }
  )


  val listSuite = suite("List operations")(
    test("Iterable contains") {
      assert(List(1,2,3), Predicate.contains(1))
    },
    test("Iterable exists"){
      assert(List('z', 'i', 'o'), Predicate.exists(Predicate.equals('o')) )
    },
    test("Iterable forall") {
      assert(List("zio", "zmanaged", "zstream", "ztrace", "zschedule").map(_.nonEmpty), Predicate.forall(Predicate.equals(true)))
    },
    test("Iterable hasSize"){
      assert(List(1,2,3,4,5), Predicate.hasSize(Predicate.equals(5)))
    }
  )


}



object PredicateExampleSpec  extends DefaultRunnableSpec (

  suite("Predicate examples")(operationsSuite, listSuite)



)





