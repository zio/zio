#!/bin/bash

# Clean existing build and mdoc output directory
rm -Rf target
rm -Rf website/docs

# Checkout latest released version of 1.x 
git checkout v1.0.11
sbt docs/mdoc

mkdir -p website/versioned_docs/version-1.x
mv zio-docs/target/mdoc/* website/versioned_docs/version-1.x

mkdir -p website/versioned_sidebars
cp website/sidebars.json website/versioned_sidebars/version-1.x-sidebars.json

git checkout series/2.x
sbt docs/mdoc

cd website 
yarn install 
yarn build 
