package zio.test.junit

import org.apache.maven.cli.MavenCli
import zio.blocking.{Blocking, effectBlocking}
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, ZSpec, _}
import zio.{RIO, ZIO}

import java.io.File
import scala.collection.immutable
import scala.xml.XML

/**
 * when running from IDE run `sbt publishM2`, copy the snapshot version the artifacts were published under (something like: `1.0.2+0-37ee0765+20201006-1859-SNAPSHOT`)
 * and put this into `VM Parameters`: `-Dproject.dir=$PROJECT_DIR$/test-junit-tests/jvm -Dproject.version=$snapshotVersion`
 */
object MavenJunitSpec extends DefaultRunnableSpec {

  def spec: ZSpec[Environment, Failure] = suite("MavenJunitSpec")(
    testM("FailingSpec results are properly reported") {
      for {
        mvn       <- makeMaven
        mvnResult <- mvn.clean() *> mvn.test()
        report    <- mvn.parseSurefireReport("zio.test.junit.maven.FailingSpec")
      } yield {
        assert(mvnResult)(not(equalTo(0))) &&
        assert(report)(
          containsFailure(
            "should fail",
            s"""zio.test.junit.TestFailed:
               |11 did not satisfy equalTo(12)
               |at ${mvn.mvnRoot}/src/test/scala/zio/test/junit/maven/FailingSpec.scala:10""".stripMargin
          ) &&
            containsFailure(
              "should fail - isSome",
              s"""zio.test.junit.TestFailed:
                 |11 did not satisfy equalTo(12)
                 |Some(11) did not satisfy isSome(equalTo(12))
                 |at ${mvn.mvnRoot}/src/test/scala/zio/test/junit/maven/FailingSpec.scala:13""".stripMargin
            ) &&
            containsSuccess("should succeed")
        )
      }
    }
  ) @@ TestAspect.sequential

  def makeMaven: ZIO[Any, AssertionError, MavenDriver] = for {
    projectDir <-
      ZIO
        .fromOption(sys.props.get("project.dir"))
        .orElseFail(
          new AssertionError(
            "Missing project.dir system property\n" +
              "when running from IDE put this into `VM Parameters`: `-Dproject.dir=$PROJECT_DIR$/test-junit-tests/jvm`"
          )
        )
    projectVer <-
      ZIO
        .fromOption(sys.props.get("project.version"))
        .orElseFail(
          new AssertionError(
            "Missing project.version system property\n" +
              "when running from IDE put this into `VM Parameters`: `-Dproject.version=<current zio version>`"
          )
        )
  } yield new MavenDriver(projectDir, projectVer)

  class MavenDriver(projectDir: String, projectVersion: String) {
    val mvnRoot: String = new File(s"$projectDir/../maven").getCanonicalPath
    private val cli     = new MavenCli
    System.setProperty("maven.multiModuleProjectDirectory", mvnRoot)

    def clean(): RIO[Blocking, Int] = run("clean")
    def test(): RIO[Blocking, Int]  = run("test", s"-Dzio.version=${projectVersion}", s"-ssettings.xml")
    def run(command: String*): RIO[Blocking, Int] = effectBlocking(
      cli.doMain(command.toArray, mvnRoot, System.out, System.err)
    )

    def parseSurefireReport(testFQN: String): ZIO[Blocking, Throwable, immutable.Seq[TestCase]] =
      effectBlocking(
        XML.load(scala.xml.Source.fromFile(new File(s"$mvnRoot/target/surefire-reports/TEST-$testFQN.xml")))
      ).map { report =>
        (report \ "testcase").map { tcNode =>
          TestCase(
            tcNode \@ "name",
            (tcNode \ "error").headOption.map(error =>
              TestError(error.text.linesIterator.map(_.trim).mkString("\n"), error \@ "type")
            )
          )
        }
      }

  }

  def containsSuccess(label: String): Assertion[Iterable[TestCase]]                = containsResult(label, error = None)
  def containsFailure(label: String, error: String): Assertion[Iterable[TestCase]] = containsResult(label, Some(error))
  def containsResult(label: String, error: Option[String]): Assertion[Iterable[TestCase]] =
    contains(TestCase(label, error.map(TestError(_, "zio.test.junit.TestFailed"))))

  case class TestCase(name: String, error: Option[TestError])
  case class TestError(message: String, `type`: String)
}
