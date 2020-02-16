package zio.test

final case class TestArgs(testSearchTerms: List[String], tagSearchTerms: List[String], testTaskPolicy: Option[String])

object TestArgs {
  def empty: TestArgs = TestArgs(List.empty[String], List.empty[String], None)

  def parse(args: Array[String]): TestArgs = {
    // TODO: Add a proper command-line parser
    val parsedArgs = args
      .sliding(2, 2)
      .collect {
        case Array("-t", term)      => ("testSearchTerm", term)
        case Array("-tags", term)   => ("tagSearchTerm", term)
        case Array("-policy", name) => ("policy", name)
      }
      .toList
      .groupBy(_._1)
      .map {
        case (k, v) =>
          (k, v.map(_._2))
      }

    val terms          = parsedArgs.getOrElse("testSearchTerm", Nil)
    val tags           = parsedArgs.getOrElse("tagSearchTerm", Nil)
    val testTaskPolicy = parsedArgs.getOrElse("policy", Nil).headOption
    TestArgs(terms, tags, testTaskPolicy)
  }
}
