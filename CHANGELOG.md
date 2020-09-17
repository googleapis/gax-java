# Changelog

### [1.58.3](https://www.github.com/googleapis/gax-java/compare/v1.58.2...v1.58.3) (2020-09-15)


### Bug Fixes

* [gax-java] Add speedy Bazel builds to Travis ([#1181](https://www.github.com/googleapis/gax-java/issues/1181)) ([2fb85fe](https://www.github.com/googleapis/gax-java/commit/2fb85fed095c6043ee39b63a0f7dff3fd93cbd7b))
* [gax-java] add Vim files to .gitignore ([#1179](https://www.github.com/googleapis/gax-java/issues/1179)) ([2de22b6](https://www.github.com/googleapis/gax-java/commit/2de22b6645fbfd7ada7d0067e5cdd3c2039ec190))
* [gax-java] Fix broken Bazel build ([#1180](https://www.github.com/googleapis/gax-java/issues/1180)) ([834c05e](https://www.github.com/googleapis/gax-java/commit/834c05e1d35a17f90bf8cd1b2cdce40bea451c95))

### [1.58.2](https://www.github.com/googleapis/gax-java/compare/v1.58.1...v1.58.2) (2020-08-07)


### Bug Fixes

* Settings objects should not try to read quotaProjectId from credentials ([#1162](https://www.github.com/googleapis/gax-java/issues/1162)) ([1b09bcf](https://www.github.com/googleapis/gax-java/commit/1b09bcff1ddfaed8cfa58b92c787f8fc9b08abef))

### [1.58.1](https://www.github.com/googleapis/gax-java/compare/v1.58.0...v1.58.1) (2020-08-06)


### Bug Fixes

* fix dependencies.properties resource file creation during deployment ([#1163](https://www.github.com/googleapis/gax-java/issues/1163)) ([3e7e1f1](https://www.github.com/googleapis/gax-java/commit/3e7e1f1e64bdeb23a51b5155faea975beec0bc84))
* Watchdog.shutdownNow() does not shutdown executor ([#1158](https://www.github.com/googleapis/gax-java/issues/1158)) ([6241a21](https://www.github.com/googleapis/gax-java/commit/6241a2118690d07dd28ffb9447423363f3f914e4))

## [1.58.0](https://www.github.com/googleapis/gax-java/compare/v1.57.2...v1.58.0) (2020-07-31)


### Features

* add retry logging ([#1160](https://www.github.com/googleapis/gax-java/issues/1160)) ([1575715](https://www.github.com/googleapis/gax-java/commit/15757151d4965276bd01e6772c10288959bb17ec))
* enable setting quota_project_id ([#1128](https://www.github.com/googleapis/gax-java/issues/1128)) ([20bb200](https://www.github.com/googleapis/gax-java/commit/20bb200c8019ad1df8acbfe210cea7d5e9a9a57c))
* non-retryable RPCs use totalTimeout ([#1149](https://www.github.com/googleapis/gax-java/issues/1149)) ([b7646a3](https://www.github.com/googleapis/gax-java/commit/b7646a3a959b7e5ef40158851f26ce6701da8ca4))


### Bug Fixes

* retain context timeouts in ServerStreamingAttemptCallable ([#1155](https://www.github.com/googleapis/gax-java/issues/1155)) ([461ff84](https://www.github.com/googleapis/gax-java/commit/461ff846ca551c2242bf6c60e61234997d0ba58e))

### [1.57.2](https://www.github.com/googleapis/gax-java/compare/v1.57.1...v1.57.2) (2020-07-21)


### Bug Fixes

* Fix javadoc generation on Java11 ([#1145](https://www.github.com/googleapis/gax-java/issues/1145)) ([c7a039e](https://www.github.com/googleapis/gax-java/commit/c7a039e07be02298d9dd906b08e1e1bb995e85e2))
* Preconditions only supports %s format ([#1153](https://www.github.com/googleapis/gax-java/issues/1153)) ([8145311](https://www.github.com/googleapis/gax-java/commit/8145311b38fdd3bf82a4958f8aef5313857b70c0))

### [1.57.1](https://www.github.com/googleapis/gax-java/compare/v1.57.0...v1.57.1) (2020-07-07)


### Bug Fixes

* add back javax.annotation dependency ([#1129](https://www.github.com/googleapis/gax-java/issues/1129)) ([77a4cc3](https://www.github.com/googleapis/gax-java/commit/77a4cc373914396dd343891e38cf743166668c96))


### Dependencies

* update google-auth-library to 0.21.0 ([#1134](https://www.github.com/googleapis/gax-java/issues/1134)) ([6528e5c](https://www.github.com/googleapis/gax-java/commit/6528e5cb9cec50ef01c0d2601c6db518df825747))
