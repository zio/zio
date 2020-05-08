package zio

object AppExample extends App {
  override def run(args: List[String]): ZIO[Any, Nothing, Int] = {
    ZIO.succeed("xxx").tap(e => {
      zio.console.putStrLn(e)
    }).createValueForBusiness
  }
}
