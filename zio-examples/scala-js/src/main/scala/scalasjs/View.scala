package scalasjs

import org.scalajs.dom
import org.scalajs.dom.html

object View:

  def renderItemCount(activeCount: Int): String =
    val noun = if activeCount == 1 then "item" else "items"
    s"$activeCount $noun left"

  def renderTodoItem(
      todo: Todo,
      onToggle: String => Unit,
      onDestroy: String => Unit,
      onCommitEdit: (String, String) => Unit
  ): html.LI =
    val li = dom.document.createElement("li").asInstanceOf[html.LI]
    li.className = if todo.completed then "completed" else ""

    val view = dom.document.createElement("div").asInstanceOf[html.Div]
    view.className = "view"

    val toggle = dom.document.createElement("input").asInstanceOf[html.Input]
    toggle.`type` = "checkbox"
    toggle.className = "toggle"
    toggle.checked = todo.completed
    toggle.onclick = (_: dom.Event) => onToggle(todo.id)

    val label = dom.document.createElement("label").asInstanceOf[html.Label]
    label.textContent = todo.title

    val destroy = dom.document.createElement("button").asInstanceOf[html.Button]
    destroy.className = "destroy"
    destroy.onclick = (_: dom.Event) => onDestroy(todo.id)

    view.appendChild(toggle)
    view.appendChild(label)
    view.appendChild(destroy)
    li.appendChild(view)

    val edit = dom.document.createElement("input").asInstanceOf[html.Input]
    edit.className = "edit"
    edit.value = todo.title
    li.appendChild(edit)

    def enterEditMode(): Unit =
      li.classList.add("editing")
      edit.value = todo.title
      edit.focus()

    def exitEditMode(): Unit =
      li.classList.remove("editing")

    def commit(): Unit =
      exitEditMode()
      onCommitEdit(todo.id, edit.value)

    label.ondblclick = (_: dom.Event) => enterEditMode()

    edit.onkeydown = (e: dom.KeyboardEvent) =>
      if e.key == "Enter" then commit()
      else if e.key == "Escape" then
        edit.value = todo.title
        exitEditMode()

    edit.onblur = (_: dom.Event) => if li.classList.contains("editing") then commit()

    li
