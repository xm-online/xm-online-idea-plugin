package com.icthh.xm.utils

import com.intellij.util.ui.UIUtil
import com.intellij.xml.actions.xmlbeans.UIUtils
import java.awt.*
import java.awt.image.BufferedImage
import javax.swing.Icon

class FontIcon(
    val font: Font,
    val iconCode: Char,
    var iconSize: Int = 16
) : Icon {

    private var buffer = lazy {
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
        g2.color = iconColor

        val sy = g2.fontMetrics.ascent
        g2.drawString(iconCode.toString(), 0, sy)

        g2.dispose()

        return@lazy b
    }

    var iconColor = Color.BLACK

    override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
        g.drawImage(buffer.value, x, y, null)

    }

    override fun getIconHeight(): Int {
        return iconSize
    }

    override fun getIconWidth(): Int {
        return iconSize
    }

}