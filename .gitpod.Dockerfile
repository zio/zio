FROM gitpod/workspace-full

RUN curl -fLo cs https://git.io/coursier-cli-"$(uname | tr LD ld)" && \
    chmod +x cs && \
    ./cs install cs && \
    echo 'export PATH="$PATH:/home/gitpod/.local/share/coursier/bin"' >> ~/.zshrc && \
    echo 'export PATH="$PATH:/home/gitpod/.local/share/coursier/bin"' >> ~/.bashrc && \
    ./cs install scala sbt scalafix scalafmt ammonite && \
    ./cs install bloop --only-prebuilt=true && \
    rm cs

RUN sh -c "$(curl -fsSL https://raw.githubusercontent.com/ohmyzsh/ohmyzsh/master/tools/install.sh)" "" --unattended
