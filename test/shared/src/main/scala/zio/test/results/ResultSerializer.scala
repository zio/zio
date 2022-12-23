package zio.test.results
import zio.test.ExecutionEvent
import zio.test.results.Json.jsonify

trait ResultSerializer {
  def render(executionEvent: ExecutionEvent): String
}
