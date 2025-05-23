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

import com.meistercharts.algorithms.layers.axis.HudElementIndex
import com.meistercharts.algorithms.layers.axis.ValueAxisHudLayer
import com.meistercharts.algorithms.layers.axis.ValueAxisLayer
import com.meistercharts.algorithms.layers.linechart.LineStyle
import com.meistercharts.annotations.Window
import com.meistercharts.annotations.Zoomed
import com.meistercharts.canvas.ConfigurationDsl
import com.meistercharts.canvas.calculateOffsetXForGap
import com.meistercharts.canvas.calculateOffsetYForGap
import com.meistercharts.canvas.layout.cache.CoordinatesCache
import com.meistercharts.canvas.layout.cache.ObjectsCache
import com.meistercharts.color.Color
import it.neckar.geometry.Direction
import it.neckar.geometry.HorizontalAlignment
import it.neckar.geometry.Side
import it.neckar.geometry.VerticalAlignment
import com.meistercharts.model.Vicinity
import it.neckar.open.collections.IterationOrder
import it.neckar.open.kotlin.lang.asProvider
import it.neckar.open.provider.CoordinatesProvider1
import it.neckar.open.provider.MultiDoublesProvider
import it.neckar.open.provider.MultiProvider
import it.neckar.open.provider.MultiProviderIndexContextAnnotation
import it.neckar.open.provider.fastForEachIndexed
import it.neckar.open.unit.number.IsFinite
import it.neckar.open.unit.number.MayBeNaN

/**
 * Paints lines from a given point into a direction (left/right/up/down)
 */
class DirectionalLinesLayer(
  val configuration: Configuration,
  additionalConfiguration: Configuration.() -> Unit = {},
) : AbstractLayer() {

  init {
    configuration.additionalConfiguration()
  }

  override val type: LayerType = LayerType.Content

  override fun paintingVariables(): DirectionalLinesLayerPaintingVariables {
    return paintingVariables
  }

  private val paintingVariables = object : DirectionalLinesLayerPaintingVariables {
    /**
     * Contains the coordinates for the HUD element
     */
    override val startCoordinatesCache = @Window @MayBeNaN CoordinatesCache()

    /**
     * The coordinates where the line ends
     */
    override val endCoordinatesCache = @Window @MayBeNaN CoordinatesCache()

    override val directionsCache = ObjectsCache(Direction.TopLeft)


    override fun calculate(paintingContext: LayerPaintingContext) {
      val chartCalculator = paintingContext.chartCalculator

      @HudElementIndex val size = configuration.locations.size(paintingContext)

      //Prepare the caches
      startCoordinatesCache.prepare(size)
      directionsCache.prepare(size)
      endCoordinatesCache.prepare(size)

      //Calculate the min/max values for all lines

      val minX: @Window Double
      val maxX: @Window Double
      val minY: @Window Double
      val maxY: @Window Double

      when (configuration.lineEndsAtMode()) {
        LineEndsAtMode.Window -> {
          minX = 0.0
          minY = 0.0
          maxX = paintingContext.width
          maxY = paintingContext.height
        }

        LineEndsAtMode.WithinContentArea -> {
          minX = chartCalculator.contentAreaRelative2windowX(0.0).coerceIn(0.0, paintingContext.width)
          minY = chartCalculator.contentAreaRelative2windowY(0.0).coerceIn(0.0, paintingContext.height)
          maxX = chartCalculator.contentAreaRelative2windowX(1.0).coerceIn(0.0, paintingContext.width)
          maxY = chartCalculator.contentAreaRelative2windowY(1.0).coerceIn(0.0, paintingContext.height)
        }

        LineEndsAtMode.WithinContentViewport -> {
          minX = chartCalculator.contentViewportMinX()
          minY = chartCalculator.contentViewportMinY()
          maxX = chartCalculator.contentViewportMaxX()
          maxY = chartCalculator.contentViewportMaxY()
        }
      }

      configuration.locations.fastForEachIndexed(paintingContext) { index: @LineIndex Int, x: @Window @MayBeNaN Double, y: @Window @MayBeNaN Double ->
        val direction = configuration.directions.valueAt(index)
        directionsCache[index] = direction

        val anchorGapX = direction.opposite().horizontalAlignment.calculateOffsetXForGap(configuration.anchorGapHorizontal.valueAt(index))
        val anchorGapY = direction.opposite().verticalAlignment.calculateOffsetYForGap(configuration.anchorGapVertical.valueAt(index))

        startCoordinatesCache.set(index, x + anchorGapX, y + anchorGapY)
        //TODO check direction with gap!

        val endX: @Window Double = when (direction.horizontalAlignment) {
          HorizontalAlignment.Left -> minX
          HorizontalAlignment.Right -> maxX
          HorizontalAlignment.Center -> x
        }

        val endY: @Window Double = when (direction.verticalAlignment) {
          VerticalAlignment.Top -> minY
          VerticalAlignment.Center -> y
          VerticalAlignment.Baseline -> y
          VerticalAlignment.Bottom -> maxY
        }

        endCoordinatesCache.set(index, endX, endY)
      }
    }
  }

  override fun paint(paintingContext: LayerPaintingContext) {
    val gc = paintingContext.gc

    paintingVariables.fastForEach { index: @LineIndex Int, startX: @IsFinite @Window Double, startY: @Window @IsFinite Double, endX: @IsFinite @Window Double, endY: @Window @IsFinite Double ->
      if (configuration.activeLineIndex == index) {
        configuration.activeLineStyles.valueAt(index).apply(gc)
      } else {
        configuration.lineStyles.valueAt(index).apply(gc)
      }

      gc.strokeLine(startX, startY, endX, endY)
    }
  }

  /**
   * Index annotation for the HUD element index
   */
  @Target(AnnotationTarget.TYPE, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.TYPE_PARAMETER, AnnotationTarget.LOCAL_VARIABLE)
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @MultiProviderIndexContextAnnotation
  annotation class LineIndex {
    companion object {
      const val None: @LineIndex Int = -1
    }
  }

  @ConfigurationDsl
  class Configuration(

    /**
     * The start of the line.
     */
    val locations: @Window @MayBeNaN CoordinatesProvider1<LayerPaintingContext>,

    /**
     * The direction into which the line is painted
     */
    var directions: MultiProvider<LineIndex, Direction> = MultiProvider.always(Direction.CenterRight),
  ) {
    /**
     * The horizontal gap between location and start of the line
     */
    var anchorGapHorizontal: @Zoomed MultiDoublesProvider<LineIndex> = MultiDoublesProvider.always(0.0)

    /**
     * The horizontal gap between location and start of the line
     */
    var anchorGapVertical: @Zoomed MultiDoublesProvider<LineIndex> = MultiDoublesProvider.always(0.0)

    /**
     * The line style for each line.
     * See [activeLineStyles] for the style if the line is active (usually mouse over)
     */
    var lineStyles: MultiProvider<LineIndex, LineStyle> = MultiProvider.always(LineStyle.LightGray)

    /**
     * The style for the active line
     */
    var activeLineStyles: MultiProvider<LineIndex, LineStyle> = MultiProvider.always(LineStyle.DarkGray)

    /**
     * Describes where the lines end
     */
    var lineEndsAtMode: () -> LineEndsAtMode = LineEndsAtMode.WithinContentViewport.asProvider()


    /**
     * The index of the active (highlighted) line.
     * Is set to -1 if there is no active line
     */
    var activeLineIndex: @LineIndex Int = -1

    /**
     * Sets the active line index if necessary. calls [callbackOnChange] only if the value has changed
     */
    inline fun setActiveLineIndex(index: @LineIndex Int, callbackOnChange: () -> Unit = {}) {
      if (activeLineIndex == index) {
        return
      }

      activeLineIndex = index
      callbackOnChange()
    }
  }

  companion object {
    /**
     * Creates a new layer that paints lines at the location of the hud
     */
    fun createForValueAxisAndHud(valueAxisLayer: ValueAxisLayer, hudLayer: ValueAxisHudLayer): DirectionalLinesLayer {
      val hudLayerPaintingProperties = hudLayer.paintingVariables()

      return DirectionalLinesLayer(
        Configuration(
          locations = hudLayerPaintingProperties.coordinatesCache.asCoordinatesProvider().as1(),
          directions = MultiProvider.invoke {
            //The anchor direction of the hud
            when (valueAxisLayer.configuration.side) {
              Side.Left -> Direction.CenterRight
              Side.Right -> Direction.CenterLeft
              Side.Top -> Direction.BottomCenter
              Side.Bottom -> Direction.TopCenter
            }
          }
        )
      ) {
        lineStyles = MultiProvider.always(LineStyle.SmallDashes)

        anchorGapHorizontal = MultiDoublesProvider { index ->
          when (valueAxisLayer.configuration.tickOrientation) {
            //the line starts at the end of the arrow head
            Vicinity.Inside -> hudLayer.configuration.arrowHeadLength.valueAt(index)
            //The line starts at the tip of the arrow head
            Vicinity.Outside -> 0.0
          }
        }

        anchorGapVertical = MultiDoublesProvider { index ->
          when (valueAxisLayer.configuration.tickOrientation) {
            //the line starts at the end of the arrow head
            Vicinity.Inside -> hudLayer.configuration.arrowHeadLength.valueAt(index)
            //The line starts at the tip of the arrow head
            Vicinity.Outside -> 0.0
          }
        }
      }
    }
  }


  /**
   * Where the line *ends*.
   *
   * Attention: The mode does *not* affect the start of the line.
   *
   * Therefore, a line might start outside the content area, enter the content area and the end at the bounds of the content area
   */
  enum class LineEndsAtMode {
    /**
     * Do not clip at all
     */
    Window,

    /**
     * Keeps the line within the content area.
     * ATTENTION: Most of the time [WithinContentViewport] is the better alternative.
     * This option is only used in some edge cases
     */
    WithinContentArea,

    /**
     * Keeps the line within the content viewport
     */
    WithinContentViewport,
  }

  interface DirectionalLinesLayerPaintingVariables : PaintingVariables {

    /**
     * Contains the coordinates for the HUD element
     */
    val startCoordinatesCache: @Window @MayBeNaN CoordinatesCache

    /**
     * The coordinates where the line ends
     */
    val endCoordinatesCache: @Window @MayBeNaN CoordinatesCache

    /**
     * The directions for the lines
     */
    val directionsCache: ObjectsCache<Direction>
  }
}

/**
 * Calls the backend for all *finite* lines.
 * Lines with infinite values are skipped
 */
inline fun DirectionalLinesLayer.DirectionalLinesLayerPaintingVariables.fastForEach(
  iterationOrder: IterationOrder,
  callback: (index: @DirectionalLinesLayer.LineIndex Int, startX: @IsFinite @Window Double, startY: @Window @IsFinite Double, endX: @IsFinite @Window Double, endY: @Window @IsFinite Double) -> Unit,
) {
  startCoordinatesCache.fastForEachIndexed(iterationOrder) { index: @DirectionalLinesLayer.LineIndex Int, startX: @MayBeNaN @Window Double, startY: @Window @MayBeNaN Double ->
    if (startX.isFinite().not() || startY.isFinite().not()) {
      //Skip if x or y are not finite
      return@fastForEachIndexed
    }

    @Window @MayBeNaN val endX = endCoordinatesCache.x(index)
    @Window @MayBeNaN val endY = endCoordinatesCache.y(index)

    if (endX.isFinite().not() || endY.isFinite().not()) {
      //Skip if x or y are not finite
      return@fastForEachIndexed
    }

    callback(index, startX, startY, endX, endY)
  }
}

inline fun DirectionalLinesLayer.DirectionalLinesLayerPaintingVariables.fastForEach(callback: (index: @DirectionalLinesLayer.LineIndex Int, startX: @IsFinite @Window Double, startY: @Window @IsFinite Double, endX: @IsFinite @Window Double, endY: @Window @IsFinite Double) -> Unit) {
  fastForEach(IterationOrder.Ascending, callback)
}

inline fun DirectionalLinesLayer.DirectionalLinesLayerPaintingVariables.fastForEachReversed(callback: (index: @DirectionalLinesLayer.LineIndex Int, startX: @IsFinite @Window Double, startY: @Window @IsFinite Double, endX: @IsFinite @Window Double, endY: @Window @IsFinite Double) -> Unit) {
  fastForEach(IterationOrder.Descending, callback)
}
