import com.stacc.jenkins.StaccPipelineSupport

def call(Map config = [:]) {
    validateConfig(config)
    def helper = new StaccPipelineSupport(this)

    pipeline {
        agent any
        environment {
            APP_NAME = "${config.appName}"
            SONAR_SERVER = 'LabSonarQube'
            SONAR_PROJECT_NAME = "${config.sonarProjectName}"
            SONAR_PROJECT_KEY = "${config.sonarProjectKey}"
            SONAR_SOURCES = './src'
            SONAR_SONAR_LOGIN = 'adam-stegienko'
            DOCKER_REGISTRY = 'registry.stegienko.com:8443'
            SEMVER_CHANNEL_RULES = "${config.semverChannelRules ?: 'tag=stable,pr=alpha:changeId.buildNumber.shortSha,master=alpha:buildNumber,dev=beta:buildNumber,release/*=rc:buildNumber,*=beta:buildNumber'}"
        }
        options {
            timestamps()
        }
        tools {
            maven 'Maven'
            jdk 'JDK'
            dockerTool '26.1.1'
        }
        stages {

            stage('Start') {
                steps {
                    script {
                        step([$class: 'GitHubPRStatusBuilder', statusMessage: [content: 'Pipeline started']])
                        step([$class: 'GitHubCommitStatusSetter', statusResultSource: [$class: 'ConditionalStatusResultSource', results: [[$class: 'AnyBuildResult', message: 'Build started', state: 'PENDING']]]])
                    }
                }
            }

            stage('Clean Workspace') {
                steps {
                    sshagent(['jenkins_github_np']) {
                        script {
                            helper.cleanGit()
                        }
                    }
                }
            }

            stage('Calculate Version') {
                when {
                    expression {
                        return helper.hasRelatedChanges(config.moduleDir)
                    }
                }
                steps {
                    script {
                        env.GIT_COMMIT_SHA = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                        sh "echo 'Current commit SHA: ${env.GIT_COMMIT_SHA}'"

                        def packageVersion = dir(config.moduleDir) {
                            sh(returnStdout: true, script: 'mvn help:evaluate -Dexpression=project.version -q -DforceStdout').trim()
                        }
                        helper.assertSemVerBaseVersion(packageVersion, 'POM')
                        sh "echo 'POM version: ${packageVersion}'"

                        env.APP_VERSION = helper.calculateSemVerVersion(
                            '',
                            env.APP_NAME,
                            packageVersion,
                            env.GIT_COMMIT_SHA,
                            null,
                            env.BRANCH_NAME,
                            env.CHANGE_ID,
                            env.BUILD_NUMBER,
                            env.SEMVER_CHANNEL_RULES,
                            env.TAG_NAME
                        )

                        sh "echo 'Docker tag to build: ${env.APP_VERSION}'"
                    }
                }
            }

            stage('SonarQube analysis') {
                when {
                    expression {
                        return helper.hasRelatedChanges(config.moduleDir)
                    }
                }
                steps {
                    dir(config.moduleDir) {
                        withMaven() {
                            withSonarQubeEnv(env.SONAR_SERVER) {
                                sh "mvn versions:set -DnewVersion=${env.APP_VERSION}"
                                sh "mvn clean package sonar:sonar -Dsonar.projectKey=${env.SONAR_PROJECT_KEY} -Dsonar.projectName='${env.SONAR_PROJECT_NAME}'"
                            }
                        }
                    }
                }
            }

            stage('Docker Build') {
                when {
                    expression {
                        return helper.hasRelatedChanges(config.moduleDir) && currentBuild.currentResult == 'SUCCESS'
                    }
                }
                steps {
                    script {
                        sh 'cp ~/.m2/settings.xml maven-settings.xml'

                        sh """
                            docker build \
                            --build-arg APP_VERSION=${env.APP_VERSION} \
                            --build-arg SKIP_TESTS=true \
                            -t ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.APP_VERSION} .
                        """

                        sh 'rm -f maven-settings.xml'
                    }
                }
            }

            stage('Docker Image Security Scan') {
                when {
                    expression {
                        return helper.hasRelatedChanges(config.moduleDir) && currentBuild.currentResult == 'SUCCESS'
                    }
                }
                steps {
                    sh "docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v cache_dir:/opt/cache aquasec/trivy image --severity HIGH,CRITICAL --exit-code 0 --timeout 10m0s ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.APP_VERSION}"
                }
            }
        }

        post {
            always {
                script {
                    helper.publishBuildStatus()
                }
                emailext body: "Build ${currentBuild.currentResult}: Job ${env.JOB_NAME} build ${env.BUILD_NUMBER}\nMore info at: ${env.BUILD_URL}",
                    from: 'jenkins+blueflamestk@gmail.com',
                    subject: "${currentBuild.currentResult}: Job '${env.JOB_NAME}' (${env.BUILD_NUMBER})",
                    to: 'adam.stegienko1@gmail.com'
            }
        }
    }
}

private void validateConfig(Map config) {
    ['moduleDir', 'appName', 'sonarProjectName', 'sonarProjectKey'].each { key ->
        if (!config[key]) {
            throw new IllegalArgumentException("Missing required config key: ${key}")
        }
    }
}