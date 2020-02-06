package zio.test

final case class TestArgs(testSearchTerms: List[String], tagSearchTerms: List[String])

object TestArgs {
  def empty: TestArgs = TestArgs(List.empty[String], List.empty[String])

  def parse(args: Array[String]): TestArgs = {
    // TODO: Add a proper command-line parser
    val parsedArgs = args
      .sliding(2, 2)
      .collect {
        case Array("-t", term)    => ("testSearchTerm", term)
        case Array("-tags", term) => ("tagSearchTerm", term)
      }
      .toList
      .groupBy(_._1)
      .map {
        case (k, v) =>
          (k, v.map(_._2))
      }

    TestArgs(parsedArgs.getOrElse("testSearchTerm", Nil), parsedArgs.getOrElse("tagSearchTerm", Nil))
  }
}
