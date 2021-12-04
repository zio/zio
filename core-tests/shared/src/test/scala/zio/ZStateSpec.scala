package zio

import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object ZStateSpec extends ZIOSpecDefault {

  def spec =
    suite("StateSpec")(
      test("state can be updated") {
        final case class MyState(counter: Int)
        val zio = for {
          _     <- ZIO.updateState[MyState](state => state.copy(counter = state.counter + 1))
          count <- ZIO.getStateWith[MyState](_.counter)
        } yield count
        assertM(zio.provide(ZState.make(MyState(0)).toLayer))(equalTo(1))
      }
    ) @@ exceptScala3
}
