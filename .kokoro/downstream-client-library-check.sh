#!/bin/bash
# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -eo pipefail
# Display commands being run.
set -x

REPO=$1
## Get the directory of the build script
scriptDir=$(realpath $(dirname "${BASH_SOURCE[0]}"))
## cd to the parent directory, i.e. the root of the git repo
cd ${scriptDir}/..

# Publish gax to local maven to make it available for downstream libraries
gradle publishToMavenLocal

# Round 1: Run gax against shared-dependencies
pushd gax
GAX_VERSION=`gradle -q pVG`
echo $GAX_VERSION
popd

 Round 1
# Check this gax-java against HEAD of java-shared dependencies

git clone "https://github.com/googleapis/java-shared-dependencies.git" --depth=1
pushd java-shared-dependencies/first-party-dependencies

# replace version
xmllint --shell <(cat pom.xml) << EOF
setns x=http://maven.apache.org/POM/4.0.0
cd .//x:artifactId[text()="gax-bom"]
cd ../x:version
set ${GAX_VERSION}
cd ../..
cd .//x:artifactId[text()="gax-grpc"]
cd ../x:version
set ${GAX_VERSION}
save pom.xml
EOF

# run dependencies script
cd ..
mvn -Denforcer.skip=true clean install

SHARED_DEPS_VERSION_POM=pom.xml
# Namespace (xmlns) prevents xmllint from specifying tag names in XPath
SHARED_DEPS_VERSION=`sed -e 's/xmlns=".*"//' ${SHARED_DEPS_VERSION_POM} | xmllint --xpath '/project/version/text()' -`

if [ -z "${SHARED_DEPS_VERSION}" ]; then
  echo "Version is not found in ${SHARED_DEPS_VERSION_POM}"
  exit 1
fi

# Round 2

# Check this BOM against a few java client libraries
git clone "https://github.com/googleapis/java-${REPO}.git" --depth=1
pushd java-${REPO}

if [[ $REPO == "bigtable" ]]; then
  pushd google-cloud-bigtable-deps-bom
fi

# replace version
xmllint --shell <(cat pom.xml) << EOF
setns x=http://maven.apache.org/POM/4.0.0
cd .//x:artifactId[text()="google-cloud-shared-dependencies"]
cd ../x:version
set ${SHARED_DEPS_VERSION}
save pom.xml
EOF

if [[ $REPO == "bigtable" ]]; then
  popd
fi

mvn -Denforcer.skip=true clean install
