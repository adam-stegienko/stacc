package com.stacc.jenkins

class StaccPipelineSupport implements Serializable {

    private final def script

    StaccPipelineSupport(def script) {
        this.script = script
    }

    void cleanGit() {
        script.sh 'git fetch --all'
        script.sh 'git reset --hard'
        script.sh 'git clean -fdx'
    }

    boolean hasRelatedChanges(String moduleDir) {
        def changeSets = script.currentBuild.changeSets
        if (changeSets == null || changeSets.isEmpty()) {
            return true
        }

        def affectedPaths = []
        for (changeLogSet in changeSets) {
            for (entry in changeLogSet.items) {
                for (file in entry.affectedFiles) {
                    affectedPaths << file.path
                }
            }
        }

        if (affectedPaths.isEmpty()) {
            return true
        }

        boolean monorepoPathsDetected = affectedPaths.any { it.startsWith('stacc-') }
        if (monorepoPathsDetected) {
            return affectedPaths.any { it.startsWith("${moduleDir}/") }
        }

        return affectedPaths.any {
            it == 'pom.xml' || it == 'Dockerfile' || it == 'Jenkinsfile' || it == 'Jenkinsfile.pr' ||
                it.startsWith('src/') || it.startsWith('config/') || it.startsWith('helm/')
        }
    }

    String getLatestDockerTag(String registry, String imageName, String majorMinor, String credFile) {
        try {
            def tagsJson = script.sh(
                returnStdout: true,
                script: """
                curl -s --netrc-file ${credFile} https://${registry}/v2/${imageName}/tags/list | \
                jq -r '.tags // [] | .[]' | \
                grep -E '^${majorMinor}\\.[0-9]+' | \
                sort -V | \
                tail -n 1
                """
            ).trim()

            if (tagsJson) {
                return tagsJson
            }
            return null
        } catch (Exception e) {
            script.sh "echo 'Could not fetch tags from registry: ${e.message}'"
            return null
        }
    }

    String getImageCommitSHA(String registry, String imageName, String tag) {
        try {
            def manifest = script.withEnv(["REGISTRY=${registry}", "IMAGE_NAME=${imageName}", "TAG=${tag}"]) {
                script.sh(
                    returnStdout: true,
                    script: '''
                    docker pull ${REGISTRY}/${IMAGE_NAME}:${TAG} > /dev/null 2>&1 || true
                    docker inspect ${REGISTRY}/${IMAGE_NAME}:${TAG} 2>/dev/null | \
                    jq -r '.[0].Config.Labels."git.commit.sha" // empty' || echo ""
                    '''
                ).trim()
            }

            return manifest ?: null
        } catch (Exception e) {
            return null
        }
    }

    String calculateNextVersion(String registry, String imageName, String baseVersion, String currentCommitSHA, String credFile) {
        def versionParts = baseVersion.tokenize('.')
        def major = versionParts[0]
        def minor = versionParts[1]

        def suffix = ""
        def patchPart = versionParts.size() > 2 ? versionParts[2] : null
        if (patchPart?.contains('-')) {
            def patchSplit = patchPart.split('-', 2)
            suffix = "-${patchSplit[1]}"
        }

        def majorMinor = "${major}.${minor}"
        def latestTag = getLatestDockerTag(registry, imageName, majorMinor, credFile)

        if (latestTag) {
            script.sh "echo 'Latest tag in registry: ${latestTag}'"

            def tagCommitSHA = getImageCommitSHA(registry, imageName, latestTag)
            if (tagCommitSHA && tagCommitSHA == currentCommitSHA) {
                script.sh "echo 'Tag ${latestTag} already exists for commit ${currentCommitSHA}, reusing it'"
                return latestTag
            }

            def latestPatchPart = latestTag.tokenize('.')[2]
            def latestPatchNum = latestPatchPart.split('-')[0].toInteger()
            def nextPatch = latestPatchNum + 1
            script.sh "echo 'Incrementing patch version from ${latestPatchNum} to ${nextPatch}'"
            return "${majorMinor}.${nextPatch}${suffix}"
        }

        script.sh "echo 'No existing tags found for ${majorMinor}.x, starting from 0'"
        return "${majorMinor}.0${suffix}"
    }

    void publishBuildStatus() {
        try {
            if (script.currentBuild.currentResult == 'SUCCESS') {
                script.step([$class: 'GitHubCommitStatusSetter', statusResultSource: [
                    $class: 'ConditionalStatusResultSource',
                    results: [[$class: 'BetterThanOrEqualBuildResult', message: 'Build succeeded', state: 'SUCCESS']]
                ]])
                script.step([$class: 'githubPRStatusPublisher',
                    statusMsg: [content: 'Build succeeded'],
                    unstableAs: 'SUCCESS'
                ])
            } else if (script.currentBuild.currentResult == 'FAILURE') {
                script.step([$class: 'GitHubCommitStatusSetter', statusResultSource: [
                    $class: 'ConditionalStatusResultSource',
                    results: [[$class: 'BetterThanOrEqualBuildResult', message: 'Build failed', state: 'FAILURE']]
                ]])
                script.step([$class: 'githubPRStatusPublisher',
                    statusMsg: [content: 'Build failed'],
                    unstableAs: 'FAILURE'
                ])
            } else {
                script.step([$class: 'GitHubCommitStatusSetter', statusResultSource: [
                    $class: 'ConditionalStatusResultSource',
                    results: [[$class: 'AnyBuildResult', message: "Build aborted. Result: ${script.currentBuild.currentResult}", state: 'ERROR']]
                ]])
                script.step([$class: 'githubPRStatusPublisher',
                    statusMsg: [content: "Build aborted. Result: ${script.currentBuild.currentResult}"],
                    unstableAs: 'ERROR'
                ])
            }
        } catch (Exception e) {
            // Suppress/log nothing
        }
    }
}