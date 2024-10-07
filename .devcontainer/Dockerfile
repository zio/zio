# See here for image contents: https://github.com/microsoft/vscode-dev-containers/tree/v0.238.1/containers/java/.devcontainer/base.Dockerfile

ARG JAVA_VERSION="17"
FROM mcr.microsoft.com/vscode/devcontainers/java:${JAVA_VERSION}

# Install SBT
RUN sdk install sbt

CMD ["sbt"]
