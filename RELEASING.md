###Overview

Most of the release process is handled by the uploadArchives task. The task can be triggered by
running "./gradlew uploadArchives" from the project's directory. A new artifact will be uploaded
to the staging repository in Sonatype when "-SNAPSHOT" is not included in the version (as
listed in the base directory's `pom.xml`). When "-SNAPSHOT" is included, the task only updates the
artifact in the snapshot repository.

###Release Instructions

Set up Sonatype Account
-----------------------
* Sign up for a Sonatype JIRA account [here](https://issues.sonatype.org)
* Click *Sign Up* in the login box, follow instructions

Get access to repository
------------------------
* Go to [community support](https://issues.sonatype.org/browse/OSSRH)
* Ask for publish rights by creating an issue similar to [this one](https://issues.sonatype.org/browse/OSSRH-16798)
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

Deploy to Sonatype
------------------
* Update all ```pom.xml``` files in the package to the release version you want. Sumit a pull request, get it reviewed, and submit
* ```mvn clean install deploy -DperformRelease=true```
* Verify the publishment [here](https://oss.sonatype.org/#nexus-search;quick~gax)
  * If there is a problem, undo by ```mvn nexus-staging:drop```
* ```mvn nexus-staging:release -DperformRelease=true```
* Update all ```pom.xml``` files to the new snapshot version

Publish the release
-------------------
* Go to [Sonatype](https://oss.sonatype.org/) and log in
* Click on *Staging Repositories* on the left
* Filter down to the repository by typing the package's groupId without periods in the search box
  * In our case, ```comgoogleapi```
* Click the *close*, then *release* button just below the top tabs
* It will take some time (up to 10 minutes) for the package to transition
