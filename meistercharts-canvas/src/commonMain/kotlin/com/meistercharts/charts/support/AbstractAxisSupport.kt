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
package com.meistercharts.charts.support

import com.meistercharts.algorithms.layers.AxisTitleLocation
import com.meistercharts.algorithms.layers.AxisTopTopTitleLayer
import com.meistercharts.algorithms.layers.MultipleLayersDelegatingLayer
import com.meistercharts.algorithms.layers.barchart.AbstractAxisLayer
import com.meistercharts.algorithms.layers.visibleIf
import com.meistercharts.canvas.ChartSupport
import com.meistercharts.canvas.layer.LayerSupport
import com.meistercharts.font.FontMetrics
import it.neckar.open.collections.Cache
import it.neckar.open.collections.cache
import it.neckar.open.provider.BooleanProvider
import it.neckar.open.provider.asSizedProvider
import it.neckar.open.unit.other.px

/**
 * Base class for supports for axis and top title axis
 */
abstract class AbstractAxisSupport<Key, AxisLayer : AbstractAxisLayer> {

  /**
   * Contains value axis layers
   */
  protected val axisLayersCache: Cache<Key, AxisLayer> = cache("axisLayersCache", 100)

  /**
   * Executes the given callback for all value axis layers
   */
  fun foreachAxisLayer(callback: (Key, AxisLayer) -> Unit) {
    axisLayersCache.forEach(callback)
  }

  /**
   * Returns the value axis layer - creates a new instance if necessary
   */
  fun getAxisLayer(key: Key): AxisLayer {
    return axisLayersCache.getOrStore(key) {
      createAxisLayer(key).also { layer ->
        layer.configuration.titleVisible = BooleanProvider { isTitleAtTopComputed(key).not() }
      }
    }
  }

  /**
   * Creates a new instance of axis layer
   */
  protected abstract fun createAxisLayer(key: Key): AxisLayer

  protected val topTitleLayersCache: Cache<Key, AxisTopTopTitleLayer> = cache("valueAxisTopTitleLayersCache", 100)

  fun foreachTopTitleLayer(callback: (Key, AxisTopTopTitleLayer) -> Unit) {
    topTitleLayersCache.forEach(callback)
  }

  /**
   * Returns the top title layer for the provided key.
   * This method automatically creates the corresponding value axis title if necessary
   */
  fun getTopTitleLayer(key: Key): AxisTopTopTitleLayer {
    return topTitleLayersCache.getOrStore(key) {
      createTopTitleLayer(key)
    }
  }

  /**
   * Creates a new instance of a top title layer
   */
  protected abstract fun createTopTitleLayer(key: Key): AxisTopTopTitleLayer

  /**
   * Returns true if the title is shown at top, false otherwise.
   */
  fun isTitleAtTopComputed(key: Key): Boolean {
    return getComputedAxisTitleLocation(key) == AxisTitleLocation.AtTop
  }

  /**
   * Returns the computed axis title location for the given key
   *
   * This method takes the [preferredAxisTitleLocation] and side of the axis into consideration.
   */
  fun getComputedAxisTitleLocation(key: Key): AxisTitleLocation {
    return when {
      preferredAxisTitleLocation == AxisTitleLocation.AtValueAxis -> {
        AxisTitleLocation.AtValueAxis
      }

      getAxisLayer(key).configuration.side.isLeftOrRight() -> {
        AxisTitleLocation.AtTop
      }

      else -> {
        //Fallback - AtTop is only supported for left/right axis
        AxisTitleLocation.AtValueAxis
      }
    }
  }

  /**
   * Adds all layers for the provided key
   */
  fun addLayers(layerSupport: LayerSupport, key: Key, visibleCondition: (() -> Boolean)? = null) {
    layerSupport.layers.addLayer(getAxisLayer(key).visibleIf(false, visibleCondition))
    layerSupport.layers.addLayer(getTopTitleLayer(key).visibleIf { isTitleAtTopComputed(key) })
  }

  /**
   * Creates the multi top title layer
   */
  fun createMultiTopTitleLayer(keys: Iterable<Key>): MultipleLayersDelegatingLayer<AxisTopTopTitleLayer> {
    val multiTopTitleLayer = MultipleLayersDelegatingLayer(
      keys.map {
        getTopTitleLayer(it)
      }.asSizedProvider()
    )
    return multiTopTitleLayer
  }

  /**
   * The preferred location of the value axis title location.
   */
  var preferredAxisTitleLocation: AxisTitleLocation = AxisTitleLocation.AtValueAxis


  /**
   * Calculates the content viewport margin top.
   * Depending on the [getComputedAxisTitleLocation] and font size.
   *
   * This method checks the current title
   */
  fun calculateContentViewportMarginTop(key: Key, chartSupport: ChartSupport): @px Double {
    val axisLayer = getAxisLayer(key)
    if (axisLayer.configuration.side.isTopOrBottom()) {
      //No margin required if the layer is at the top or bottom
      return 0.0
    }

    //We know the layer is placed at left or right
    if (axisLayer.configuration.hasNonBlankTitle(chartSupport).not()) {
      //No title is set
      return axisLayer.configuration.calculatePreferredViewportMarginTop()
    }

    return when (getComputedAxisTitleLocation(key)) {
      AxisTitleLocation.AtValueAxis -> {
        //the title is painted at the axis itself. We need enough space for the ticks
        axisLayer.configuration.calculatePreferredViewportMarginTop()
      }

      AxisTitleLocation.AtTop -> {
        val topTitleLayer = getTopTitleLayer(key)
        val titleFontHeight = FontMetrics[topTitleLayer.configuration.titleFont.withDefaultValues()].totalHeight
        return (titleFontHeight + topTitleLayer.configuration.titleGapVertical)
      }
    }
  }
}

/**
 * Returns the computed axis title location
 */
fun <AxisLayer : AbstractAxisLayer> AbstractAxisSupport<Unit, AxisLayer>.getComputedAxisTitleLocation(): AxisTitleLocation {
  return this.getComputedAxisTitleLocation(Unit)
}

fun <AxisLayer : AbstractAxisLayer> AbstractAxisSupport<Unit, AxisLayer>.isTitleAtTopComputed(): Boolean {
  return isTitleAtTopComputed(Unit)
}


typealias ValueAxisTopTopTitleLayerConfiguration<Key> = AxisTopTopTitleLayer.Configuration.(Key, axis: AxisTopTopTitleLayer) -> Unit
