#!/bin/bash
# Copyright 2018 Google Inc.
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

echo "Compiling using Java:"
java -version
mvn -V -B -ntp clean compile

# We ensure the generated class files are compatible with Java 8
if [ ! -z "${JAVA8_HOME}" ]; then
  setJava "${JAVA8_HOME}"
fi

echo "Running tests using Java:"
java -version

if [ "${GITHUB_JOB}" == "units-java8" ]; then
  java -version 2>&1 | grep -q 'openjdk version "1.8.'
  MATCH=$? # 0 if the output has the match
  if [ "$MATCH" != "0" ]; then
    echo "Please specify JDK 8 for Java 8 tests"
    exit 1
  fi
fi

echo
mvn install -DskipTests
