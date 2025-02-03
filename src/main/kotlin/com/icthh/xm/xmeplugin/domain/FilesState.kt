package com.icthh.xm.xmeplugin.domain

data class FilesState(
    val updatedFiles: MutableSet<String> = mutableSetOf(),
    val deletedFiles: MutableSet<String> = mutableSetOf(),
) {
    val files: Set<String> get() = updatedFiles + deletedFiles
}
