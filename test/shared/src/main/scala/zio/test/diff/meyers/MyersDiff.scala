package zio.test.diff.meyers

import zio.test.diff.Delta.TYPE.UNCHANGED
import zio.test.diff._

/**
 * A modified version of [[https://github.com/scalameta/munit/blob/master/munit/shared/src/main/scala/munit/internal/difflib/MyersDiff.scala]]
 */
private[diff] class MyersDiff[T](equality: Equality[T] = Equality.default[T]) {
  def diff(original: Vector[T], revised: Vector[T]): Patch[T] = {
    val patch = buildRevision(buildPath(original, revised), original, revised)
    fillInUnchanged(original, revised, patch)
  }
  private def fillInUnchanged(original: Vector[T], revised: Vector[T], patch: Patch[T]): Patch[T] = {
    def endOffsets(p: Patch[T]) =
      p.deltas.lastOption
        .fold(0 -> 0)(d => d.original.endPosition -> d.revised.endPosition)

    val result =
      patch.deltasSorted.foldLeft(Patch[T]()) {
        case (patch, delta) =>
          val (origEndOffset, revisedEndOffset) = endOffsets(patch)
          if (delta.original.position > origEndOffset || delta.revised.position > revisedEndOffset) {
            val unchanged = Delta(
              UNCHANGED,
              Chunk(origEndOffset, original.slice(origEndOffset, delta.original.position)),
              Chunk(revisedEndOffset, revised.slice(revisedEndOffset, delta.revised.position))
            )
            patch.addDelta(unchanged).addDelta(delta)
          } else {
            patch.addDelta(delta)
          }
      }
    val (origEndOffset, revisedEndOffset) = endOffsets(result)
    if (origEndOffset < original.length - 1 || revisedEndOffset < revised.length - 1) {
      result.addDelta(
        Delta(
          UNCHANGED,
          Chunk(origEndOffset, original.slice(origEndOffset, original.length)),
          Chunk(revisedEndOffset, revised.slice(revisedEndOffset, revised.length))
        )
      )

    } else {
      result
    }
  }

  private def buildRevision(
    _path: PathNode,
    orig: Vector[T],
    rev: Vector[T]
  ): Patch[T] = {
    var path  = _path
    var patch = new Patch[T]
    if (path.isSnake) path = path.prev
    while (path != null &&
           path.prev != null &&
           path.prev.j >= 0) {
      if (path.isSnake)
        throw new IllegalStateException(
          "bad diffpath: found snake when looking for diff"
        )
      val i = path.i
      val j = path.j
      path = path.prev
      val ianchor = path.i
      val janchor = path.j
      val original =
        new Chunk[T](
          ianchor,
          copyOfRange(orig, ianchor, i)
        )
      val revised =
        new Chunk[T](
          janchor,
          copyOfRange(rev, janchor, j)
        )
      val delta: Delta[T] =
        if (original.size == 0 && revised.size != 0) {
          InsertDelta[T](original, revised)
        } else if (original.size > 0 && revised.size == 0) {
          DeleteDelta[T](original, revised)
        } else {
          ChangeDelta[T](original, revised)
        }
      patch = patch.addDelta(delta)
      if (path.isSnake) {
        path = path.prev
      }
    }
    patch
  }

  private def copyOfRange(original: Vector[T], fromIndex: Int, to: Int) =
    original.slice(fromIndex, to)

  def buildPath(
    orig: Vector[T],
    rev: Vector[T]
  ): PathNode = {

    val N = orig.size
    val M = rev.size

    val MAX      = N + M + 1
    val size     = 1 + 2 * MAX
    val middle   = size / 2
    val diagonal = new Array[PathNode](size)

    diagonal(middle + 1) = new Snake(0, -1, null)
    var d = 0
    while (d < MAX) {
      var k = -d
      while (k <= d) {
        val kmiddle        = middle + k
        val kplus          = kmiddle + 1
        val kminus         = kmiddle - 1
        var prev: PathNode = null
        var i              = 0
        if ((k == -d) || (k != d && diagonal(kminus).i < diagonal(kplus).i)) {
          i = diagonal(kplus).i
          prev = diagonal(kplus)
        } else {
          i = diagonal(kminus).i + 1
          prev = diagonal(kminus)
        }
        diagonal(kminus) = null // no longer used

        var j              = i - k
        var node: PathNode = new DiffNode(i, j, prev)
        // orig and rev are zero-based
        // but the algorithm is one-based
        // that's why there's no +1 when indexing the sequences
        while (i < N &&
               j < M &&
               equality.equals(orig(i), rev(j))) {
          i += 1
          j += 1
        }
        if (i > node.i) {
          node = new Snake(i, j, node)
        }
        diagonal(kmiddle) = node
        if (i >= N && j >= M) {
          return diagonal(kmiddle)
        }

        k += 2
      }
      diagonal(middle + d - 1) = null
      d += 1
    }
    // According to Myers, this cannot happen
    throw new DifferentiationFailedException("could not find a diff path")
  }
}

class DifferentiationFailedException(message: String) extends Exception(message)
