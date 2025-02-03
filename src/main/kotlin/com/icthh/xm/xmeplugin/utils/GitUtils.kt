import com.icthh.xm.xmeplugin.domain.ChangesFiles
import com.icthh.xm.xmeplugin.domain.FilesState
import com.icthh.xm.xmeplugin.utils.getSettings
import com.icthh.xm.xmeplugin.utils.root
import com.intellij.openapi.project.Project
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.internal.storage.file.FileRepository
import org.eclipse.jgit.lib.NullProgressMonitor
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream


fun Project.getLocalBranches(): List<String> {
    val refPrefix = "refs/heads/"
    return withGit {
        it.branchList().call().map { it.name }.map { if (it.startsWith(refPrefix)) it.substringAfter(refPrefix) else it }
    } ?: emptyList()
}

class ConflictStateOfRepository() : Exception()
@Throws(ConflictStateOfRepository::class)
fun Project.getChangesFiles(): FilesState {
    val updated = withGit { git ->
        val fileState = FilesState()

        val statusCommand = git.status()
        statusCommand.setProgressMonitor(NullProgressMonitor.INSTANCE)
        val status = statusCommand.call()
        if (status.isClean) {
            return fileState
        }

        if (status.conflicting.isNotEmpty()) {
            throw ConflictStateOfRepository()
        }

        fileState.updatedFiles.addAll(status.added)
        fileState.updatedFiles.addAll(status.changed)
        fileState.updatedFiles.addAll(status.modified)
        fileState.updatedFiles.addAll(status.untracked)

        fileState.deletedFiles.addAll(status.missing)
        fileState.deletedFiles.addAll(status.removed)

        fileState.updatedFiles.removeAll(status.ignoredNotInIndex)
        fileState.deletedFiles.removeAll(status.ignoredNotInIndex)

        fileState
    }
    return updated ?: FilesState()
}

fun Project.calculateChangedFilesState(files: Set<String> = emptySet(), forceUpdate: Boolean = false): ChangesFiles {
    val selected = getSettings().selected()
    selected ?: return ChangesFiles()

    val toDelete = LinkedHashSet<String>()
    val filesForUpdate = HashSet<String>()
    val bigFiles = LinkedHashSet<String>()
    val editedFromStart = LinkedHashSet<String>()
    val editedInThisIteration = LinkedHashSet<String>()
    val updatedFileContent: MutableMap<String, InputStream> = HashMap()

    val localChanges = getChangesFiles()
    val updateFiles = localChanges.files
    val filesToProcess = files.ifEmpty { updateFiles + selected.lastChangedFiles }

    filesToProcess.forEach {
        if (localChanges.deletedFiles.contains(it)) {
            toDelete.add(this.toRelatedPath(it))
        }

        val absolutePath = this.toAbsolutePath("/$it")
        val file = absolutePath.toVirtualFile() ?: return@forEach
        if (!file.isConfigFile(this)) {
            return@forEach
        }

        val byteArray = file.contentsToByteArray()
        val relatedPath = this.toRelatedPath(absolutePath)

        if (byteArray.isEmpty() || byteArray.size >= 1024 * 1024) {
            bigFiles.add(relatedPath)
        }

        if (updateFiles.contains(it)) {
            editedInThisIteration.add(relatedPath)
            updatedFileContent[relatedPath] = ByteArrayInputStream(byteArray)
        }

        if (selected.lastChangedFiles.contains(relatedPath)) {
            editedFromStart.add(relatedPath)
            updatedFileContent[relatedPath] = ByteArrayInputStream(byteArray)
        }

        if (forceUpdate) {
            filesForUpdate.add(relatedPath)
            updatedFileContent[relatedPath] = ByteArrayInputStream(byteArray)
        }
    }

    filesForUpdate.addAll(editedFromStart)
    filesForUpdate.addAll(editedInThisIteration)

    val changesFiles = ArrayList<String>().union(filesForUpdate).union(toDelete)
    return ChangesFiles(
        editedInThisIteration,
        editedFromStart,
        filesForUpdate,
        changesFiles,
        bigFiles,
        toDelete,
        updatedFileContent,
        forceUpdate
    )
}

fun Project.getFileContent(branchName: String, path: String): ByteArray? {
    val normalizedPath = toRelatedPath(path).substring(1)
    return repository()?.use { repository ->
        val lastCommitId = repository.resolve(branchName)
        RevWalk(repository).use { revWalk ->
            val commit = revWalk.parseCommit(lastCommitId)
            val tree = commit.tree
            val treeWalk = TreeWalk(repository)
            treeWalk.addTree(tree)
            treeWalk.isRecursive = true
            treeWalk.filter = PathFilter.create(normalizedPath)
            if (!treeWalk.next()) {
                return null
            }
            val objectId = treeWalk.getObjectId(0)
            val loader = repository.open(objectId)
            val content = loader.bytes
            revWalk.dispose()
            content
        }
    }
}

private fun Project.repository(): Repository? {
    val root = root() ?: return null
    return FileRepositoryBuilder().setGitDir(File(root.path + "/.git"))
        .readEnvironment()
        .findGitDir()
        .build();
}

private inline fun <T> Project.withGit(operation :(git: Git) -> T): T? {
    return repository()?.use {
        Git(it).use { git ->
            operation(git)
        }
    }
}

fun Project.addToGit(file: File) {
    withGit { git ->
        git.add().addFilepattern(toRelatedPath(file.normalizedPath).substring(1)).call()
    }
}
