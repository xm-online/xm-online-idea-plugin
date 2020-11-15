package com.icthh.xm.utils

import com.intellij.util.ui.UIUtil
import com.intellij.xml.actions.xmlbeans.UIUtils
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.Icon

class FontIcon(
    var font: Font,
    val iconCode: Char,
    var iconSize: Int = 16
) : Icon {

    var iconColor = Color.BLACK
    private var buffer = renderIcon()


    private fun renderIcon(): BufferedImage {
        val b = UIUtil.createImage(
            iconWidth, iconHeight,
            BufferedImage.TYPE_INT_ARGB
        )

        val g2 = b.graphics as Graphics2D
        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )

        g2.font = font
        g2.color = iconColor ?: Color.BLACK

        val sy = g2.fontMetrics.ascent
        g2.drawString(iconCode.toString(), 0, sy)

        g2.dispose()
        return b
    }

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        g.drawImage(buffer, x, y, null)

    }

    override fun getIconHeight(): Int {
        return iconSize
    }

    override fun getIconWidth(): Int {
        return iconSize
    }

    fun updateFontSize(fontSize: Int) {
        if (font.size != fontSize) {
            iconSize = fontSize + 2
            font = font.deriveFont(fontSize.toFloat())
            buffer = renderIcon()
        }
    }

}