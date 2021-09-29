---
id: index
title: "Introduction"
---

ZIO already provided four build-in services, when we use these services we don't need to provide their corresponding environment explicitly. The `ZEnv` environment is a type alias for all of these services and will be provided by ZIO to our effects:

- **[Console](console.md)** — Operations for reading/writing strings from/to the standard input, output, and error console.
- **[Clock](clock.md)** — Contains some functionality related to time and scheduling. 
- **[Random](random.md)** — Provides utilities to generate random numbers.
- **[System](system.md)** — Contains several useful functions related to system environments and properties.
