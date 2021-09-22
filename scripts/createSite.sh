#!/bin/bash

set -e 

OLDDIR=`pwd`
# Clean existing build and mdoc output directory
rm -Rf target
rm -Rf website/docs
rm -Rf website/versioned_docs

# Checkout latest released version of 1.x 
git checkout origin/master
sbt docs/mdoc docs/unidoc

mkdir -p website/versioned_docs/version-1.x
mv zio-docs/target/mdoc/* website/versioned_docs/version-1.x

mkdir -p website/static/api/1.x
mv website/static/api website/static/api-1.x

# No we need to checkout the branch that originally has triggered the site build 
git checkout $1
sbt docs/unidoc docs/mdoc

cd website 
rm -Rf node_modules
yarn install 
yarn build 

cd $OLDDIR

