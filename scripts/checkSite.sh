#!/bin/bash

set -e 

ZIO_LATEST_2=`git describe --tags --abbrev=0 ` sbt docs/unidoc docs/mdoc
cd website
mv version2-only-docusaurus.config.js docusaurus.config.js 
mv versions.json versions.json.org
yarn install 
yarn build 
mv versions.json.org versions.json
cd ..
