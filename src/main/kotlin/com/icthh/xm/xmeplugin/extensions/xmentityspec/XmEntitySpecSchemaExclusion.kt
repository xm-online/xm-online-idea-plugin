package com.icthh.xm.xmeplugin.extensions.xmentityspec

import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion

class XmEntitySpecSchemaExclusion : JsonSchemaCatalogExclusion {
    override fun isExcluded(file: VirtualFile): Boolean {
        val isFileExcluded =
            file.path.endsWith("/entity/xmentityspec.yml") || file.path.contains("/entity/xmentityspec/")
        return isFileExcluded
    }
}
