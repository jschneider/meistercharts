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
@file:Suppress("NOTHING_TO_INLINE")

package com.meistercharts.history

import it.neckar.open.provider.MultiProvider


/**
 * Interface that is implemented by concrete series index value classes
 */
interface DataSeriesIndex {
  /**
   * The int value representation of the data series index
   */
  val value: Int
}


inline fun <DataSeriesIndexType : DataSeriesIndex, T> MultiProvider<DataSeriesIndexType, T>.valueAt(index: DataSeriesIndexType): T {
  return this.valueAt(index.value)
}
