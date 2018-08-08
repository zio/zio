import scala.sys.process.Process

lazy val commitSha = Process("git rev-parse --short HEAD").lineStream.head

version in ThisBuild ~= { verzion =>
  s"$verzion-$commitSha"
}
