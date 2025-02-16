import com.icthh.xm.xmeplugin.utils.*
import com.icthh.xm.xmeplugin.yaml.exts.YamlJsonSchemaContributor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.rd.util.ConcurrentHashMap
import java.io.*
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Paths


val TENANT_NAME: Key<String> = Key.create("TENANT_NAME")

fun <T> VirtualFile.withCache(key: String, project: Project, compute: () -> T): T {
    val cacheKey = buildCacheKey<T>(key)
    return CachedValuesManager.getManager(project).getCachedValue(this, cacheKey, {
        CachedValueProvider.Result.create(compute.invoke(), VFS_STRUCTURE_MODIFICATIONS)
    }, false)
}

fun InputStream.readTextAndClose(charset: Charset = Charsets.UTF_8): String {
    return this.bufferedReader(charset).use { it.readText() }
}

fun createProjectFile(
    directory: String,
    name: String,
    project: Project,
    body: String
) {
    project.doAsync {
        File(directory).mkdirs()
        val file = File(directory.trimEnd('/') + "/" + name.trimStart('/'))
        if (!file.exists()) {
            file.createNewFile()
        }
        file.writeText(body)
        project.addToGit(file)
        val virtualFile = VfsUtil.findFileByIoFile(file, true) ?: return@doAsync
        invokeLater {
            OpenFileDescriptor(project, virtualFile).navigate(true)
        }
    }
}

fun File.deleteSymlink() {
    walkTopDown().forEach {
        if (Files.isSymbolicLink(it.toPath())) {
            it.delete()
            it.parentFile?.delete()
            it.parentFile?.parentFile?.delete()
        }
    }
    VfsUtil.findFile(toPath(), false)?.refresh(false, false)
    delete()
}

fun String.toVirtualFile(): VirtualFile? {
    return VfsUtil.findFile(Paths.get(this), false)
}

fun File.toVirtualFile(): VirtualFile? {
    return VfsUtil.findFile(this.toPath(), false)
}

fun Project.configPathToRealPath(configPath: String): String {
    val selected = this.getSettings().selected()
    val path = configPath.trimStart('/')
    if (!isConfigProject()) {
        return selected?.basePath.let { "$it/$path" }
    }
    return "${this.basePath}/$path"
}

fun String.toNormalizedPath(): String = this.replace(File.separatorChar, '/')
val File.normalizedPath: String get() = this.absolutePath.replace(File.separatorChar, '/')

fun Project.toAbsolutePath(relatedPath: String) = configPathToRealPath(toRelatedPath(relatedPath))
fun Project.toRelatedPath(absolutePath: String): String {
    if (absolutePath.startsWith(getConfigRootDir())) {
        return CONFIG_DIR_NAME + absolutePath.substringAfter(getConfigRootDir())
    } else if (absolutePath.startsWith(getLinkedConfigRootDir())) {
        val relatedPath = absolutePath.substringAfter(getLinkedConfigRootDir())
        if (relatedPath.startsWith("/features/")) {
            val symlinkedFile = File(absolutePath)
            val resolvedPath = symlinkedFile.canonicalFile.normalizedPath
            return toRelatedPath(resolvedPath);
        }
        return CONFIG_DIR_NAME + "/tenants" + relatedPath
    } else {
        return absolutePath
    }
}

private fun VirtualFile.getConfigRelatedPath(project: Project): String {
    return getPathRelatedTo(project)
}

fun VirtualFile.getTenantName(project: Project): String {
    val inTenantPath = getConfigRelatedPath(project).substringAfter("/")
    if (inTenantPath.contains("/")) {
        return inTenantPath.substringBefore("/")
    }
    return inTenantPath
}

fun VirtualFile.getServiceName(project: Project): String? {
    val relatedPath = getConfigRelatedPath(project)
    val tenantName = getTenantName(project)
    if (!relatedPath.contains("/${tenantName}/")) {
        return null
    }
    val inTenantPath = relatedPath.substringAfter("/$tenantName/")
    if (inTenantPath.contains("/")) {
        return inTenantPath.substringBefore("/")
    }
    return inTenantPath
}

fun PsiFile.getTenantName(): String {
    var tenantName = getUserData(TENANT_NAME)
    if (tenantName == null) {
        tenantName = virtualFile().getTenantName(project)
        putUserData(TENANT_NAME, tenantName)
    }
    return tenantName
}

fun PsiFile.getTenantFolder(): String {
    val tenantName = this.originalFile.getTenantName()
    return "${project.basePath}/config/tenants/${tenantName}";
}

fun PsiFile.getServiceName(): String? {
    return virtualFile().getServiceName(project)
}

fun VirtualFile.isConfigFile(project: Project): Boolean {
    return this.path.startsWith(project.getConfigRootDir()) || this.path.startsWith(project.getLinkedConfigRootDir())
}

val keyCache = ConcurrentHashMap<String, Key<*>>()
fun <T> VirtualFile.savedToUsedData(key: String, operation: VirtualFile.() -> T): T {
    val key: Key<T> = keyCache.getOrPut(key) { Key.create<T>(key) } as Key<T>
    val userData = getUserData(key)
    if (userData == null) {
        val result = operation()
        putUserData(key, result)
        return result
    }
    return userData
}

private fun VirtualFile.getPathRelatedTo(
    project: Project,
    root: String = ""
): String {
    return this.savedToUsedData("getPathRelatedTo + ${this.path}") {

        var vFile = this;

        while (vFile.parent != null && !vFile.parent.path.equals(project.getLinkedConfigRootDir() + root) && !vFile.parent.path.equals(
                project.getConfigRootDir() + root
            )
        ) {
            vFile = vFile.parent
        }

        if (!project.isConfigProject() && vFile.parent != null && !vFile.parent.path.equals(project.getConfigRootDir() + root)) {
            vFile = vFile.parent
        }

        return@savedToUsedData this.path.substring(vFile.path.length)
    }
}

@Synchronized
fun Project.doUpdateSymlinkToLep() {
    val selected = this.getSettings().selected()
    selected ?: return
    if (isConfigProject()) {
        return
    }

    File("${this.basePath}/src/main/lep").deleteSymlink()
    File("${this.basePath}/src/main/lep/features").deleteSymlink()
    File("${this.basePath}/src/test/lep").deleteSymlink()
    File("${this.basePath}/src/test/lep/features").deleteSymlink()
    if (selected.basePath.isNullOrBlank()) {
        return
    }

    val tenantsPath = "${selected.basePath}/config/tenants"
    val featuresPath = "${selected.basePath}/config/features"
    val tenantsDirectory = File(tenantsPath)
    if (!tenantsDirectory.exists()) {
        log.info("Folder '${tenantsPath}' not exists")
        return
    }

    createCommonsSymlink(tenantsPath, "lep", "main")
    (tenantsDirectory.list() ?: emptyArray()).filter { selected.selectedTenants.contains(it) }.forEach {
        createSymlink(tenantsPath, it, "lep", "main")
        createSymlink(tenantsPath, it, "test", "test")
    }

    VfsUtil.findFile(File("${basePath}").toPath(), false)?.refresh(true, true)

    val featuresDirectory = File(featuresPath)
    if (!featuresDirectory.exists()) {
        log.info("Folder '${featuresPath}' not exists")
        return
    }

    selected.selectedFeatures.filter { File("${featuresPath.trimEnd('/')}/${it.name}/${it.version}").exists() }.forEach {
        createFeatures(featuresPath, it.name, it.version, "lep", "main")
        createFeatures(featuresPath,it.name, it.version, "lep", "test")
    }

    VfsUtil.findFile(File("${basePath}").toPath(), false)?.refresh(true, true)
}

private fun Project.createSymlink(tenantsPath: String, tenant: String, sourceType: String, targetType: String) {
    val fromTest = File("${tenantsPath}/${tenant}/${getApplicationName()}/${sourceType}")
    if (fromTest.exists()) {
        val lepPath = "${this.basePath}/src/${targetType}/lep/${tenant}/${getApplicationName()}"
        File(lepPath).mkdirs()
        log.info("${fromTest} -> ${lepPath}/${sourceType}")
        Files.createSymbolicLink(File("${lepPath}/${sourceType}").toPath(), fromTest.toPath())
    }
}

private fun Project.createFeatures(featuresPath: String, feature: String, version: String, sourceType: String, targetType: String) {
    val fromTest = File("${featuresPath}/${feature}/${version}/${getApplicationName()}/${sourceType}")
    if (fromTest.exists()) {
        val lepPath = "${this.basePath}/src/${targetType}/lep/features/${feature}/${getApplicationName()}"
        File(lepPath).mkdirs()
        log.info("${fromTest} -> ${lepPath}/${sourceType}")
        Files.createSymbolicLink(File("${lepPath}/${sourceType}").toPath(), fromTest.toPath())
    }
}

private fun Project.createCommonsSymlink(tenantsPath: String, sourceType: String, targetType: String) {
    val fromTest = File("${tenantsPath}/commons/lep")
    if (fromTest.exists()) {
        val lepPath = "${this.basePath}/src/${targetType}/lep/commons"
        File(lepPath).mkdirs()
        log.info("${fromTest} -> ${lepPath}/${sourceType}")
        Files.createSymbolicLink(File("${lepPath}/${sourceType}").toPath(), fromTest.toPath())
    }
}

fun Project.convertPathToUrl(path: String?): URL? {
    if (path == null) {
        return null
    }

    if (path.startsWith("classpath:")) {
        return YamlJsonSchemaContributor::class.java.classLoader.getResource(path.substringAfter("classpath:"))
    }

    try {
        try {
            return URI(path).toURL()
        } catch (e: Exception) {
            if (e !is URISyntaxException && e !is MalformedURLException) {
                throw e
            }

            var file = File(path)
            if (!file.isAbsolute) {
                val projectBase = basePath
                file = File(projectBase, path)
            }
            return file.toURI().toURL()
        }
    } catch (ex: IOException) {
        log.warn("Error reading file $path", ex)
        return null
    }
}

