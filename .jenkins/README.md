# Jenkins Shared Library

This repository now uses a Jenkins Shared Library layout without requiring a globally preconfigured library entry in Jenkins.

Reusable pipeline code lives in:

- `vars/staccServicePipeline.groovy`
- `vars/staccServicePrPipeline.groovy`
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
