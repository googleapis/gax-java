#!/bin/bash
# Copyright 2018 Google LLC
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

scriptDir=$(realpath $(dirname "${BASH_SOURCE[0]}"))
# cd to the parent directory, i.e. the root of the git repo
cd ${scriptDir}/..

echo $JOB_TYPE

function setJava() {
  export JAVA_HOME=$1
  export PATH=${JAVA_HOME}/bin:$PATH
}

# This project requires compiling the classes in JDK 11 or higher for GraalVM
# classes. Compiling this project with Java 8 or earlier would fail with "class
# file has wrong version 55.0, should be 53.0" and "unrecognized --release 8
# option" (set in build.gradle).
if [ ! -z "${JAVA11_HOME}" ]; then
  setJava "${JAVA11_HOME}"
fi

mvn -V -B -ntp clean install -DskipTests

# We ensure the generated class files are compatible with Java 8
if [ ! -z "${JAVA8_HOME}" ]; then
  setJava "${JAVA8_HOME}"
fi

RETURN_CODE=0

case "${JOB_TYPE}" in
test)
  # run tests in Java 8 with the source compiled in Java 11
  mvn -V -B -ntp surefire:test
  RETURN_CODE=$?
  ;;
clirr)
  mvn -B -ntp clirr:check
  RETURN_CODE=$?
  ;;
*) ;;
esac

echo "exiting with ${RETURN_CODE}"
exit ${RETURN_CODE}