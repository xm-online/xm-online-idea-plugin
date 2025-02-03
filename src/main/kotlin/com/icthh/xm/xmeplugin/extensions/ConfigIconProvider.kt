package com.icthh.xm.xmeplugin.extensions

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.SVGRenderingHints
import com.github.weisj.jsvg.parser.DefaultParserProvider
import com.github.weisj.jsvg.parser.LoaderContext
import com.github.weisj.jsvg.parser.SVGLoader
import com.icthh.xm.xmeplugin.utils.doAsync
import com.icthh.xm.xmeplugin.utils.getConfigRootDir
import com.icthh.xm.xmeplugin.utils.log
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.ImageUtil
import getTenantName
import httpGet
import net.sf.image4j.codec.ico.ICODecoder
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLDecoder
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import kotlin.text.Charsets.UTF_8


class ConfigIconProvider: IconProvider() {

    data class TenantIcon(
        val modificationTime: Long,
        val icon: Icon
    )
    val iconCache = ConcurrentHashMap<String, TenantIcon>()

    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element is PsiFileSystemItem && element.virtualFile != null) {
            return getIcon(element.virtualFile, element.project)
        }
        return null
    }

    fun getIcon(file: VirtualFile, project: Project?): Icon? {

        if (project != null && file.isDirectory && "${project.getConfigRootDir()}/tenants".equals(file.parent?.path)) {
            val tenant = file.getTenantName(project)
            val webappPath = "${project.getConfigRootDir()}/tenants/${tenant}/webapp/settings-public.yml"
            val publicSettingsFile = File(webappPath)
            if (!publicSettingsFile.exists()) {
                return null
            }

            val modificationCount = publicSettingsFile.lastModified()
            val cachedIcon = iconCache[tenant]
            if (cachedIcon != null && cachedIcon.modificationTime == modificationCount) {
                return cachedIcon.icon
            }

            try {
                val lines = publicSettingsFile.readLines()
                val image = project.handeImage(file, tenant, lines, "logoUrl:", publicSettingsFile.lastModified())
                    ?: project.handeImage(file, tenant, lines, "favicon:", publicSettingsFile.lastModified())
                    ?: project.handeImage(file, tenant, lines, "logo-url:", publicSettingsFile.lastModified())

                if (image == null) {
                    iconCache.put(tenant, TenantIcon(modificationCount, PlatformIcons.PACKAGE_ICON))
                } else {
                    iconCache.put(tenant, TenantIcon(modificationCount, image))
                }
                return image
            } catch (e: Exception) {
                log.warn("Error read public settings file ${publicSettingsFile}")
                log.trace(e)
            }
        }
        return null
    }

    private fun Project.handeImage(
        virtualFile: VirtualFile,
        tenant: String,
        lines: List<String>,
        imageParameter: String,
        lastModified: Long
    ): Icon? {
        try {
            for (line in lines) {
                if (line.startsWith(imageParameter)) {
                    val data = line.substring(imageParameter.length).trim().trim('\'', '"')
                    if (data.startsWith("data:image") && data.contains("svg+xml")) {
                        val parts = data.split(',')
                        if (parts.size <= 1) {
                            break
                        }

                        val target = String(decode(data, parts), UTF_8)
                        return loadSvgAsIcon(target)
                    } else if (data.startsWith("data:image") && data.contains(";base64,")) {
                        val parts = data.split(',')
                        if (parts.size <= 1) {
                            break
                        }
                        val imageBytes = decode(data, parts)
                        val bufferedImage = ImageIO.read(ByteArrayInputStream(imageBytes))
                        return ImageIcon(bufferedImage.scaleToIcon())
                    } else if (data.startsWith("http://") || data.startsWith("https://")) {
                        doAsync {
                            uploadImage(virtualFile, tenant, data, lastModified)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.warn("Error handle image for tenant ${tenant}")
            log.trace(e)
        }

        return null
    }

    private fun decode(data: String, parts: List<String>): ByteArray {
        return if (data.contains("base64")) {
            Base64.getDecoder().decode(parts[1])
        } else if (data.contains("%")) {
            URLDecoder.decode(parts[1], UTF_8).toByteArray()
        } else {
            parts[1].toByteArray()
        }
    }

    fun loadSvgAsIcon(svgContent: String): ImageIcon {
        val loader = SVGLoader()
        val svgDocument: SVGDocument? = loader.load(svgContent.byteInputStream(UTF_8), null, LoaderContext.builder()
            .parserProvider(DefaultParserProvider())
            .build())

        val size = svgDocument!!.size()
        val image = ImageUtil.createImage(size.width.toInt(), size.height.toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_BILINEAR
        )
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(SVGRenderingHints.KEY_SOFT_CLIPPING, SVGRenderingHints.VALUE_SOFT_CLIPPING_ON);

        svgDocument.render(null, g)
        g.dispose()
        return ImageIcon(image.scaleToIcon())
    }

    private fun uploadImage(
        virtualFile: VirtualFile,
        tenant: String,
        data: String,
        lastModified: Long
    ) {
        try {
            val imageContent = data.httpGet()
            imageContent ?: return
            if (data.endsWith(".svg")) {
                val target = String(imageContent, UTF_8)
                val icon = loadSvgAsIcon(target)
                iconCache[tenant] = TenantIcon(lastModified, icon)
            } else {
                val image = ImageIO.read(ByteArrayInputStream(imageContent))
                val icon = if (image == null) {
                    val icons = ICODecoder.read(ByteArrayInputStream(imageContent))
                    icons?.first()
                } else {
                    image.scaleToIcon()
                }
                icon ?: return
                iconCache.put(tenant, TenantIcon(lastModified, ImageIcon(icon)))
            }
            virtualFile.refresh(true, false)
        } catch (e: Exception) {
            log.warn("Error load image from url ${data} $e")
            log.trace(e)
        }
    }

    fun BufferedImage.scaleToIcon(): BufferedImage {
        val size = 16
        val resized = ImageUtil.createImage(size, size, this.getType())
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

