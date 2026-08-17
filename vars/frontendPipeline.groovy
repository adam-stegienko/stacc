import com.stacc.jenkins.StaccPipelineSupport

def call(Map config = [:]) {
    validateConfig(config)
    def helper = new StaccPipelineSupport(this)

    pipeline {
        agent any

        triggers {
            githubPush()
        }

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
            jdk 'JDK'
            dockerTool '26.1.1'
            nodejs 'NodeJS'
        }
        stages {

            stage('Clean Workspace') {
                steps {
                    sshagent(['jenkins_github_np']) {
                        script {
                            helper.cleanGit()
                            sh 'git tag -d $(git tag -l) > /dev/null 2>&1'
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
                        def currentCommitSHA = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                        sh "echo 'Current commit SHA: ${currentCommitSHA}'"

                        def packageVersion = dir(config.moduleDir) {
                            sh(returnStdout: true, script: 'node -p "require(\'./package.json\').version"').trim()
                        }
                        helper.assertSemVerBaseVersion(packageVersion, 'package.json')
                        sh "echo 'Package.json version: ${packageVersion}'"

                        withCredentials([usernamePassword(credentialsId: 'docker_registry_credentials', usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASS')]) {
                            def netrcFile = "${env.WORKSPACE}/.netrc-${env.BUILD_NUMBER}"
                            def registryHost = env.DOCKER_REGISTRY.split(':')[0]

                            sh """
                            cat > ${netrcFile} <<EOF
machine ${registryHost}
login \$REGISTRY_USER
password \$REGISTRY_PASS
EOF
                            chmod 600 ${netrcFile}
                            """

                            try {
                                env.APP_VERSION = helper.calculateSemVerVersion(
                                    env.DOCKER_REGISTRY,
                                    env.APP_NAME,
                                    packageVersion,
                                    currentCommitSHA,
                                    netrcFile,
                                    env.BRANCH_NAME,
                                    env.CHANGE_ID,
                                    env.BUILD_NUMBER,
                                    env.SEMVER_CHANNEL_RULES,
                                    env.TAG_NAME
                                )
                            } finally {
                                sh "rm -f ${netrcFile}"
                            }
                        }

                        env.GIT_COMMIT_SHA = currentCommitSHA
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
                        sh 'npm ci --legacy-peer-deps'
                        sh 'npm run build'
                    }
                }
            }

            stage('SonarQube analysis') {
                when {
                    expression {
                        return helper.hasRelatedChanges(config.moduleDir) && currentBuild.currentResult == 'SUCCESS' && env.BRANCH_NAME == 'master'
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
                        return helper.hasRelatedChanges(config.moduleDir) && currentBuild.currentResult == 'SUCCESS' && env.BRANCH_NAME?.startsWith('release/')
                    }
                }
                steps {
                    sh "docker run --rm -v /var/run/docker.sock:/var/run/docker.sock aquasec/trivy image --severity HIGH,CRITICAL --exit-code 0 ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.APP_VERSION}"
                }
            }

            stage('Docker Push') {
                when {
                    expression {
                        return helper.hasRelatedChanges(config.moduleDir) && currentBuild.currentResult == 'SUCCESS'
                    }
                }
                steps {
                    script {
                        withCredentials([usernamePassword(credentialsId: 'docker_registry_credentials', usernameVariable: 'REGISTRY_USER', passwordVariable: 'REGISTRY_PASS')]) {
                            sh '''
                                echo "$REGISTRY_PASS" | docker login --username "$REGISTRY_USER" --password-stdin "https://''' + env.DOCKER_REGISTRY + '''"
                            '''
                            sh "docker push ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.APP_VERSION}"

                            if (env.BRANCH_NAME == 'master') {
                                sh "docker tag ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.APP_VERSION} ${env.DOCKER_REGISTRY}/${env.APP_NAME}:latest"
                                sh "docker push ${env.DOCKER_REGISTRY}/${env.APP_NAME}:latest"
                                sh "echo 'Pushed latest tag for master branch'"
                            } else {
                                sh "echo 'Skipping latest tag (only master branch gets latest tag)'"
                            }

                            sh "docker logout ${env.DOCKER_REGISTRY}"
                        }
                    }
                }
            }

            stage('Cleanup') {
                steps {
                    script {
                        if (helper.hasRelatedChanges(config.moduleDir)) {
                            sh "echo 'Build completed successfully'"
                            sh "echo 'Docker image: ${env.DOCKER_REGISTRY}/${env.APP_NAME}:${env.APP_VERSION}'"
                        } else {
                            sh "echo 'No related changes detected for ${config.moduleDir}; pipeline stages were skipped.'"
                        }
                    }
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