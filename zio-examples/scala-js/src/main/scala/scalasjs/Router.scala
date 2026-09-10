package scalasjs

import org.scalajs.dom

object Router:

  def current: Filter = Filter.fromHash(dom.window.location.hash)

  def onChange(handler: Filter => Unit): Unit =
    dom.window.onhashchange = (_: dom.Event) => handler(current)
