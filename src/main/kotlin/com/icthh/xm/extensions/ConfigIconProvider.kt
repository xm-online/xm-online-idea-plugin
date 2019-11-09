package com.icthh.xm.extensions

import com.icthh.xm.utils.getConfigRelatedPath
import com.icthh.xm.utils.getConfigRootDir
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import icons.PluginIcons
import org.apache.commons.imaging.Imaging.getBufferedImage
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.util.*
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.RenderingHints
import java.awt.Graphics2D




class ConfigIconProvider: IconProvider() {

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

            for (line in publicSettingsFile.readLines()) {
                if (line.startsWith("logoUrl:")) {
                    val data = line.substring("logoUrl:".length).trim().trim('\'', '"')
                    if (!data.startsWith("data:image")) {
                        break
                    }
                    val parts = data.split(',')
                    if (parts.size <= 1) {
                        break
                    }
                    val imageBytes = Base64.getDecoder().decode(parts[1]);
                    val bufferedImage = ImageIO.read(ByteArrayInputStream(imageBytes))
                    return ImageIcon(bufferedImage.scaleToIcon())
                }
            }
        }
        return null
    }

    fun BufferedImage.scaleToIcon(): BufferedImage {
        val resized = BufferedImage(24, 24, this.getType())
        val g = resized.createGraphics()
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        g.drawImage(
            this, 0, 0, 24, 24, 0, 0, this.getWidth(),
            this.getHeight(), null
        )
        g.dispose()
        return resized
    }
}

data class PublicSettings(
    val logoUrl: String?
)
