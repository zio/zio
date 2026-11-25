# How the Plugins Fit Together

> ZIO SBT splits the build of a ZIO library into three sbt plugins that each own one concern, and two modules that support them.

## One Plugin per Concern

The ecosystem plugin owns how the code is compiled and checked. It decides the Scala versions, the compiler options and the `fmt`, `lint` and `prepare` commands, so every ZIO library builds the same way.

The website plugin owns the documentation. It compiles the Markdown in `docs/` with mdoc, so every Scala example is type-checked, and wraps the result in a Docusaurus website. It also generates the `README.md` from `docs/index.md`, so the README and the first page of the website never disagree.

The CI plugin owns the GitHub workflows. It describes the whole pipeline in Scala, using the model of `zio-sbt-githubactions`, and writes the YAML files that GitHub runs. The workflow calls back into the other plugins: the `lint` job runs the `lint` command of the ecosystem plugin, and the `build` job runs the website build.

## How Documentation Reaches zio.dev

A documentation change travels through four stages:

1. `compileDocs` runs mdoc over `docs/` and writes the compiled pages to `website/docs`.
2. On a release, the `release-docs` job publishes `website/docs` to npm as the package `@zio.dev/<project>`.
3. The `notify-docs-release` job tells the `zio/zio` repository that a new version of the package exists.
4. The ZIO website, built from `zio/zio`, picks up the new package version and shows the pages under `zio.dev/<project>/`.

The first stage runs on a laptop, which is why `previewWebsite` can show the same pages locally. The other three run in CI.

## Source Embedding

`zio-sbt-source` is not a plugin. It is a library on the classpath of the docs project, so mdoc can load its `mdoc:embed` modifier. Embedding examples from real source files keeps the documentation compiling against the code it describes.
