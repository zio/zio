package examples


import zio.test.{test, _}

object PredicatesExampleSpecRunner extends DefaultRunnableSpec(PredicateExampleSpec.spec1)


object PredicateExampleSpec {

  val spec1: ZSpec[Any, Nothing, String] =  test("(1 + 1) must be 2") {
    assert[Int](1 + 1, Predicate.equals(2))
  }

  val spec2: Spec[String, ZTest[Any, Nothing]] = suite("That")(spec1)

  val spec: Spec[String, ZTest[Any, Nothing]] = suite("Operations")(
      test("(1 + 1) must be 2") {
        assert[Int](1 + 1, Predicate.equals(2))
      },
      test("(10 - 5) must be 5") {
        assert[Int](10 - 5, Predicate.equals(5))
      },
      test("(2 * 5) must be 10") {
        assert[Int](1 + 1, Predicate.equals(10))
      },
      test("(25 / 5) must be 5") {
        assert[Int](25 / 5, Predicate.equals(5))
      })


}


object PredicateExampleSpec2  extends DefaultRunnableSpec({

   suite("Operations")(
     test("(1 + 1) must be 2") {
       assert[Int](1 + 1, Predicate.equals(2))
     }
      ,
     test("(10 - 5) must be 5") {
       assert[Int](10 - 5, Predicate.equals(5))
     },
     test("(2 * 5) must be 10") {
       assert[Int](1 + 1, Predicate.equals(10))
     },
     test("(25 / 5) must be 5") {
       assert[Int](25 / 5, Predicate.equals(5))
     }
   )

})





