package zio.test

import zio.{Chunk, ZIO}

/**
 * Syntax for writing test like
 * {{{
 * object MySpec extends MutableRunnableSpec {
 *   suite("foo") {
 *     testM("name") {
 *     } @@ ignore
 *
 *     test("name 2")
 *   }
 *   suite("another suite") {
 *     test("name 3")
 *   }
 * }
 * }}}
 */
trait MutableRunnableSpec extends DefaultRunnableSpec { self =>

  type ZS = ZSpec[Environment, Any]

  sealed trait SpecBuilder {
    def toSpec: ZS
    def label: String
  }

  case class SuiteBuilder(label: String) extends SpecBuilder {

    var nested: Chunk[SpecBuilder] = Chunk.empty

//    /**
//     * Syntax for adding aspects.
//     * {{{
//     * test("foo") { assert(42, equalTo(42)) } @@ ignore
//     * }}}
//     */
//    final def @@(
//      aspect: TestAspect[Environment, Environment, Failure, Failure]
//    ): SpecBuilder = {
//      spec = spec @@ aspect
//      this
//    }

    def toSpec: ZS =
      zio.test.suite(label)(
        nested.map(_.toSpec): _*
      )
  }

  case class TestBuilder(label: String, var toSpec: ZS) extends SpecBuilder {
    /**
     * Syntax for adding aspects.
     * {{{
     * test("foo") { assert(42, equalTo(42)) } @@ ignore
     * }}}
     */
    final def @@(
      aspect: TestAspect[Environment, Environment, Failure, Failure]
    ): TestBuilder = {
      toSpec = toSpec @@ aspect
      this
    }
  }

  // init SpecBuilder for this test class
  var stack: List[SuiteBuilder] = SuiteBuilder(self.getClass.getSimpleName) :: Nil
  var stackIsTest = false

  /**
   * Builds a suite containing a number of other specs.
   */
  def suite(label: String)(specs: => SpecBuilder): SuiteBuilder = {
    if(stackIsTest)
      throw new RuntimeException(s"Can not add suite $label to test ${stack.head.label}")
    val _oldStack = stack
    val builder = SuiteBuilder(label)
    stack.head.nested = stack.head.nested :+ builder
    stack = builder :: stack
    specs
    stack = _oldStack
    builder
  }

  /**
   * Builds a spec with a single pure test.
   */
  def test(label: String)(assertion: => TestResult): TestBuilder = {
    if(stackIsTest)
      throw new RuntimeException(s"Can not add test $label to test ${stack.head.label}")
    stackIsTest = true
    val test = zio.test.test(label)(assertion)
    val builder = TestBuilder(label, test)
    stack.head.nested = stack.head.nested :+ builder
    stackIsTest = false
    builder
  }

  /**
   * Builds a spec with a single effectful test.
   */
  def testM(label: String)(assertion: => ZIO[Environment, Failure, TestResult]): TestBuilder = {
    if(stackIsTest)
      throw new RuntimeException(s"Can not add test $label to test ${stack.head.label}")
    stackIsTest = true
    val test = zio.test.testM(label)(assertion)
    val builder = TestBuilder(label, test)
    stack.head.nested = stack.head.nested :+ builder
    stackIsTest = false
    builder
  }

  override def spec: ZSpec[Environment, Failure] =
    stack.head.toSpec
}
