package com.icthh.xm.xmeplugin.utils

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.intellij.openapi.diagnostic.Logger
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.MergeCommand
import org.eclipse.jgit.api.TransportCommand
import org.eclipse.jgit.lib.NullProgressMonitor
import org.eclipse.jgit.transport.CredentialsProvider
import org.eclipse.jgit.transport.SshSessionFactory
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.transport.sshd.JGitKeyCache
import org.eclipse.jgit.transport.sshd.KeyPasswordProvider
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder
import org.eclipse.jgit.util.FS
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.security.PublicKey

const val EXTERNAL_TENANTS_FILE = "config/tenants/external-tenants.yml"
private const val GIT_TIMEOUT_SECONDS = 600

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

    try {
        withTransport(repository) { transport ->
            if (File(repositoryDir, ".git").exists()) {
                pullExternalTenantRepository(repositoryDir, uri, branch, transport)
            } else {
                cloneExternalTenantRepository(repositoryDir, uri, branch, repository.depth, transport)
            }
        }
    } catch (e: Exception) {
        externalTenantsLog.warn("Sync of external tenant $tenant from $uri failed", e)
        if (!File(repositoryDir, ".git").exists()) {
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

private fun cloneExternalTenantRepository(
    repositoryDir: File,
    uri: String,
    branch: String?,
    depth: Int?,
    transport: TransportSettings
) {
    repositoryDir.parentFile.mkdirs()
    val clone = Git.cloneRepository()
        .setURI(uri)
        .setDirectory(repositoryDir)
        .setProgressMonitor(NullProgressMonitor.INSTANCE)
    branch?.let { clone.setBranch(it) }
    depth?.takeIf { it > 0 }?.let { clone.setDepth(it) }
    try {
        transport.apply(clone).call().close()
    } catch (e: Exception) {
        repositoryDir.deleteRecursively()
        throw e
    }
}

private fun pullExternalTenantRepository(
    repositoryDir: File,
    uri: String,
    branch: String?,
    transport: TransportSettings
) {
    Git.open(repositoryDir).use { git ->
        val config = git.repository.config
        if (config.getString("remote", "origin", "url") != uri) {
            config.setString("remote", "origin", "url", uri)
            config.save()
        }

        val currentBranch = git.repository.branch
        if (branch != null && currentBranch != branch) {
            // the user switched the clone to another branch on purpose, keep it as is
            externalTenantsLog.info("Skip pull of ${repositoryDir}: branch '$currentBranch' is checked out instead of '$branch'")
            return
        }

        val pull = git.pull()
            .setRemote("origin")
            .setRemoteBranchName(branch ?: currentBranch)
            .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
            .setProgressMonitor(NullProgressMonitor.INSTANCE)
        val result = transport.apply(pull).call()
        if (!result.isSuccessful) {
            externalTenantsLog.warn("Pull of ${repositoryDir} is not fast-forward: ${result.mergeResult?.mergeStatus}")
        }
    }
}

private class TransportSettings(
    val credentialsProvider: CredentialsProvider?,
    val sshSessionFactory: SshSessionFactory?
) {
    fun <C : TransportCommand<C, *>> apply(command: C): C {
        command.setTimeout(GIT_TIMEOUT_SECONDS)
        credentialsProvider?.let { command.setCredentialsProvider(it) }
        sshSessionFactory?.let { factory ->
            command.setTransportConfigCallback { transport ->
                if (transport is SshTransport) {
                    transport.sshSessionFactory = factory
                }
            }
        }
        return command
    }
}

/**
 * Credentials stay in memory: nothing lands in the `.git/config` of the clone.
 * The private key of `ssh.privateKey` lives in a temporary file for the time of the operation only.
 */
private fun <T> withTransport(repository: ExternalTenantRepository, operation: (TransportSettings) -> T): T {
    val ssh = repository.ssh
    if (ssh?.enabled != true) {
        val login = repository.login
        val password = repository.password
        val credentials = if (!login.isNullOrEmpty() || !password.isNullOrEmpty()) {
            UsernamePasswordCredentialsProvider(login.orEmpty(), password.orEmpty())
        } else {
            null
        }
        return operation(TransportSettings(credentials, null))
    }

    val keyFile = ssh.privateKey?.takeIf { it.isNotBlank() }?.let { createPrivateFile("xme-ssh-key", it.trimEnd() + "\n") }
    val userHome = FS.DETECTED.userHome()
    val builder = SshdSessionFactoryBuilder()
        .setHomeDirectory(userHome)
        .setSshDirectory(File(userHome, ".ssh"))
    keyFile?.let { key -> builder.setDefaultIdentities { listOf(key.toPath()) } }
    ssh.passPhrase?.takeIf { it.isNotEmpty() }?.let { passPhrase ->
        builder.setKeyPasswordProvider { PassPhraseProvider(passPhrase) }
    }
    if (ssh.acceptUnknownHost == true) {
        builder.setServerKeyDatabase { _, _ -> AcceptAllServerKeys }
    }
    val factory = builder.build(JGitKeyCache())
    try {
        return operation(TransportSettings(null, factory))
    } finally {
        factory.close()
        keyFile?.delete()
    }
}

private class PassPhraseProvider(private val passPhrase: String) : KeyPasswordProvider {
    private var attempts = 1
    override fun getPassphrase(uri: URIish?, attempt: Int): CharArray = passPhrase.toCharArray()
    override fun setAttempts(maxNumberOfAttempts: Int) {
        attempts = maxNumberOfAttempts
    }
    override fun getAttempts(): Int = attempts
    override fun keyLoaded(uri: URIish?, attempt: Int, error: Exception?): Boolean = false
}

private object AcceptAllServerKeys : ServerKeyDatabase {
    override fun lookup(
        connectAddress: String,
        remoteAddress: InetSocketAddress,
        config: ServerKeyDatabase.Configuration
    ): List<PublicKey> = emptyList()

    override fun accept(
        connectAddress: String,
        remoteAddress: InetSocketAddress,
        serverKey: PublicKey,
        config: ServerKeyDatabase.Configuration,
        provider: CredentialsProvider?
    ): Boolean = true
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
