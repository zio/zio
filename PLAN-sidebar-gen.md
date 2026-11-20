# Plan: ecosystem sidebar generator handles flat sidebars (ZIO-197)

Problem: `docusaurus generate-sidebar` crashes on zio-sbt 0.8.11 (flat sidebar starting with a `doc` item)
and silently drops every project whose flat sidebar starts with "index" unless hard-listed.

Steps
1. Replace the old-style list + "labeled category" filter with one `projectCategory(project, sidebar)`.
   - single labeled category -> used as is (generic labels like "Getting Started" replaced by project name)
   - flat array -> wrapped in a category (label = project display name, link = index doc, index item removed)
2. `mapConfig` maps `doc` items (prefix id) and passes `link` items through; never returns undefined.
3. Display name: small override map for acronyms, else title-case of the package name.
4. Edit index.ts, recompile index.js (tsc 4.7 --target es2020 reproduces the committed output).
5. Verify with current pins and with the #11483 pins (revert afterwards); never commit generated output.
