One-time setup
==============

Set up Sonatype Account
-----------------------
* Sign up for a Sonatype JIRA account [here](https://issues.sonatype.org)
* Click *Sign Up* in the login box, follow instructions

Get access to repository
------------------------
* Go to [community support](https://issues.sonatype.org/browse/OSSRH)
* Ask for publish rights by creating an issue similar to [this one](https://issues.sonatype.org/browse/OSSRH-32031)
  * You must be logged in to create a new issue
  * Use the *Create* button at the top tab

* Generate the key `gpg --gen-key`
  * Keep the defaults, but specify a passphrase

* Determine your `gpg` version: `gpg --version`

* Find the ID of your public key
  * If you're using GPG version 1.y.z, `gpg --list-secret-keys`
    * Look for the line with format `sec   2048R/ABCDEFGH 2015-11-17`
    * The `ABCDEFGH` is the ID for your public key
  * If you're using GPG version 2.y.z `gpg --list-secret-keys --keyid-format LONG`
    * Look for line with format `sec   rsa2048/ABCDEFGHIJKLMNOP`
    * The `ABCDEFGHIJKLMNOP` is the ID. It is 16-byte long, but Gradle
      only support 8-byte keys. Use the *last* 8 bytes of the key when
      following the rest of this document.
    * `gpg --export-secret-keys > $HOME/.gnupg/secring.gpg`

* Upload your public key to a public server: `gpg --send-keys --keyserver hkp://pgp.mit.edu <YOUR-KEY-ID-HERE>`

Add deploy credential settings
------------------------
* Create a settings file at `$HOME/.gradle/gradle.properties` with your key information and your sonatype username/password

```
signing.keyId=<YOUR-KEY-ID-HERE>
signing.password=<YOUR-PASSWORD-HERE>
signing.secretKeyRingFile=/usr/local/google/home/<YOUR-USER-NAME>/.gnupg/secring.gpg

ossrhUsername=<YOUR-NEXUS-USERNAME>
ossrhPassword=<YOUR-NEXUS-PASSWORD>
```

Install releasetool
-------------------
See [releasetool](https://github.com/googleapis/releasetool) for installation instructions. You will
need python 3.6+ to run this tool.

To prepare a release
====================

Update version and deploy to Sonatype
-------------------------------------
1. Run `releasetool start`. Select "minor" or "patch" for the release type. This will bump the
   artifact versions, ask you to edit release notes, and create the release pull request.
  * Note: be sure to make these notes nice as they will be used for the release notes as well.
2. Request a review on the PR.
2. Run `./gradlew stageRelease` to:
  * Update `README.md` and `samples/pom.xml`
  * Regenerate `gh-pages` branch containing Javadocs
  * Stage artifacts on Sonatype: to the staging repository if "-SNAPSHOT" is *not* included in the version; otherwise to the snapshot repository only
3. Submit the PR.

Publish the release
-------------------
1. Run `./gradlew finalizeRelease`
  * Note: this will release **ALL** versions that have been staged to Sonatype:
    if you have staged versions you do not intend to release, remove these first
    from the [Nexus Repository Manager](https://oss.sonatype.org/) by logging in
    (upper right) and browsing staging repositories (left panel)
2. It will take some time (~10 min to ~8 hours) for the package to transition
3. Publish a new release on Github using releasetool.
  * Run `releasetool tag`. It will list the last few merged PRs. Select the newly merged release PR.
  * Releasetool will create the GitHub release with notes extracted from the pull request and tag
    the new release.

Bump development version
------------------------
1. Run `releasetool start` again, but specify "snapshot" when prompted for the type of release.
   This will bump the artifact versions and create a new snapshot pull request.
2. Review and submit the PR.
