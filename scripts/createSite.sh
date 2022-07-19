set -ex

OLDDIR=`pwd`
export SBT_OPTS="-Xmx2048m -XX:+UseG1GC"

# Clean existing build and mdoc output directory
rm -Rf target
rm -Rf website/docs
rm -Rf website/versioned_docs

# Checkout latest version of the website from the 1.x series

original_dir=$(pwd)
cd /tmp
git clone https://github.com/zio/zio.git
cd zio
git checkout series/1.x
sbt docs/mdoc
sbt docs/unidoc

mkdir -p "$original_dir"/website/versioned_docs/version-1.x
mv zio-docs/target/mdoc/* "$original_dir"/website/versioned_docs/version-1.x

mkdir -p "$original_dir"/website/static/api/1.x
mv website/static/api "$original_dir"/website/static/api-1.x

# Now we need to checkout the branch that originally has triggered the site build
cd "$original_dir"
git fetch --tags
ZIO_LATEST_2=`git describe --tags --abbrev=0 ` sbt docs/unidoc
ZIO_LATEST_2=`git describe --tags --abbrev=0 ` sbt docs/mdoc

cd website 
rm -Rf node_modules
yarn install 
yarn build 

cd $OLDDIR
