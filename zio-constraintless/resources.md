# ZIO Constraintless Resources

> The paper behind ZIO Constraintless, the source code and where to ask questions.

This page collects the external material behind ZIO Constraintless.

## The Paper

ZIO Constraintless is a Scala take on the paper [Deconstraining](https://web.archive.org/web/20260115140505/http://www.doc.ic.ac.uk/~wlj05/files/Deconstraining.pdf), which describes the idea in Haskell. The paper's encoding and the problem with the Hughes schema are summarised in [Why Not the Hughes Schema?](./explanation/why-not-the-hughes-schema.md).

## Source Code and Community

The source code, including the `examples` project with a larger expression language, is in the [zio/zio-constraintless](https://github.com/zio/zio-constraintless) repository. A runnable program that uses [`TypeList`](./reference.md#typelist), [`IsElementOf`](./reference.md#iselementof) and [`Instances`](./reference.md#instances) is [Expr.scala](https://github.com/zio/zio-constraintless/blob/master/examples/shared/src/main/scala/zio/constraintless/examples/Expr.scala). Questions are welcome on the [ZIO Discord](https://discord.gg/2ccFBr4).
