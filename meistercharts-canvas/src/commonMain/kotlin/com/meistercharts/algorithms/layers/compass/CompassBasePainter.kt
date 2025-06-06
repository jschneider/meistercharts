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
package com.meistercharts.algorithms.layers.compass

import com.meistercharts.algorithms.layers.LayerPaintingContext
import com.meistercharts.annotations.Domain
import com.meistercharts.axis.AxisEndConfiguration
import com.meistercharts.axis.IntermediateValuesMode
import com.meistercharts.axis.LinearAxisTickCalculator
import com.meistercharts.axis.LinearAxisTickCalculator.calculateTickValues
import com.meistercharts.calc.domain2rad
import com.meistercharts.canvas.ArcType
import com.meistercharts.canvas.ConfigurationDsl
import com.meistercharts.font.FontDescriptorFragment
import com.meistercharts.color.Color
import it.neckar.geometry.Coordinates
import it.neckar.geometry.Direction
import it.neckar.geometry.PolarCoordinates
import com.meistercharts.range.ValueRange
import it.neckar.open.formatting.CachedNumberFormat
import it.neckar.open.formatting.decimalFormat
import it.neckar.open.unit.si.rad
import kotlin.math.PI

/**
 * Paints a class compass base
 */
class CompassBasePainter(
  styleConfiguration: Style.() -> Unit = {}
) : GaugeBasePainter {

  val style: Style = Style().also(styleConfiguration)

  override fun paintBase(gaugePaintable: GaugePaintable, paintingContext: LayerPaintingContext, radius: Double, startAngle: Double, extendWithRotationDirection: Double, valueRange: ValueRange) {
    val gc = paintingContext.gc

    gc.fill(style.backgroundColor)
    gc.fillArcCenter(0.0, 0.0, radius, startAngle, extendWithRotationDirection, ArcType.Round)

    gc.stroke(style.compassColor)
    gc.strokeArcCenter(0.0, 0.0, radius, startAngle, extendWithRotationDirection, ArcType.Open)

    // paint inner circles
    gc.stroke(style.tickColor)
    for (circle in 1..style.numberInnerCircles) {
      gc.strokeArcCenter(0.0, 0.0, (radius / (style.numberInnerCircles + 1)) * circle, startAngle, extendWithRotationDirection, ArcType.Open)
    }

    // paint ticks
    gc.fill(style.labelsColor)
    gc.font(style.font)

    style.ticksProvider.calculateTicks(valueRange).forEach { tickValue: Double ->
      @rad val tickAngle = domain2rad(tickValue, valueRange, startAngle, extendWithRotationDirection)
      gc.strokeLine(
        Coordinates.origin.x,
        Coordinates.origin.y,
        PolarCoordinates.toCartesianX(radius, tickAngle),
        PolarCoordinates.toCartesianY(radius, tickAngle)
      )

      // paint tick label
      gc.fillText(
        style.valueFormat.format(tickValue),
        PolarCoordinates.toCartesianX(radius + style.labelsGap, tickAngle),
        PolarCoordinates.toCartesianY(radius + style.labelsGap, tickAngle),
        Direction.Center
      )
    }

    // check whether to draw start and end lines
    if (gaugePaintable.style.extend != PI * 2) {
      gc.stroke(style.compassColor)
      gc.strokeLine(
        Coordinates.origin.x,
        Coordinates.origin.y,
        PolarCoordinates.toCartesianX(radius, startAngle),
        PolarCoordinates.toCartesianY(radius, startAngle)
      )
      gc.strokeLine(
        Coordinates.origin.x,
        Coordinates.origin.y,
        PolarCoordinates.toCartesianX(radius, startAngle + extendWithRotationDirection),
        PolarCoordinates.toCartesianY(radius, startAngle + extendWithRotationDirection)
      )
    }
  }

  @ConfigurationDsl
  class Style {
    /**
     * Provides the ticks
     */
    val ticksProvider: GaugeTicksProvider = AutoGaugeTicksProvider()

    /**
     * The color to paint the background
     */
    var backgroundColor: Color = Color.color(1.0, 1.0, 1.0, 0.0)

    /**
     * The color to paint the compass frame
     */
    var compassColor: Color = Color.black

    /**
     * The color to paint the ticks
     */
    var tickColor: Color = Color.lightgray

    /**
     * The color to paint the tick labels
     */
    var labelsColor: Color = Color.blue

    /**
     * The gap between the compass rose and the labels
     */
    var labelsGap: Double = 25.0

    /**
     * Number of inner circles to draw
     */
    var numberInnerCircles: Int = 3

    /**
     * Font used for values
     */
    var font: FontDescriptorFragment = FontDescriptorFragment.empty

    /**
     * Format for formatting values
     */
    var valueFormat: CachedNumberFormat = decimalFormat

  }
}

/**
 * Calculates ticks for the gauge
 */
fun interface GaugeTicksProvider {
  fun calculateTicks(valueRange: ValueRange): @Domain DoubleArray
}

/**
 * Calculates ticks using the [LinearAxisTickCalculator]]
 */
class AutoGaugeTicksProvider(val tickCount: Int = 20) : GaugeTicksProvider {
  override fun calculateTicks(valueRange: ValueRange): @Domain DoubleArray {
    return calculateTickValues(valueRange.start, valueRange.end, AxisEndConfiguration.Default, tickCount, 0.0, IntermediateValuesMode.Also5and2)
  }
}
