#!/bin/bash
# Copyright 2022 Google LLC
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

CLIENT_LIBRARY=$1

# Example JOB_TYPE: "test" or "graalvm"
export JOB_TYPE=$2
## Get the directory of the build script
scriptDir="$(realpath "$(dirname "${BASH_SOURCE[0]}")")"
## cd to the parent directory, i.e. the root of the git repo
cd "${scriptDir}"/..

# Round 1
# Publish gax to local maven to make it available for downstream libraries
./gradlew publishToMavenLocal

# Read current gax version
GAX_VERSION=$( ./gradlew -q :gax:properties | grep '^version: ' | cut -d' ' -f2 )

# Round 2
# Run this gax-java against HEAD of java-shared dependencies

git clone "https://github.com/googleapis/java-shared-dependencies.git" --depth=1
pushd java-shared-dependencies/first-party-dependencies

# replace version
xmllint --shell pom.xml << EOF
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

cd ..
mvn verify install -B -V -ntp -fae \
-DskipTests=true \
-Dmaven.javadoc.skip=true \
-Dgcloud.download.skip=true \
-Denforcer.skip=true

# Namespace (xmlns) prevents xmllint from specifying tag names in XPath
SHARED_DEPS_VERSION=$( sed -e 's/xmlns=".*"//' pom.xml | xmllint --xpath '/project/version/text()' - )

if [ -z "${SHARED_DEPS_VERSION}" ]; then
  echo "Version is not found in pom.xml"
  exit 1
fi

# Round 3
# Run this shared-dependencies BOM against java client libraries
git clone "https://github.com/googleapis/java-${CLIENT_LIBRARY}.git" --depth=1
pushd java-"${CLIENT_LIBRARY}"

if [[ $CLIENT_LIBRARY == "bigtable" ]]; then
  pushd google-cloud-bigtable-deps-bom
fi

# replace version
xmllint --shell pom.xml << EOF
setns x=http://maven.apache.org/POM/4.0.0
cd .//x:artifactId[text()="google-cloud-shared-dependencies"]
cd ../x:version
set ${SHARED_DEPS_VERSION}
save pom.xml
EOF

if [[ $CLIENT_LIBRARY == "bigtable" ]]; then
  popd
fi

echo "Modification on the shared dependencies BOM:"
git diff
echo

# This reads the JOB_TYPE environmental variable ("test" or "graalvm")
.kokoro/build.sh
