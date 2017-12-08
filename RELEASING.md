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

* Determine your `gpg` version: `gpg -- version`

* Find the ID of your public key
  * If you're using GPG version 1.y.z, `gpg --list-secret-keys`
    * Look for the line with format `sec   2048R/ABCDEFGH 2015-11-17`
    * The `ABCDEFGH` is the ID for your public key
  * If you're using GPG version 2.y.z `gpg --list-secret-keys --keyid-format LONG`
    * Look for line with format `sec   rsa2048/ABCDEFGHIJKLMNOP`
    * The `ABCDEFGHIJKLMNOP` is the ID. It is 16-byte long, but Gradle
      only support 8-byte keys. Use the *last* 8 bytes of the key when
      following the rest of this document.
    * `gpg --export-secret-keys $HOME/.gnupg/secring.gpg`

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

To prepare a release
====================

Update version and deploy to Sonatype
-------------------------------------
1. Update the `gax/version.txt`, `gax-grpc/version.txt`, and `gax-httpjson/version.txt` files to the
  release version you want
2. Run `./gradlew stageRelease` to:
  * Update `README.md` and `samples/pom.xml`
  * Regenerate `gh-pages` branch containing Javadocs
  * Stage artifacts on Sonatype: to the staging repository if "-SNAPSHOT" is *not* included in the version; otherwise to the snapshot repository only
3. Submit a pull request, get it reviewed, and submit

Publish the release
-------------------
1. Run `./gradlew finalizeRelease`
  * Note: this will release **ALL** versions that have been staged to Sonatype:
    if you have staged versions you do not intend to release, remove these first
    from the [Nexus Repository Manager](https://oss.sonatype.org/) by logging in
    (upper right) and browsing staging repositories (left panel)
2. It will take some time (~10 min to ~8 hours) for the package to transition
3. Publish a new release on Github:
  * Go to the [releases page](https://github.com/googleapis/gax-java/releases) and click "Draft a new release"
    in order to start a new draft.
  * Make sure the "Tag Version" is `vX.Y.Z` and the "Release Title" is `X.Y.Z`, where `X.Y.Z` is the release
    version as listed in the `version.txt` files.
  * Add the commits since the last release into the release draft. Try to group them into sections with related
    changes. Anything that is a breaking change needs to be marked with `*breaking change*`. Such changes are
    only allowed for alpha/beta modules and `@BetaApi` features.
  * Ensure that the format is consistent with previous releases. After adding any missing updates and
    reformatting as necessary, publish the draft.

Bump development version
------------------------
1. Update the `gax/version.txt`, `gax-grpc/version.txt`, and `gax-httpjson/version.txt` files to the
  following "-SNAPSHOT" version
