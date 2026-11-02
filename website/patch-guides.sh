#!/usr/bin/env bash
# Copies guides that live in their own sbt project into website/docs, so the
# site builds locally without running mdoc. `sbt docs/mdoc` writes the
# processed version of these files to the same place; an existing file is
# kept so this never overwrites mdoc output.
#
# The file is copied, not symlinked: Docusaurus resolves symlinks to their
# real path, finds no doc metadata for a file outside website/docs, and the
# page crashes with "Cannot read properties of undefined (reading 'id')".
set -euo pipefail
cd "$(dirname "$0")"

source=../documentation/guides/tutorials/make-a-zio-app-configurable/docs/make-a-zio-application-configurable.md
dest=docs/guides/tutorials/make-a-zio-application-configurable.md

# A symlink here was left by an earlier version of this script
if [ -L "$dest" ] || [ ! -e "$dest" ]; then
  mkdir -p "$(dirname "$dest")"
  rm -f "$dest"
  cp "$source" "$dest"
fi
