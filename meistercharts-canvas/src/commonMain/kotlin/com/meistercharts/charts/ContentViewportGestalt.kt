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
package com.meistercharts.charts

import com.meistercharts.algorithms.impl.FittingInContentViewport
import com.meistercharts.annotations.Zoomed
import com.meistercharts.canvas.MeisterChartBuilder
import com.meistercharts.model.Insets
import it.neckar.logging.Logger
import it.neckar.logging.LoggerFactory
import it.neckar.open.observable.ObservableObject

/**
 * Configures the content viewport using margins
 */
open class ContentViewportGestalt(contentViewportMargin: @Zoomed Insets) : ChartGestalt {
  /**
   * The current content viewport margin
   */
  val contentViewportMarginProperty: ObservableObject<@Zoomed Insets> = ObservableObject(contentViewportMargin).also {
    it.consumeImmediately { margin ->
      logger.debug("contentViewportMargin changed to $margin")
    }
  }

  var contentViewportMargin: @Zoomed Insets by contentViewportMarginProperty

  @ChartGestaltConfiguration
  override fun configure(meisterChartBuilder: MeisterChartBuilder) {
    meisterChartBuilder.apply {
      configure {
        contentViewportMarginProperty.consumeImmediately {
          chartSupport.rootChartState.contentViewportMargin = it
          chartSupport.zoomAndTranslationSupport.resetToDefaults()
        }
      }

      zoomAndTranslationDefaults = FittingInContentViewport
    }
  }

  inline fun setMarginTop(newTop: @Zoomed Double) {
    contentViewportMargin = contentViewportMargin.withTop(newTop)
  }

  inline fun setMarginLeft(newLeft: @Zoomed Double) {
    contentViewportMargin = contentViewportMargin.withLeft(newLeft)
  }

  inline fun setMarginBottom(newBottom: @Zoomed Double) {
    contentViewportMargin = contentViewportMargin.withBottom(newBottom)
  }

  inline fun setMarginRight(newRight: @Zoomed Double) {
    contentViewportMargin = contentViewportMargin.withRight(newRight)
  }

  companion object {
    private val logger: Logger = LoggerFactory.getLogger("com.meistercharts.charts.ContentViewportGestalt")
  }
}
