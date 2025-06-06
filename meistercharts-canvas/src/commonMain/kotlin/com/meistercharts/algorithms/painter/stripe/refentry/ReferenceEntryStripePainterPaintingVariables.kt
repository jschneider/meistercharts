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
package com.meistercharts.algorithms.painter.stripe.refentry

import com.meistercharts.algorithms.painter.stripe.StripePainterPaintingVariables
import com.meistercharts.history.HistoryEnumSet
import com.meistercharts.history.ReferenceEntryData
import com.meistercharts.history.ReferenceEntryDataSeriesIndex
import com.meistercharts.history.ReferenceEntryDifferentIdsCount
import com.meistercharts.history.ReferenceEntryId

/**
 * Painting variables for enums
 */
open class ReferenceEntryStripePainterPaintingVariables :
  StripePainterPaintingVariables<ReferenceEntryDataSeriesIndex, ReferenceEntryId, ReferenceEntryDifferentIdsCount, HistoryEnumSet, ReferenceEntryData?>(
    dataSeriesIndexDefault = ReferenceEntryDataSeriesIndex.zero,
    value1Default = ReferenceEntryId.NoValue,
    value2Default = ReferenceEntryDifferentIdsCount.NoValue,
    value3Default = HistoryEnumSet.NoValue,
    value4Default = null,

    ) {
  //TODO add
}
