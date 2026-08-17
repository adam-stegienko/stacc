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
        return affectedPaths.any { path ->
            isSharedPipelinePath(path) ||
                path.startsWith("${moduleDir}/") ||
                (!monorepoPathsDetected && isModuleRootPath(path))
        }
    }

    private boolean isSharedPipelinePath(String path) {
        return path.startsWith('vars/') ||
            path.startsWith('src/com/stacc/jenkins/') ||
            path.startsWith('.jenkins/')
    }

    private boolean isModuleRootPath(String path) {
        return path == 'README.md' ||
            path == 'pom.xml' ||
            path == 'Dockerfile' ||
            path == 'Jenkinsfile' ||
            path == 'Jenkinsfile.pr' ||
            path.startsWith('src/') ||
            path.startsWith('config/') ||
            path.startsWith('helm/')
    }

    String calculateSemVerVersion(
        String registry,
        String imageName,
        String baseVersion,
        String currentCommitSHA,
        String credFile,
        String branchName,
        String changeId,
        String buildNumber,
        String channelRules = null,
        String tagName = null
    ) {
        def effectiveRules = channelRules ?: 'tag=stable,pr=alpha:changeId.buildNumber.shortSha,master=alpha:buildNumber,dev=beta:buildNumber,release/*=rc:buildNumber,*=beta:buildNumber'
        def semVerBase = normalizeBaseSemVer(baseVersion)
        def selectedRule = selectChannelRule(effectiveRules, branchName, changeId, tagName)

        def channel = selectedRule.channel
        if (channel == 'stable') {
            if (selectedRule.pattern == 'tag') {
                return semVerBase
            }
            return calculateNextStableVersion(registry, imageName, semVerBase, currentCommitSHA, credFile)
        }

        def parts = resolveSuffixParts(selectedRule.parts, branchName, changeId, buildNumber, currentCommitSHA, tagName)
        return buildPreReleaseVersion(semVerBase, channel, parts)
    }

    void assertSemVerBaseVersion(String baseVersion, String sourceLabel) {
        if (!normalizeBaseSemVer(baseVersion)) {
            script.error("${sourceLabel} version '${baseVersion}' is not SemVer-compatible. Use MAJOR.MINOR.PATCH (optional -SNAPSHOT).")
        }
    }

    String getLatestDockerTag(String registry, String imageName, String majorMinor, String credFile) {
        try {
            def tagsJson = script.sh(
                returnStdout: true,
                script: """
                curl -s --netrc-file ${credFile} https://${registry}/v2/${imageName}/tags/list | \
                jq -r '.tags // [] | .[]' | \
                grep -E '^${majorMinor}\\.[0-9]+${'$'}' | \
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

    String calculateNextStableVersion(String registry, String imageName, String baseVersion, String currentCommitSHA, String credFile) {
        def versionParts = baseVersion.tokenize('.')
        def major = versionParts[0]
        def minor = versionParts[1]

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
            return "${majorMinor}.${nextPatch}"
        }

        script.sh "echo 'No existing tags found for ${majorMinor}.x, starting from 0'"
        return "${majorMinor}.0"
    }

    private String normalizeBaseSemVer(String baseVersion) {
        def version = (baseVersion ?: '').trim()
        def matcher = version =~ /^(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$/
        if (matcher.matches()) {
            return "${matcher[0][1]}.${matcher[0][2]}.${matcher[0][3]}"
        }
        return null
    }

    private Map selectChannelRule(String channelRules, String branchName, String changeId, String tagName) {
        def parsedRules = parseChannelRules(channelRules)
        def branch = branchName ?: ''
        def hasPr = changeId != null && changeId.toString().trim()
        def hasTag = tagName != null && tagName.toString().trim()

        for (rule in parsedRules) {
            if (rule.pattern == 'pr' && hasPr) {
                return rule
            }
            if (rule.pattern == 'tag' && hasTag) {
                return rule
            }
            if (rule.pattern != 'pr' && rule.pattern != 'tag' && matchesPattern(branch, rule.pattern)) {
                return rule
            }
        }

        return [pattern: '*', channel: 'beta', parts: ['buildNumber']]
    }

    private List<Map> parseChannelRules(String channelRules) {
        def rules = []
        channelRules.split(',').each { rawRule ->
            def rule = rawRule.trim()
            if (!rule || !rule.contains('=')) {
                return
            }

            def split = rule.split('=', 2)
            def pattern = split[0].trim()
            def channelAndParts = split[1].trim()
            if (!pattern || !channelAndParts) {
                return
            }

            def channel = channelAndParts
            def parts = []
            if (channelAndParts.contains(':')) {
                def cp = channelAndParts.split(':', 2)
                channel = cp[0].trim()
                parts = cp[1].split('\\.').collect { it.trim() }.findAll { it }
            }

            rules << [
                pattern: pattern,
                channel: sanitizePreReleaseIdentifier(channel),
                parts: parts
            ]
        }

        return rules
    }

    private boolean matchesPattern(String value, String pattern) {
        if (pattern == '*') {
            return true
        }
        if (pattern.endsWith('*')) {
            return value.startsWith(pattern[0..-2])
        }
        return value == pattern
    }

    private List resolveSuffixParts(List configuredParts, String branchName, String changeId, String buildNumber, String commitSha, String tagName) {
        def parts = (configuredParts ?: [])
        if (parts.isEmpty()) {
            parts = ['buildNumber']
        }

        def tokenValues = [
            changeId: changeId,
            buildNumber: buildNumber,
            shortSha: shortCommitSha(commitSha),
            branchName: sanitizePreReleaseIdentifier(branchName ?: ''),
            tagName: sanitizePreReleaseIdentifier(tagName ?: '')
        ]

        return parts.collect { token ->
            tokenValues.containsKey(token) ? tokenValues[token] : token
        }
    }

    private String buildPreReleaseVersion(String semVerBase, String channel, List parts) {
        def normalizedParts = parts.findAll { it != null && it.toString().trim() }
            .collect { sanitizePreReleaseIdentifier(it.toString()) }
            .findAll { it }

        if (normalizedParts.isEmpty()) {
            normalizedParts = ['0']
        }

        return "${semVerBase}-${channel}.${normalizedParts.join('.')}"
    }

    private String sanitizePreReleaseIdentifier(String value) {
        def cleaned = (value ?: '').toLowerCase().replaceAll(/[^0-9a-z-]/, '-')
        cleaned = cleaned.replaceAll(/-+/, '-')
        cleaned = cleaned.replaceAll(/^-+|-+$/, '')
        return cleaned ?: '0'
    }

    private String shortCommitSha(String commitSha) {
        def sha = (commitSha ?: '').trim()
        if (!sha) {
            return 'unknown'
        }
        return sha.length() > 8 ? sha.substring(0, 8) : sha
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