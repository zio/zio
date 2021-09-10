#!/bin/bash

# Clean existing build and mdoc output directory
rm -Rf target
rm -Rf website/docs
rm -Rf website/versioned_docs

# Checkout latest released version of 1.x 
git checkout v1.0.11
sbt docs/mdoc

mkdir -p website/versioned_docs/version-1.x
mv zio-docs/target/mdoc/* website/versioned_docs/version-1.x

git checkout documentation
sbt docs/mdoc

cd website 
rm -Rf node_modules
yarn install 
yarn build 
