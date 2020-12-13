package com.icthh.xm.extensions

import com.icthh.xm.service.getConfigRelatedPath
import com.icthh.xm.service.getConfigRootDir
import com.icthh.xm.utils.log
import com.intellij.ide.IconProvider
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.PlatformIcons
import net.sf.image4j.codec.ico.ICODecoder
import org.apache.commons.imaging.Imaging
import org.apache.http.client.fluent.Request.Get
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon


class ConfigIconProvider: IconProvider() {

    val logosCache = ConcurrentHashMap<String, Icon>()
    val faviconCache = ConcurrentHashMap<String, Icon>()

    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element is PsiFileSystemItem) {
            return getIcon(element.virtualFile, element.project)
        }
        return null
    }

    fun getIcon(file: VirtualFile, project: Project?): Icon? {

        if (project != null && file.isDirectory && "${project.getConfigRootDir()}/tenants".equals(file.parent?.path)) {
            val relatedPath = file.getConfigRelatedPath(project)
            val tenant = relatedPath.substring(1).split("/")[0]
            val webappPath = "${project.getConfigRootDir()}/tenants/${tenant}/webapp/settings-public.yml"
            val publicSettingsFile = File(webappPath)
            if (!publicSettingsFile.exists()) {
                return null
            }

            val lines = publicSettingsFile.readLines()
            return handeImage(file, tenant, lines, "favicon:", faviconCache)
                ?: handeImage(file, tenant, lines, "logoUrl:", logosCache)
                ?: handeImage(file, tenant, lines, "logo-url:", logosCache)
        }
        return null
    }

    private fun handeImage(
        virtualFile: VirtualFile,
        tenant: String,
        lines: List<String>,
        imageParameter: String,
        imagesCache: MutableMap<String, Icon>
    ): Icon? {
        if (imagesCache.contains(tenant)) {
            return imagesCache.get(tenant)
        }

        for (line in lines) {
            if (line.startsWith(imageParameter)) {
                val data = line.substring(imageParameter.length).trim().trim('\'', '"')
                if (data.startsWith("data:image")) {
                    val parts = data.split(',')
                    if (parts.size <= 1) {
                        break
                    }
                    val imageBytes = Base64.getDecoder().decode(parts[1]);
                    val bufferedImage = ImageIO.read(ByteArrayInputStream(imageBytes))
                    return imagesCache.getOrPut(tenant, {ImageIcon(bufferedImage.scaleToIcon())})
                } else if (data.startsWith("http://") || data.startsWith("https://")) {
                    getApplication().executeOnPooledThread{
                        uploadImage(virtualFile, tenant, data, imagesCache)
                    }
                    return null
                }
            }
        }
        imagesCache.put(tenant, PlatformIcons.PACKAGE_ICON)
        return null
    }

    private fun uploadImage(
        virtualFile: VirtualFile,
        tenant: String,
        data: String,
        imagesCache: MutableMap<String, Icon>
    ) {
        try {
            val stream = Get(data).execute()?.returnContent()?.asStream()
            stream ?: return
            val imageContent = stream.readBytes()
            val image = ImageIO.read(ByteArrayInputStream(imageContent))
            val icon = if (image == null) {
                val icons = ICODecoder.read(ByteArrayInputStream(imageContent))
                icons?.first()
            } else {
                image.scaleToIcon()
            }
            icon ?: return
            imagesCache.put(tenant, ImageIcon(icon))
            virtualFile.refresh(true, false)
        } catch (e: Exception) {
            log.warn("Error load image from url ${data}")
            log.trace(e)
            imagesCache.put(tenant, PlatformIcons.PACKAGE_ICON)
        }
    }

    fun BufferedImage.scaleToIcon(): BufferedImage {
        val size = 16
        val resized = BufferedImage(size, size, this.getType())
        val g = resized.createGraphics()
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        g.drawImage(
            this, 0, 0, size, size, 0, 0, this.getWidth(),
            this.getHeight(), null
        )
        g.dispose()
        return resized
    }
}

data class PublicSettings(
    val logoUrl: String?
)
