// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.GraphicsUtil
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.geom.Area
import java.awt.geom.RoundRectangle2D
import javax.swing.JPanel
import javax.swing.border.Border
import kotlin.math.max

class RoundedPanel private constructor(unscaledRadius: Int = RADIUS) : JPanel(BorderLayout()) {
  companion object {
    const val RADIUS = 20
    const val THICKNESS = 1
    const val ACTIVE_THICKNESS = 2
    val SELECTED_BORDER_COLOR = JBColor(0x3574F0, 0x3574F0)
    val BORDER_COLOR = JBColor(0xD3D5DB, 0x43454A)

    fun createRoundedPane(): RoundedPanel {
      return RoundedPanel(RADIUS)
    }
  }

  val contentPanel: JPanel = RoundedJPanel(unscaledRadius)

  init {
    add(contentPanel, BorderLayout.CENTER)
  }

  private class RoundedJPanel(radius: Int) : JPanel() {

    private val radius = JBUI.scale(radius)

    override fun paintComponent(g: Graphics) {
      val g2d = g as Graphics2D
      val config = GraphicsUtil.setupAAPainting(g)
      g2d.clip(RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), radius.toDouble(), radius.toDouble()))
      super.paintComponent(g2d)

      config.restore()
    }
  }
}

open class RoundedBorder(unscaledAreaThickness: Int,
                         unscaledThickness: Int,
                         private val color: Color,
                         private val backgroundColor: () -> Color,
                         unscaledRadius: Int
) : Border {
  private val areaThickness = JBUI.scale(unscaledAreaThickness)
  private val thickness = JBUI.scale(unscaledThickness)
  private val arcSize = JBUI.scale(unscaledRadius)

  override fun paintBorder(c: Component, g: Graphics?, x: Int, y: Int, width: Int, height: Int) {
    g as Graphics2D

    val config = GraphicsUtil.setupAAPainting(g)
    val area = createArea(x, y, width, height, arcSize, 0.0)

    val bkgArea = createArea(x, y, width, height, arcSize, thickness.toDouble()/2)

    g.color = backgroundColor()
    g.fill(bkgArea)


    g.color = color
    val innerArea = createArea(x, y, width, height, arcSize, thickness.toDouble())

    area.subtract(innerArea)
    g.fill(area)

    config.restore()
  }

  private fun createArea(x: Int, y: Int, width: Int, height: Int, arcSize: Int, th: Double): Area {
    val innerArc = max((arcSize - th), 0.0).toInt()
    return Area(RoundRectangle2D.Double((x + th), (y + th),
                                   (width - 2 * th), (height - 2 * th),
                                   innerArc.toDouble(), innerArc.toDouble()))
  }

  override fun getBorderInsets(c: Component?): Insets {
    return JBUI.insets(areaThickness)
  }

  override fun isBorderOpaque(): Boolean {
    return false
  }
}