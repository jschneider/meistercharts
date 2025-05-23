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
package com.meistercharts.charts.timeline

import com.meistercharts.algorithms.layers.HideAfterTimeoutLayer
import com.meistercharts.algorithms.layers.MouseWheelWithoutModifierMessageLayer
import com.meistercharts.algorithms.layers.axis.ValueAxisLayer
import com.meistercharts.algorithms.layers.debug.addVersionNumberHidden
import com.meistercharts.algorithms.layers.toolbar.ToolbarButtonFactory
import com.meistercharts.algorithms.layers.toolbar.ToolbarLayer
import com.meistercharts.algorithms.layers.toolbar.resetZoomAndTranslationButton
import com.meistercharts.algorithms.layers.toolbar.zoomInButton
import com.meistercharts.algorithms.layers.toolbar.zoomOutButton
import com.meistercharts.algorithms.layers.visibleIf
import com.meistercharts.canvas.ConfigurationDsl
import com.meistercharts.canvas.MeisterchartBuilder
import com.meistercharts.canvas.paintable.Button
import com.meistercharts.canvas.translateOverTime
import com.meistercharts.charts.ChartGestalt
import com.meistercharts.history.HistoryStorage
import com.meistercharts.history.InMemoryHistoryStorage
import com.meistercharts.resources.Icons
import kotlin.jvm.JvmOverloads

/**
 * Represents an interactive line chart with a time axis and a toolbar.
 *
 * Supports at most 10 visible [ValueAxisLayer]s at once.
 */
class TimeLineChartWithToolbarGestalt @JvmOverloads constructor(
  historyStorage: HistoryStorage = InMemoryHistoryStorage(),
  styleConfiguration: Style.() -> Unit = {},
  val toolbarConfiguration: ToolbarButtonFactory.() -> List<Button> = { emptyList() }
) : ChartGestalt {

  val style: Style = Style().also(styleConfiguration)

  val timeLineChartGestalt: TimeLineChartGestalt = TimeLineChartGestalt(initialHistoryStorage = historyStorage)

  val scrollWithoutModifierHintLayer: HideAfterTimeoutLayer<MouseWheelWithoutModifierMessageLayer> = MouseWheelWithoutModifierMessageLayer.create()

  override fun configure(meisterChartBuilder: MeisterchartBuilder) {
    timeLineChartGestalt.configure(meisterChartBuilder)

    meisterChartBuilder.configure {
      val buttonFactory = ToolbarButtonFactory()
      val customButtons: List<Button> = toolbarConfiguration(buttonFactory)

      val toolbarLayer = ToolbarLayer(
        listOf(
          buttonFactory.toggleButton(Icons::play, Icons::pause).also {
            it.selectedProperty.bindBidirectional(chartSupport.translateOverTime.animatedProperty)
          },
          buttonFactory.zoomInButton(),
          buttonFactory.zoomOutButton(),
          buttonFactory.resetZoomAndTranslationButton(),
        ) + customButtons
      ) {
      }

      layers.addLayer(scrollWithoutModifierHintLayer.visibleIf { style.showMouseWheelModifierHint })

      layers.addLayer(toolbarLayer.visibleIf {
        style.showToolbar
      })
      layers.addVersionNumberHidden()
    }
  }

  @ConfigurationDsl
  class Style {
    /**
     * Whether the toolbar is visible (true) or not (false)
     */
    var showToolbar: Boolean = true

    /**
     * If set to true, the hint for scrolling without modifier is shown
     */
    var showMouseWheelModifierHint: Boolean = true
  }

}
