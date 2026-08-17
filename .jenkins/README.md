# Jenkins Shared Library

This repository now uses a Jenkins Shared Library layout without requiring a globally preconfigured library entry in Jenkins.

Reusable pipeline code lives in:

- `vars/javaPipeline.groovy`
- `vars/javaPrPipeline.groovy`
- `vars/frontendPipeline.groovy`
- `vars/frontendPrPipeline.groovy`
- `src/com/stacc/jenkins/StaccPipelineSupport.groovy`

Each module `Jenkinsfile` and `Jenkinsfile.pr` dynamically loads the library from this same repository using:

```groovy
def libraryVersion = env.CHANGE_BRANCH ?: env.BRANCH_NAME ?: 'master'

library identifier: "stacc-pipelines@${libraryVersion}", retriever: modernSCM([
    $class: 'GitSCMSource',
    remote: 'git@github.com:adam-stegienko/stacc.git',
    credentialsId: 'jenkins_github_np'
])
```

That means no `Manage Jenkins -> Global Pipeline Libraries` setup should be required, assuming the Jenkins instance already has the Git and Pipeline Shared Library plugins available.

## Unified SemVer Strategy

Java and frontend pipelines now use the same versioning rules through `StaccPipelineSupport.calculateSemVerVersion(...)`.

The pipelines fail fast when the manifest version is not SemVer-compatible.

- Source-of-truth base version comes from the module manifest:
  - Java: `pom.xml` `project.version`
  - Frontend: `package.json` `version`
- Default channel mapping (evaluated in order):
  - `tag=stable`
  - `pr=alpha:changeId.buildNumber.shortSha`
  - `master=alpha:buildNumber`
  - `dev=beta:buildNumber`
  - `release/*=rc:buildNumber`
  - `*=beta:buildNumber`

Result examples with default mapping:

- Tag build: `MAJOR.MINOR.PATCH`
- PR build: `MAJOR.MINOR.PATCH-alpha.<changeId>.<buildNumber>.<shortSha>`
- Master build: `MAJOR.MINOR.PATCH-alpha.<buildNumber>`
- Dev build: `MAJOR.MINOR.PATCH-beta.<buildNumber>`
- Release build: `MAJOR.MINOR.PATCH-rc.<buildNumber>`

Notes:

- Docker tags remain SemVer-compatible and Docker-safe (no `+build` metadata).
- Stable patch increments ignore pre-release tags and only use strict `MAJOR.MINOR.PATCH` tags.
- Branch and channel behavior is configurable via pipeline config/environment:
  - `semverChannelRules`

`semverChannelRules` format:

- `pattern=channel:part1.part2.part3`
- `pattern` supports exact names (for example `master`), wildcards (for example `release/*`), and reserved patterns `pr` and `tag`.
- Rule order matters: first match wins.
- Supported part tokens: `changeId`, `buildNumber`, `shortSha`, `branchName`, `tagName`.
