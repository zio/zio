package zio.test

package object experimental {

  type TestAspectAtLeastR[R] =
    TestAspect.Aux[Nothing, Any, Nothing, Any, ({ type Apply[+R1] = R1 with R })#Apply, ({ type Apply[+E] = E })#Apply]

  type TestAspectPoly =
    TestAspect.Aux[Nothing, Any, Nothing, Any, ({ type Apply[+R] = R })#Apply, ({ type Apply[+E] = E })#Apply]
}
