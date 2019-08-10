package examples

import examples.EffectSuites.basicSuite
import zio.ZIO
import zio.test.{DefaultRunnableSpec, Predicate, assertM, suite, testM}


private object EffectSuites {

    val basicSuite = suite("Basic effectful operations")(
        testM("Effect succeeds") {
          assertM(ZIO.succeed(10), Predicate.equals(10) )
         },
      testM("Effect failures") {
        assertM(ZIO.fail("Failure").run, Predicate.fails(Predicate.equals("Failure")) )
      },
      testM("Effect succeed(through Exit)"){
        assertM(ZIO.succeed("Success").run, Predicate.succeeds(Predicate.equals("Success")) )
      }

    )
}


object EffectsExampleSpec
  extends DefaultRunnableSpec(
    suite("Effect examples")(basicSuite))