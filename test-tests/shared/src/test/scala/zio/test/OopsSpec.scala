//package zio.test
//
////import zio.test.Assertion._
//
//object OopsSpec extends ZIOBaseSpec {
//
//  val failing = TestAspect.failing
//
//  def spec: Spec[Annotations, TestFailure[Any]] =
//    suite("OopsSpec")(
//      test("assertion") {
//        assertTrue(
//          10 == 12,
//          100 == 100,
//          "hello".length == 8
//        )
//      },
//      test("another") {
//        assertTrue(10 == 12)
//      }
//    )
//
//}
