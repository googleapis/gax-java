#!/bin/bash
# Copyright 2019 Google Inc.
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

echo "Running `stage.sh`. Invoking stage_gradle and stage_maven"

#set -eo pipefail
#
#if [[ -n "${AUTORELEASE_PR}" ]]
#then
#  # Start the releasetool reporter
#  requirementsFile=$(realpath $(dirname "${BASH_SOURCE[0]}")/../requirements.txt)
#  python3 -m pip install --require-hashes -r $requirementsFile
#  python3 -m releasetool publish-reporter-script > /tmp/publisher-script; source /tmp/publisher-script
#fi
#
#source $(dirname "$0")/common.sh
#MAVEN_SETTINGS_FILE=$(realpath $(dirname "$0")/../../)/settings.xml
#pushd $(dirname "$0")/../../
#
#setup_environment_secrets
#mkdir -p ${HOME}/.gradle
#create_gradle_properties_file "${HOME}/.gradle/gradle.properties"
#
#if [[ -z "${AUTORELEASE_PR}" ]]
#then
#  ./gradlew publishToSonatype
#else
#  ./gradlew publishToSonatype closeAndReleaseSonatypeStagingRepository
#fi
