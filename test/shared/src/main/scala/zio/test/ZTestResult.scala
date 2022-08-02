package zio.test

import zio._
import zio.test.Hash.ProjectHash

trait TestStats {
  def flaky[E](results: Seq[ZTestResult[E]]): Boolean
  def performanceDegraded[E](current:ZTestResult[E], previous: Seq[ZTestResult[E]]): Boolean
}

case class ZTestResult[E](
                        fullyQualifiedName: String,
                        labels: List[String],
                        // For grouping results. Cannot reliably be done with labels
                        ancestors: List[SuiteId],
                        test: Either[TestFailure[E], TestSuccess],
                        annotations: TestAnnotationMap,
                        time: Duration,
                        projectHash: ProjectHash,
                        // In addition to real-time streaming output, we could also capture the output
                        // from specific tests for later browsing
                        output: Chunk[String]
                      )

object Hash {
  import java.nio.file.{Files, Path}
  import java.security.{DigestInputStream, MessageDigest}
  import scala.util.Using

  def sha256(path: String): ProjectHash =
    sha256(Path.of(path))

  def sha256(path: Path): ProjectHash =
    sha256(Seq(path): _*)

  def sha256(roots: Path*): ProjectHash = {
    val md = MessageDigest.getInstance("SHA-256")
    val buffer = new Array[Byte](4096)
    roots.foreach { root =>
      Files.walk(root).filter(!_.toFile.isDirectory).forEach { path =>
        Using.resource(new DigestInputStream(Files.newInputStream(path), md)) { dis =>
          // fully consume the inputstream
          while (dis.available > 0) {
            dis.read(buffer)
          }
        }
      }
    }
    ProjectHash(md.digest.map(b => String.format("%02x", Byte.box(b))).mkString)
  }

  case class ProjectHash(raw: String)

  // usage:
  sha256("/path/to/dir1")
}