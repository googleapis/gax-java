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

* Generate the key ```gpg --gen-key```
  * Keep the defaults, but specify a passphrase

* Find the ID of your public key ```gpg --list-secret-keys```
  * Look for the line with format ```sec   2048R/ABCDEFGH 2015-11-17```
  * The ```ABCDEFGH``` is the ID for your public key

* Upload your public key to a public server: ```gpg --send-keys --keyserver hkp://pgp.mit.edu ABCDEFGH```

Add deploy credential settings
------------------------
* Create a settings file at ```$HOME/.gradle/gradle.properties``` with your key information and your sonatype username/password

```
signing.keyId=<YOUR-KEY-ID-HERE>
signing.password=<YOUR-PASSWORD-HERE>
signing.secretKeyRingFile=/usr/local/google/home/<YOUR-USER-NAME>/.gnupg/secring.gpg

ossrhUsername=<YOUR-NEXUS-USERNAME>
ossrhPassword=<YOUR-NEXUS-PASSWORD>
```

Update version and deploy to Sonatype
-------------------------------------
* Update the ```gax/version.txt``` and ```gax-grpc/version.txt``` files to the
  release version you want
* Run ```./gradlew stageRelease``` to:
  * Update ```README.md``` and ```samples/pom.xml```
  * Regenerate ```gh-pages``` branch containing Javadocs
  * Stage artifacts on Sonatype: to the staging repository if "-SNAPSHOT" is *not* included in the version; otherwise to the snapshot repository only
* Submit a pull request, get it reviewed, and submit

Publish the release
-------------------
* Run ```./gradlew finalizeRelease```
  * Note: this will release **ALL** versions that have been staged to Sonatype:
    if you have staged versions you do not intend to release, remove these first
    from the [Nexus Repository Manager](https://oss.sonatype.org/) by logging in
    (upper right) and browsing staging repositories (left panel)
* It will take some time (~10 min to ~8 hours) for the package to transition

Bump development version
------------------------
* Update the ```gax/version.txt``` and ```gax-grpc/version.txt``` files to the
  following "-SNAPSHOT" version
