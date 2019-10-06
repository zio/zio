package zio.test

import zio.DefaultRuntime

trait AsyncBaseSpec extends DefaultRuntime {
  def run: List[Async[(Boolean, String)]]
}
