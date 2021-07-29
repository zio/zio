package zio.test

import zio.Task

import java.nio.file.{Files, Paths, StandardOpenOption}

// FIXME I don't know it this even work
trait FileIOPlatformSpecific {

  def existsFile(path: String): Task[Boolean] =
    Task(Files.exists(Paths.get(path)))

  // FIXME should it rather be blocking?
  def readFile(path: String): Task[String] =
    Task(Files.readString(Paths.get(path)))

  def writeFile(path: String, content: String): Task[Unit] =
    Task(Files.writeString(Paths.get(path), content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
}
