package com.icthh.xm.xmeplugin.utils

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.concurrent.TimeUnit

const val EXTERNAL_TENANTS_FILE = "config/tenants/external-tenants.yml"
private const val GIT_TIMEOUT_MINUTES = 10L

private val externalTenantsLog = Logger.getInstance("com.icthh.xm.xmeplugin.ExternalTenants")

data class ExternalTenantsConfig(
    @JsonProperty("external-tenants")
    val externalTenants: Map<String, ExternalTenantRepository?>? = null
)

data class ExternalTenantRepository(
    val uri: String? = null,
    val branchName: String? = null,
    val login: String? = null,
    val password: String? = null,
    val depth: Int? = null,
    val ssh: ExternalTenantSsh? = null
)

data class ExternalTenantSsh(
    val enabled: Boolean? = null,
    val privateKey: String? = null,
    val passPhrase: String? = null,
    val acceptUnknownHost: Boolean? = null
)

/**
 * Tenants of `config/tenants/external-tenants.yml` of the config repository, the ones with `uri` only
 * (ee-config ignores an entry without it).
 */
fun readExternalTenants(configBasePath: String?): Map<String, ExternalTenantRepository> {
    if (configBasePath.isNullOrBlank()) {
        return emptyMap()
    }
    val file = File(configBasePath, EXTERNAL_TENANTS_FILE)
    if (!file.isFile) {
        return emptyMap()
    }
    return try {
        val config = YAML_MAPPER.readValue<ExternalTenantsConfig>(file)
        config.externalTenants.orEmpty()
            .filterValues { !it?.uri.isNullOrBlank() }
            .mapValues { it.value!! }
    } catch (e: Exception) {
        externalTenantsLog.warn("Error reading ${file.absolutePath}", e)
        emptyMap()
    }
}

/**
 * Local clones live next to the config repository: `<config-repo>-external-tenants/<TENANT>`.
 */
fun externalTenantRepositoryDir(configBasePath: String, tenant: String): File {
    val configRoot = File(configBasePath).absoluteFile
    return File(configRoot.parentFile, "${configRoot.name}-external-tenants/$tenant")
}

/**
 * Clones the repository of the external tenant, or fast-forwards an existing clone.
 * Returns the `config/tenants` folder of the clone, or null when the clone has no folder of the tenant.
 */
fun syncExternalTenantRepository(configBasePath: String, tenant: String, repository: ExternalTenantRepository): String? {
    val repositoryDir = externalTenantRepositoryDir(configBasePath, tenant)
    val uri = repository.uri ?: return null
    val branch = repository.branchName?.takeIf { it.isNotBlank() }

    if (File(repositoryDir, ".git").exists()) {
        pullExternalTenantRepository(repositoryDir, uri, branch, repository)
    } else {
        repositoryDir.parentFile.mkdirs()
        val args = mutableListOf("clone")
        branch?.let { args += listOf("--branch", it) }
        repository.depth?.takeIf { it > 0 }?.let { args += listOf("--depth", "$it") }
        args += listOf(uri, repositoryDir.absolutePath)
        val result = runGit(repository, repositoryDir.parentFile, args)
        if (!result.success) {
            externalTenantsLog.warn("Clone of external tenant $tenant from $uri failed: ${result.output}")
            repositoryDir.deleteRecursively()
            return null
        }
    }

    val tenantsPath = File(repositoryDir, "config/tenants")
    if (!File(tenantsPath, tenant).isDirectory) {
        externalTenantsLog.warn("External repository of tenant $tenant has no folder config/tenants/$tenant")
        return null
    }
    return tenantsPath.absolutePath
}

private fun pullExternalTenantRepository(
    repositoryDir: File,
    uri: String,
    branch: String?,
    repository: ExternalTenantRepository
) {
    val remoteUrl = runGit(repository, repositoryDir, listOf("config", "--get", "remote.origin.url"))
    if (remoteUrl.output.trim() != uri) {
        runGit(repository, repositoryDir, listOf("remote", "set-url", "origin", uri))
    }

    val currentBranch = runGit(repository, repositoryDir, listOf("rev-parse", "--abbrev-ref", "HEAD")).output.trim()
    if (branch != null && currentBranch != branch) {
        // the user switched the clone to another branch on purpose, keep it as is
        externalTenantsLog.info("Skip pull of ${repositoryDir}: branch '$currentBranch' is checked out instead of '$branch'")
        return
    }

    val args = mutableListOf("pull", "--ff-only", "origin")
    args += branch ?: currentBranch
    val result = runGit(repository, repositoryDir, args)
    if (!result.success) {
        externalTenantsLog.warn("Pull of ${repositoryDir} failed: ${result.output}")
    }
}

private class GitResult(val success: Boolean, val output: String)

private fun runGit(repository: ExternalTenantRepository, workDir: File, args: List<String>): GitResult {
    val tempFiles = ArrayList<File>()
    val outputFile = File.createTempFile("xme-git", ".log").also { tempFiles += it }
    try {
        val processBuilder = ProcessBuilder(listOf("git") + args)
            .directory(workDir)
            .redirectErrorStream(true)
            .redirectOutput(outputFile)
        val env = processBuilder.environment()
        env["GIT_TERMINAL_PROMPT"] = "0"
        configureAuthentication(repository, env, tempFiles)

        val process = processBuilder.start()
        if (!process.waitFor(GIT_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            return GitResult(false, "git ${args.firstOrNull()} timed out")
        }
        return GitResult(process.exitValue() == 0, outputFile.readText())
    } catch (e: Exception) {
        return GitResult(false, e.message ?: e.toString())
    } finally {
        tempFiles.forEach { it.delete() }
    }
}

/**
 * Credentials go through the environment only: nothing lands in the `.git/config` of the clone
 * or in the command line.
 */
private fun configureAuthentication(
    repository: ExternalTenantRepository,
    env: MutableMap<String, String>,
    tempFiles: MutableList<File>
) {
    val ssh = repository.ssh
    if (ssh?.enabled == true) {
        val sshCommand = mutableListOf("ssh")
        ssh.privateKey?.takeIf { it.isNotBlank() }?.let { key ->
            val keyFile = createPrivateFile("xme-ssh-key", key.trimEnd() + "\n").also { tempFiles += it }
            sshCommand += listOf("-i", "'${keyFile.absolutePath}'", "-o", "IdentitiesOnly=yes")
        }
        if (ssh.acceptUnknownHost == true) {
            sshCommand += listOf("-o", "StrictHostKeyChecking=accept-new")
        }
        val passPhrase = ssh.passPhrase
        if (passPhrase.isNullOrEmpty()) {
            sshCommand += listOf("-o", "BatchMode=yes")
        } else {
            val askPass = createPrivateFile("xme-ssh-askpass", "#!/bin/sh\nprintf '%s\\n' \"\$XME_SSH_PASSPHRASE\"\n")
                .also { tempFiles += it }
            askPass.setExecutable(true, true)
            env["SSH_ASKPASS"] = askPass.absolutePath
            env["SSH_ASKPASS_REQUIRE"] = "force"
            env["XME_SSH_PASSPHRASE"] = passPhrase
            env.putIfAbsent("DISPLAY", ":0")
        }
        env["GIT_SSH_COMMAND"] = sshCommand.joinToString(" ")
        return
    }

    val login = repository.login
    val password = repository.password
    if (!login.isNullOrEmpty() || !password.isNullOrEmpty()) {
        val token = Base64.getEncoder().encodeToString("${login.orEmpty()}:${password.orEmpty()}".toByteArray())
        env["GIT_CONFIG_COUNT"] = "1"
        env["GIT_CONFIG_KEY_0"] = "http.extraHeader"
        env["GIT_CONFIG_VALUE_0"] = "Authorization: Basic $token"
    }
}

private fun createPrivateFile(prefix: String, content: String): File {
    val file = File.createTempFile(prefix, "")
    try {
        Files.setPosixFilePermissions(file.toPath(), setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
    } catch (e: UnsupportedOperationException) {
        file.setReadable(false, false)
        file.setReadable(true, true)
    }
    file.writeText(content)
    return file
}
