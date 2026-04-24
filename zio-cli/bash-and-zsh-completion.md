# Bash and Zsh Completion

> **ZIO CLI** supports a mechanism for performing tab completion of command line
options and arguments in bash and zsh. The approach that **ZIO CLI** uses to
communicate with the shell tooling for performing tab completion is heavily
inspired by the excellent Haskell
[optparse-applicative](https://github.com/pcapriotti/optparse-applicative#bash-zsh-and-fish-completions)
library. Every `CliApp` is extended with a few hidden built-in options for
providing tab completions to shell environments.

## Overview

**ZIO CLI** supports a mechanism for performing tab completion of command line
options and arguments in bash and zsh. The approach that **ZIO CLI** uses to
communicate with the shell tooling for performing tab completion is heavily
inspired by the excellent Haskell
[optparse-applicative](https://github.com/pcapriotti/optparse-applicative#bash-zsh-and-fish-completions)
library. Every `CliApp` is extended with a few hidden built-in options for
providing tab completions to shell environments.

In what follows, pretend that your CLI application (called `my-cli-app`) has been
installed into a stable location in your path (such as the `~/.local/bin`
directory favored by the **ZIO CLI** installer script).

## Generating a completion shell script
The `--shell-completion-script` and `--shell-type` built-in options produce a
shell script that enables tab completion. In the example below, we generate a
completion script (called `completion-script.sh`):
```bash
my-cli-app                                       \
    --shell-completion-script `which my-cli-app` \
    --shell-type bash > completion-script.sh
```
After generating the script, you can quickly enable tab completion via:
```bash
source completion-script.sh
```
Unfortunately, the tab completion will only be enabled within the current shell
session. Normally, the output of `--shell-completion-script` should be shipped
with the program and copied to the appropriate directory (e.g.,
`/etc/bash_completion.d/`) during program installation.

## How Bash and Zsh Completions are Generated
The shell completion scripts register an event handler that fires whenever
`my-cli-app` is the first term at the terminal prompt and the tab key is
pressed. This event handler sends information about the terminal contents and
cursor position back to `my-cli-app` using another built-in option called
`--shell-completion-index` and some special environment variables
(`COMP_WORD_0`, `COMP_WORD_1`, ...).

When `my-cli-app` receives these values, it runs a completion algorithm and
prints the completion terms to the console (one line per completion term). The
console output feeds back into the shell machinery, which renders the completion
results in the terminal.

For example, when the user types the following in the terminal
```
$ my-cli-app foo bar baz
```
and then moves the cursor over "foo" and hits the tab key, `my-cli-app` is called
as follows:
```bash
COMP_WORD_0=my-cli-app     \
COMP_WORD_1=foo            \
COMP_WORD_2=bar            \
COMP_WORD_3=baz            \
my-cli-app                 \
--shell-completion-index 1 \
--shell-type bash
```

The `COMP_WORD_` prefix of these environment variables is directly inspired by
the `COMP_WORD` array-valued Bash variable that is part of its
[programmable completion system](https://www.gnu.org/software/bash/manual/html_node/Programmable-Completion.html).
Unfortunately, array-valued variables cannot be used as environment variables,
so our approach instead uses one variable per term in the array.

## Further Reading
The [optparse-applicative documentation](https://github.com/pcapriotti/optparse-applicative#bash-zsh-and-fish-completions)
is an excellent resource that may help to clarify the implementation above.
