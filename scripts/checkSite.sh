#!/bin/bash

set -e 

rm -Rf website/build website/docs
ZIO_LATEST_2=`git describe --tags --abbrev=0 ` sbt docs/unidoc docs/mdoc
cd website
mv docusaurus.config.js docusaurus.config.js.org 
cp version2-only-docusaurus.config.js docusaurus.config.js 
mv versions.json versions.json.org
yarn install 
yarn build 
mv versions.json.org versions.json
mv docusaurus.config.js.org docusaurus.config.js
cd ..
