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
package com.meistercharts.demo

import com.meistercharts.algorithms.ValueRange
import com.meistercharts.animation.Easing
import com.meistercharts.history.generator.DecimalValueGenerator
import it.neckar.open.kotlin.lang.ifNaN
import it.neckar.open.kotlin.lang.random
import it.neckar.open.unit.number.Positive
import it.neckar.open.unit.si.ms
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/**
 * Builds a [DecimalValueGenerator] that takes a timestamp as an argument
 */
class TimeBasedValueGeneratorBuilder(config: TimeBasedValueGeneratorBuilder.() -> Unit) {

  /**
   * The range in which the generated values must lie
   */
  var valueRange: ValueRange = ValueRange.default

  /**
   * The value to start with.
   * Returns the center of the value range - if no value has been set manually
   */
  var startValue: Double = Double.NaN
    get() {
      return field.ifNaN { valueRange.center() }
    }

  /**
   * The minimum difference between the last generated value of one [period] and the next [period]
   */
  var minDeviation: @Positive Double = Double.NaN
    get() {
      return field.ifNaN { valueRange.delta * 0.05 }
    }

  /**
   * The maximum difference between the last generated value of one [period] and the next [period]
   */
  var maxDeviation: @Positive Double = Double.NaN
    get() {
      return field.ifNaN { valueRange.delta * 0.15 }
    }

  /**
   * The easing to be used to generate all values during a [period]
   */
  var easing: Easing = Easing.inOut

  /**
   * The time that should pass between the previously generated value and the next target value
   */
  var period: @ms @Positive Double = 10.seconds.toDouble(DurationUnit.MILLISECONDS)

  init {
    this.config()
  }

  private var built: Boolean = false

  /**
   * Call this function to create a [DecimalValueGenerator] that takes a timestamp as an argument
   *
   * Beware: this function may only be called once!
   */
  fun build(): DecimalValueGenerator {
    check(!built) { "build() may only called once" }
    built = true

    require(minDeviation >= 0) { "$minDeviation < 0" }
    require(maxDeviation > 0) { "$maxDeviation <= 0" }
    require(minDeviation < maxDeviation) { "$minDeviation >= $maxDeviation" }
    require(period > 0) { "period must be greater than 0 but was <$period>" }
    require(valueRange.contains(startValue)) { "value-range $valueRange does not contain start value $startValue" }

    return object : DecimalValueGenerator {
      private var periodStartValue = startValue
      private var periodStartTimestamp: @ms Double = 0.0
      private var periodEndValue: Double = periodStartValue
      private var periodEndTimestamp: @ms Double = 0.0

      override fun generate(timestamp: @ms Double): Double {
        check(valueRange.contains(periodStartValue)) { "$valueRange does not contain period start value $periodStartValue" }
        check(valueRange.contains(periodEndValue)) { "$valueRange does not contain period end value $periodEndValue" }

        if (timestamp > periodEndTimestamp) {
          //start of a new period -> compute its desired end-value
          periodStartTimestamp = timestamp
          periodEndTimestamp = timestamp + period
          periodStartValue = periodEndValue
          var loopCounter = 0
          do {
            ++loopCounter
            val sign = when {
              periodEndValue <= valueRange.start -> 1.0 //periodEndValue must get larger so the sign must be positive
              periodEndValue >= valueRange.end -> -1.0 //periodEndValue must get smaller so the sign must be negative
              else -> if (random.nextBoolean()) -1.0 else 1.0
            }
            periodEndValue += sign * (random.nextDouble(minDeviation, maxDeviation))
          } while (loopCounter < 5 && !valueRange.contains(periodEndValue))
          periodEndValue = periodEndValue.coerceAtMost(valueRange.end)
          periodEndValue = periodEndValue.coerceAtLeast(valueRange.start)
        }
        return periodStartValue + easing((timestamp - periodStartTimestamp) / period) * (periodEndValue - periodStartValue)
      }
    }
  }
}

fun timeBasedValueGenerator(config: TimeBasedValueGeneratorBuilder.() -> Unit): DecimalValueGenerator {
  return TimeBasedValueGeneratorBuilder(config).build()
}

