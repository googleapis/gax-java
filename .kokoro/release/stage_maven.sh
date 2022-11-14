#!/bin/bash
# Copyright 2022 Google Inc.
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

if [[ -n "${AUTORELEASE_PR}" ]]
then
  # Start the releasetool reporter
  requirementsFile=$(realpath $(dirname "${BASH_SOURCE[0]}")/../requirements.txt)
  python3 -m pip install --require-hashes -r $requirementsFile
  python3 -m releasetool publish-reporter-script > /tmp/publisher-script; source /tmp/publisher-script
fi

source $(dirname "$0")/common.sh
MAVEN_SETTINGS_FILE=$(realpath $(dirname "$0")/../../)/settings.xml
pushd $(dirname "$0")/../../

setup_environment_secrets
create_settings_xml_file "settings.xml"

mvn clean deploy -B \
  -DskipTests=true \
  -Dclirr.skip=true \
  --settings ${MAVEN_SETTINGS_FILE} \
  -Dgpg.executable=gpg \
  -Dgpg.passphrase=${GPG_PASSPHRASE} \
  -Dgpg.homedir=${GPG_HOMEDIR} \
  -P release

## The job triggered by Release Please (release-trigger) has this AUTORELEASE_PR
## environment variable. Fusion also lets us to specify this variable.
#if [[ -n "${AUTORELEASE_PR}" ]]
#then
#  mvn nexus-staging:release -B \
#    -DperformRelease=true \
#    --settings=${MAVEN_SETTINGS_FILE}
#else
#  echo "AUTORELEASE_PR is not set. Not releasing."
#fi
