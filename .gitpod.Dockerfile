FROM gitpod/workspace-full
USER gitpod
RUN brew install scala coursier/formulas/coursier sbt scalaenv ammonite-repl
RUN sudo env "PATH=$PATH" coursier bootstrap org.scalameta:scalafmt-cli_2.12:2.4.2 \
  -r sonatype:snapshots \
  -o /usr/local/bin/scalafmt --standalone --main org.scalafmt.cli.Cli
RUN bash -cl "set -eux \
    version=0.9.0 \
    coursier fetch \
        org.scalameta:metals_2.12:$version \
        org.scalameta:mtags_2.13.3:$version \
        org.scalameta:mtags_2.12.12:$version"