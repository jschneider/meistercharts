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

import com.meistercharts.algorithms.layers.barchart.CategoryAxisLayer
import com.meistercharts.algorithms.layers.linechart.LineStyle
import com.meistercharts.algorithms.layout.BoxIndex
import com.meistercharts.annotations.Window
import com.meistercharts.annotations.Zoomed
import com.meistercharts.canvas.ConfigurationDsl
import com.meistercharts.color.Color
import com.meistercharts.model.Insets
import it.neckar.geometry.Orientation
import it.neckar.open.provider.DoublesProvider1
import it.neckar.open.provider.MultiDoublesProvider
import it.neckar.open.provider.MultiProvider
import it.neckar.open.provider.MultiProviderIndexContextAnnotation
import it.neckar.open.provider.fastForEachIndexed
import kotlin.jvm.JvmOverloads

/**
 * Paints a grid for zoomed values
 */
class GridLayer @JvmOverloads constructor(
  val configuration: Configuration,
  dataConfiguration: Configuration.() -> Unit = {},
) : AbstractLayer() {
  init {
    configuration.also(dataConfiguration)
  }

  override val type: LayerType
    get() = LayerType.Background

  override fun paint(paintingContext: LayerPaintingContext) {
    val gc = paintingContext.gc

    //Paint all lines
    configuration.valuesProvider.fastForEachIndexed(paintingContext) { index, value: @Window Double ->
      val orientation = configuration.orientationProvider.valueAt(index)

      //Calculate the min/max values for all sides
      @Window val minX = configuration.paddingLeft.valueAt(index)
      @Window val maxX = paintingContext.width - configuration.paddingRight.valueAt(index)
      @Window val minY = configuration.paddingTop.valueAt(index)
      @Window val maxY = paintingContext.height - configuration.paddingBottom.valueAt(index)

      when (orientation) {
        //Lines painted from left to right
        Orientation.Horizontal -> {
          @Window val locationY = value
          if (locationY < minY || locationY > maxY) {
            //not visible
            return@fastForEachIndexed
          }

          configuration.lineStyles.valueAt(index).apply(gc)
          gc.strokeLine(minX, locationY, maxX, locationY)
        }

        //Lines painted from top to bottom
        Orientation.Vertical -> {
          @Window val locationX = value
          if (locationX < minX || locationX > maxX) {
            //not visible
            return@fastForEachIndexed
          }

          configuration.lineStyles.valueAt(index).apply(gc)
          gc.strokeLine(locationX, minY, locationX, maxY)
        }
      }
    }
  }

  @ConfigurationDsl
  class Configuration(
    /**
     * Returns the values where grid lines will be placed.
     * This is either the x or y value - depending on the orientation provided by the [orientationProvider]
     */
    val valuesProvider: @Window DoublesProvider1<LayerPaintingContext>,

    /**
     * Provides the orientation of the grid lines.
     *
     * - Vertical: The grid lines are painted from top to bottom
     * - Horizontal: The grid lines are painted from left to right
     */
    val orientationProvider: MultiProvider<GridLine, Orientation> = MultiProvider.always(Orientation.Vertical),
  ) {
    /**
     * The style to be used for each grid line
     */
    var lineStyles: MultiProvider<GridLine, LineStyle> = MultiProvider.always(LineStyle(color = Color.lightgray, lineWidth = 1.0))


    var paddingLeft: @Zoomed MultiDoublesProvider<GridLine> = MultiDoublesProvider.always(0.0)
    var paddingRight: @Zoomed MultiDoublesProvider<GridLine> = MultiDoublesProvider.always(0.0)
    var paddingTop: @Zoomed MultiDoublesProvider<GridLine> = MultiDoublesProvider.always(0.0)
    var paddingBottom: @Zoomed MultiDoublesProvider<GridLine> = MultiDoublesProvider.always(0.0)

    /**
     * Sets the padding from the given passpartout for *all* lines
     */
    fun applyPasspartout(passpartout: Insets) {
      paddingLeft = MultiDoublesProvider.always(passpartout.left)
      paddingRight = MultiDoublesProvider.always(passpartout.right)
      paddingTop = MultiDoublesProvider.always(passpartout.top)
      paddingBottom = MultiDoublesProvider.always(passpartout.bottom)
    }
  }

  /**
   * Grid line index provided by the values provider ([Data.valuesProvider]).
   */
  @Target(AnnotationTarget.TYPE)
  @MustBeDocumented
  @Retention(AnnotationRetention.SOURCE)
  @MultiProviderIndexContextAnnotation
  annotation class GridLine
}

/**
 * Creates a grid for a [CategoryAxisLayer]
 */
@JvmOverloads
fun CategoryAxisLayer.createGrid(dataConfiguration: GridLayer.Configuration.() -> Unit = {}): GridLayer {
  return GridLayer(
    GridLayer.Configuration(
      valuesProvider = object : DoublesProvider1<LayerPaintingContext> {
        override fun size(param1: LayerPaintingContext): Int {
          return layout.numberOfBoxes
        }

        override fun valueAt(index: @GridLayer.GridLine Int, param1: LayerPaintingContext): Double {
          val zoomedValue = layout.calculateCenter(BoxIndex(index)) ?: 0.0

          //Switch based on the orientation of the *value axis*!
          return when (configuration.orientation) {
            Orientation.Vertical -> {
              //Axis line: From top to bottom --> grid horizontal
              param1.chartCalculator.zoomed2windowY(zoomedValue)
            }

            Orientation.Horizontal -> {
              //Axis line: From left to right --> grid vertical
              param1.chartCalculator.zoomed2windowX(zoomedValue)
            }
          }
        }
      },
      orientationProvider = MultiProvider { configuration.orientation.opposite() }
    ),
    dataConfiguration = dataConfiguration
  )
}
