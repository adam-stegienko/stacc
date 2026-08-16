Configure a Jenkins Shared Library named `stacc-pipelines` that points to this repository.

Required layout now lives in:
- `vars/staccServicePipeline.groovy`
- `vars/staccServicePrPipeline.groovy`
- `src/com/stacc/jenkins/StaccPipelineSupport.groovy`

Expected Jenkins usage:

```groovy
@Library('stacc-pipelines') _
```

After the library is configured in Jenkins global settings, each module `Jenkinsfile` and `Jenkinsfile.pr` can stay as a thin wrapper.