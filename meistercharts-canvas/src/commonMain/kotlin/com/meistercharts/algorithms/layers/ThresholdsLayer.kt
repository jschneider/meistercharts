/**
 * Copyright 2023 Neckar IT GmbH, Mössingen, Germany
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.meistercharts.algorithms.layers

import com.meistercharts.model.Orientation
import com.meistercharts.algorithms.layers.linechart.Dashes
import com.meistercharts.algorithms.layers.linechart.LineStyle
import com.meistercharts.algorithms.layers.text.TextPainter
import com.meistercharts.algorithms.painter.Color
import com.meistercharts.algorithms.painter.DirectLineLivePainter
import com.meistercharts.algorithms.painter.DirectLinePainter
import com.meistercharts.annotations.DomainRelative
import com.meistercharts.annotations.Window
import com.meistercharts.annotations.Zoomed
import com.meistercharts.canvas.CanvasRenderingContext
import com.meistercharts.canvas.DebugFeature
import com.meistercharts.canvas.FontDescriptorFragment
import com.meistercharts.canvas.LineSpacing
import com.meistercharts.canvas.StyleDsl
import com.meistercharts.canvas.paintMark
import com.meistercharts.canvas.saved
import com.meistercharts.model.BasePointProvider
import com.meistercharts.model.Direction
import com.meistercharts.model.DirectionBasedBasePointProvider
import com.meistercharts.model.HorizontalAlignment
import com.meistercharts.model.Insets
import com.meistercharts.model.Rectangle
import com.meistercharts.model.Size
import it.neckar.open.provider.DoublesProvider
import it.neckar.open.provider.MultiProviderIndexContextAnnotation
import it.neckar.open.provider.MultiProvider
import it.neckar.open.provider.fastForEachIndexed
import it.neckar.open.kotlin.lang.asProvider
import com.meistercharts.style.BoxStyle
import it.neckar.open.unit.other.px
import kotlin.jvm.JvmOverloads

/**
 * Visualizes thresholds (as lines with an optional label) horizontally or vertically
 *
 * ATTENTION: This layer paints the lines and labels itself.
 * A new (and maybe better?) alternative: [ValueAxisHudLayer] + [DirectionalLinesLayer]
 */
@Deprecated("Do no use anymore. Use ThresholdsSupport with new layers instead")
class ThresholdsLayer @JvmOverloads constructor(
  val data: Data,
  styleConfiguration: Style.() -> Unit = {}
) : AbstractLayer() {

  val style: Style = Style().also(styleConfiguration)

  override val type: LayerType
    get() = LayerType.Content

  private val linePainter = DirectLinePainter(snapXValues = true, snapYValues = true)
  private val textPainter = TextPainter()
  private val textBoxSizeAdjustment: ((textBox: @px Rectangle, gc: CanvasRenderingContext) -> Size) = { textBox, gc ->
    if (style.orientation != Orientation.Vertical || style.anchorDirection.horizontalAlignment != HorizontalAlignment.Left) {
      // TODO
      textBox.size
    } else {
      // The right border is drawn half to the left and half to the right of the right side
      // of the text box. Hence, we must only take half the border-width into account.
      val boxStyle = style.boxStyle()
      val halfBorderWidth = boxStyle.borderWidth / 2.0
      val borderOffset = (boxStyle.borderColor?.let { halfBorderWidth } ?: 0.0)
      val boxRight = gc.translationX + textBox.getX() + textBox.getWidth() + borderOffset
      val maxBoxRight = gc.width
      if (boxRight > maxBoxRight) { // reduce box-width
        Size(textBox.getWidth() - (boxRight - maxBoxRight), textBox.getHeight())
      } else { // keep original size
        textBox.size
      }
    }
  }

  override fun paint(paintingContext: LayerPaintingContext) {
    if (data.thresholdValues.isEmpty()) {
      return
    }

    val gc = paintingContext.gc

    if (DebugFeature.ShowBounds.enabled(paintingContext)) {
      gc.fill(Color.color(1.0, 0.7, 0.4, 0.5))
      gc.fillRect(0.0, 0.0, style.passpartout.left, gc.height)
      gc.fillRect(gc.width - style.passpartout.right, 0.0, style.passpartout.right, gc.height)
      gc.fillRect(0.0, 0.0, gc.width, style.passpartout.top)
      gc.fillRect(0.0, gc.height - style.passpartout.bottom, gc.width, style.passpartout.bottom)
    }

    data.thresholdValues.fastForEachIndexed { index, threshold ->
      require(threshold.isFinite()) { "Threshold is not finite <$threshold>" }

      val lineStyle = style.lineStyles.valueAt(index)
      val halfLineWidth = lineStyle.lineWidth * 0.5

      val thresholdLineXStart: @Window Double
      val thresholdLineXEnd: @Window Double
      val thresholdLineYStart: @Window Double
      val thresholdLineYEnd: @Window Double

      when (style.orientation) {
        Orientation.Vertical -> {
          thresholdLineXStart = style.passpartout.left + halfLineWidth
          thresholdLineXEnd = gc.width - style.passpartout.right - halfLineWidth
          thresholdLineYStart = paintingContext.chartCalculator.domainRelative2windowY(threshold)
          thresholdLineYEnd = thresholdLineYStart
        }

        Orientation.Horizontal -> {
          thresholdLineXStart = paintingContext.chartCalculator.domainRelative2windowX(threshold)
          thresholdLineXEnd = thresholdLineXStart
          thresholdLineYStart = style.passpartout.top + halfLineWidth
          thresholdLineYEnd = gc.height - style.passpartout.bottom - halfLineWidth
        }
      }

      gc.saved {
        lineStyle.apply(gc)
        linePainter.begin(gc)
        linePainter.addCoordinates(gc, thresholdLineXStart, thresholdLineYStart)
        linePainter.addCoordinates(gc, thresholdLineXEnd, thresholdLineYEnd)
        linePainter.paint(gc)
      }

      if (style.showThresholdLabel) {
        val lineBoundingBox = Rectangle.withLTRB(
          thresholdLineXStart - halfLineWidth,
          thresholdLineYStart - halfLineWidth,
          thresholdLineXEnd + halfLineWidth,
          thresholdLineYEnd + halfLineWidth
        )
        paintLabel(paintingContext, index, lineBoundingBox)
      }
    }
  }

  /**
   * Paints the label of the index-th threshold
   */
  private fun paintLabel(
    paintingContext: LayerPaintingContext,
    /**
     * The index
     */
    index: Int,
    /**
     * The bounding box of the line
     */
    lineBoundingBox: Rectangle,
  ) {
    if (data.thresholdValues.size() <= index) {
      return
    }
    val labels = data.thresholdLabels.valueAt(index)
    if (labels.isEmpty()) {
      return
    }
    val gc = paintingContext.gc
    gc.font(style.font)
    gc.saved {
      val anchorPoint = style.anchorPointProvider.calculateBasePoint(lineBoundingBox)
      gc.translate(anchorPoint.x, anchorPoint.y)

      if (DebugFeature.ShowBounds.enabled(paintingContext)) {
        gc.paintMark(color = Color.black)
      }

      textPainter.paintText(
        gc,
        labels,
        style.textColor,
        style.boxStyle(),
        style.lineSpacing,
        style.horizontalAlignment,
        style.anchorDirection,
        style.anchorGapHorizontal,
        style.anchorGapVertical,
        textBoxSizeAdjustment = textBoxSizeAdjustment
      )
    }
  }

  class Data(
    /**
     * Provides the threshold values
     */
    val thresholdValues: @DomainRelative DoublesProvider,

    /**
     * Provides the threshold labels
     */
    val thresholdLabels: MultiProvider<ThresholdValues, List<String>>,
  )

  /**
   * Marker annotation for the multi provider indices.
   *
   * Multi providers using this as IndexContext refer to the same index as [Data.thresholdValues]
   */
  @MultiProviderIndexContextAnnotation
  @Retention(AnnotationRetention.SOURCE)
  annotation class ThresholdValues {
  }

  @StyleDsl
  class Style {
    /**
     * The orientation of this layer:
     *  * [Orientation.Vertical]: the thresholds are drawn horizontally
     *  * [Orientation.Horizontal]: the thresholds are drawn vertically
     */
    var orientation: Orientation = Orientation.Vertical

    /**
     * Provides the line styles for the threshold lines
     */
    var lineStyles: MultiProvider<ThresholdValues, LineStyle> = MultiProvider.always(
      LineStyle(
        color = Color.lightgray,
        lineWidth = 1.0,
        dashes = Dashes.LargeDashes
      )
    )

    /**
     * The pass partout used for the threshold lines.
     * If set, a threshold line will not be drawn in the area of the pass partout.
     *
     * Beware that
     * * the left and right values are taken into account if [orientation] is [Orientation.Vertical] (= horizontal threshold lines)
     * * the top and bottom values are taken into account if [orientation] is [Orientation.Horizontal] (= vertical threshold lines)
     */
    var passpartout: @Zoomed Insets = Insets.onlyRight(80.0)

    /**
     * Whether to show the threshold label (if present)
     */
    var showThresholdLabel: Boolean = true

    /**
     * The color of the text of the threshold label
     */
    var textColor: Color = Color.lightgray

    /**
     * The style for the box (background fill + border stroke) of the threshold label
     */
    var boxStyle: () -> BoxStyle = BoxStyle().asProvider()

    /**
     * The font to be used for the threshold label
     */
    var font: FontDescriptorFragment = FontDescriptorFragment.empty

    /**
     * The line spacing of the threshold label
     */
    var lineSpacing: LineSpacing = LineSpacing.Single

    /**
     * Provides the anchor point of the threshold label
     */
    var anchorPointProvider: BasePointProvider = DirectionBasedBasePointProvider(Direction.CenterRight)

    /**
     * The anchor direction of the threshold label
     */
    var anchorDirection: Direction = Direction.CenterLeft

    /**
     * The gap to the anchor point of the threshold label
     */
    var anchorGapHorizontal: Double = 0.0
    var anchorGapVertical: Double = 0.0

    /**
     * The alignment of the text (within the box) of the threshold label.
     *
     * This is only relevant for multi line text.
     */
    var horizontalAlignment: HorizontalAlignment = HorizontalAlignment.Left

    /**
     * Changes the layer orientation to vertical.
     * This method modifies the threshold layer to match the new orientation
     */
    fun applyVerticalConfiguration() {
      orientation = Orientation.Vertical
      anchorPointProvider = DirectionBasedBasePointProvider(Direction.CenterRight)
      anchorDirection = Direction.CenterLeft
      anchorGapHorizontal = 0.0
      anchorGapVertical = 0.0
      horizontalAlignment = HorizontalAlignment.Left
    }

    /**
     * Changes the layer orientation to horizontal.
     * This method modifies the threshold layer to match the new orientation
     */
    fun applyHorizontalConfiguration() {
      orientation = Orientation.Horizontal
      anchorPointProvider = DirectionBasedBasePointProvider(Direction.TopCenter)
      anchorDirection = Direction.BottomCenter
      anchorGapHorizontal = 5.0
      anchorGapVertical = 5.0
      horizontalAlignment = HorizontalAlignment.Center
    }

    /**
     * Horizontal(!)! lines.
     * The labels are placed on top of the threshold lines
     */
    fun applyVerticalConfigurationLabelsOnTop() {
      applyVerticalConfiguration()

      passpartout = Insets.empty
      anchorPointProvider = DirectionBasedBasePointProvider(Direction.TopRight)
      anchorDirection = Direction.BottomRight
    }

  }
}
