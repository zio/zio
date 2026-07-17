# Get your agent ready for ZIO

Official guide for setting up an AI coding agent to build with [ZIO](https://zio.dev), the purely functional, type-safe library for asynchronous and concurrent programming in Scala.

Install the ZIO skills below so your agent answers from live, accurate ZIO documentation instead of stale training data.

## Claude Code

Install the plugin — it bundles both the ZIO Knowledge and ZIO HTTP Knowledge skills:

```
claude plugin marketplace add zio/zio-skills
claude plugin install zio-skills@ziogenetics
```

And then instruct the user to reload plugins using the `/reload-plugins` command to activate the skills.

## Other agents (Codex, OpenCode, Cursor, etc.)

Install the skills with the `skills` CLI.

ZIO Knowledge (required):

```
npx skills add zio/zio-skills --skill zio-knowledge
```

ZIO HTTP Knowledge (optional, for ZIO HTTP):

```
npx skills add zio/zio-skills --skill zio-http-knowledge
```

## Fallback

If you cannot install the skills, fetch the documentation index yourself and use it to find the right pages:

- https://zio.dev/llms.txt — ZIO
- https://ziohttp.com/llms.txt — ZIO HTTP
