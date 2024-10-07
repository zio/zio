# See here for image contents: https://github.com/microsoft/vscode-dev-containers/tree/v0.238.1/containers/java/.devcontainer/base.Dockerfile

ARG JAVA_VERSION="17"
FROM mcr.microsoft.com/vscode/devcontainers/java:${JAVA_VERSION}

# Install Coursier, SBT and Scala
RUN curl -fL "https://github.com/coursier/launchers/raw/master/cs-x86_64-pc-linux.gz" | gzip -d > cs
RUN cs setup

CMD ["sbt"]
