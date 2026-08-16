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
        }
        options {
            timestamps()
        }
        tools {
            jdk 'JDK'
            dockerTool '26.1.1'
            nodejs 'NodeJS'
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

            stage('Checkout') {
                steps {
                    checkout scm
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
                        env.APP_VERSION = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        sh "echo 'Current commit SHA: ${env.GIT_COMMIT_SHA}'"
                        sh "echo 'Docker tag to build: ${env.APP_VERSION}'"
                    }
                }
            }

            stage('NPM Build') {
                when {
                    expression {
                        return helper.hasRelatedChanges(config.moduleDir)
                    }
                }
                steps {
                    dir(config.moduleDir) {
                        sh 'npm ci'
                        sh 'npm run build'
                    }
                }
            }

            stage('SonarQube analysis') {
                when {
                    expression {
                        return helper.hasRelatedChanges(config.moduleDir) && currentBuild.currentResult == 'SUCCESS'
                    }
                }
                steps {
                    dir(config.moduleDir) {
                        script {
                            def scannerHome = tool 'JenkinsSonarScanner'
                            withSonarQubeEnv(env.SONAR_SERVER) {
                                sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${env.SONAR_PROJECT_KEY} -Dsonar.projectName='${env.SONAR_PROJECT_NAME}' -Dsonar.projectVersion=${env.APP_VERSION}"
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
                    dir(config.moduleDir) {
                        sh """
                            docker build \
                            --build-arg APP_VERSION=${env.APP_VERSION} \
                            --label git.commit.sha=${env.GIT_COMMIT_SHA} \
                            --label build.timestamp=\$(date -u +%Y-%m-%dT%H:%M:%SZ) \
                            -t ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.APP_VERSION} .
                        """
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