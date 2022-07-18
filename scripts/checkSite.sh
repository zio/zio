#!/bin/bash

set -ex

ZIO_LATEST_2=`git describe --tags --abbrev=0 ` sbt docs/unidoc
ZIO_LATEST_2=`git describe --tags --abbrev=0 ` sbt docs/mdoc
cd website

mv docusaurus.config.js docusaurus.config.js.org 
cp version2-only-docusaurus.config.js docusaurus.config.js 

mv src/pages/index.js src/pages/index.js.org
cp src/pages/version2-only-index.js.txt src/pages/index.js

mv versions.json versions.json.org

rm -Rf node_modules 
rm -f package-lock.json

yarn install 
yarn build 

mv versions.json.org versions.json
mv docusaurus.config.js.org docusaurus.config.js
mv src/pages/index.js.org src/pages/index.js

cd ..
