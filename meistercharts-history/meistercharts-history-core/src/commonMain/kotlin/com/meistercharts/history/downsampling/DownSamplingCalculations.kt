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
package com.meistercharts.history.downsampling

import com.meistercharts.history.HistoryBucket
import com.meistercharts.history.HistoryBucketDescriptor
import com.meistercharts.history.MayBeNoValueOrPending
import com.meistercharts.history.ReferenceEntryData
import com.meistercharts.history.ReferenceEntryDataSeriesIndex
import com.meistercharts.history.ReferenceEntryId
import com.meistercharts.history.ReferenceEntryIdInt
import com.meistercharts.history.TimestampIndex
import com.meistercharts.history.impl.HistoryChunk
import com.meistercharts.history.impl.HistoryValuesBuilder
import com.meistercharts.history.impl.RecordingType
import it.neckar.open.collections.IntArray2
import it.neckar.open.collections.fastForEach
import it.neckar.open.collections.fastForEachIndexed
import it.neckar.open.formatting.formatUtc
import it.neckar.open.kotlin.lang.toIntCeil
import it.neckar.open.provider.SizedProvider
import it.neckar.open.provider.fastForEach
import it.neckar.open.provider.isNotEmpty
import it.neckar.open.unit.other.Sorted
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt


/**
 * Creates a new down sampled bucket for the given children.
 *
 * This is the target descriptor.
 */
fun HistoryBucketDescriptor.calculateDownSampled(
  /**
   * The buckets that are used to calculate the down sampled bucket
   */
  childBuckets: @Sorted List<HistoryBucket>,
): HistoryBucket {
  require(childBuckets.isNotEmpty()) { "At least one bucket required for down sampling" }

  //check the child buckets are ordered
  childBuckets.ensureSorted()

  //Check the descriptors
  childBuckets.fastForEach {
    require(it.descriptor.parent() == this)

    require(it.chunk.firstTimestamp >= this.start)
    require(it.chunk.lastTimestamp < this.end)
  }

  return calculateDownSampled(SizedProvider.mapped(childBuckets) {
    it.chunk
  })
}

/**
 * Creates a new down sampled bucket for the given children.
 *
 * This is the target descriptor.
 */
fun HistoryBucketDescriptor.calculateDownSampled(
  /**
   * Provides the child chunks
   */
  childChunks: @Sorted SizedProvider<HistoryChunk>,
): HistoryBucket {
  require(childChunks.isNotEmpty()) { "At least one bucket required for down sampling" }


  require(childChunks.first().firstTimestamp >= this.start) {
    "Invalid child bucket start <${childChunks.first().firstTimestamp.formatUtc()}> while descriptor start is <${this.start.formatUtc()}> for bucket range <${this.bucketRange}>"
  }
  require(childChunks.last().lastTimestamp <= this.end) {
    "Invalid child bucket end <${childChunks.last().lastTimestamp.formatUtc()}> while descriptor end is <${this.end.formatUtc()}> for bucket range <${this.bucketRange}>"
  }

  val firstChunk = childChunks.first()

  //Contains all possible timestamps for the (new) down sampled bucket
  val timestampsIterator = DownSamplingTargetTimestampsIterator.create(this)

  //Calculates averages, min, max
  val downSamplingCalculator = DownSamplingCalculator(firstChunk.decimalDataSeriesCount, firstChunk.enumDataSeriesCount, firstChunk.referenceEntryDataSeriesCount)

  //The target array
  val downSampledValuesBuilder = HistoryValuesBuilder(
    decimalDataSeriesCount = firstChunk.decimalDataSeriesCount,
    enumDataSeriesCount = firstChunk.enumDataSeriesCount,
    referenceEntryDataSeriesCount = firstChunk.referenceEntryDataSeriesCount,

    initialTimestampsCount = this.bucketRange.entriesCount,
    recordingType = RecordingType.Calculated
  )

  //Now collect the information for each bucket
  childChunks.fastForEach { childChunk ->
    childChunk.timeStamps.fastForEachIndexed { indexAsInt, timestamp ->
      val index = TimestampIndex(indexAsInt)

      //Find the next slot for the time stamp
      while (timestamp >= timestampsIterator.slotEnd) {
        //We reached the end of the current slot

        //Save the calculated values
        @ReferenceEntryIdInt val downSampledReferenceEntryIds = downSamplingCalculator.referenceEntryIds()
        downSampledValuesBuilder.setAllValuesForTimestamp(
          timestampIndex = timestampsIterator.index,
          decimalValues = downSamplingCalculator.averageValues(),
          minValues = downSamplingCalculator.minValues(),
          maxValues = downSamplingCalculator.maxValues(),
          enumValues = downSamplingCalculator.enumUnionValues(),
          enumOrdinalsMostTime = downSamplingCalculator.enumMostTimeOrdinalValues(),
          referenceEntryIds = downSampledReferenceEntryIds,
          referenceEntryStatuses = downSamplingCalculator.referenceEntryStatuses(),
          referenceEntryDifferentIdsCount = downSamplingCalculator.referenceEntryDifferentIdsCount(),
          entryDataSet = childChunks.collectReferenceEntryData(downSampledReferenceEntryIds),
        )

        //Reset the calculator - a new average is calculated
        downSamplingCalculator.reset()
        timestampsIterator.next()
      }

      //Add the sample to the calculator
      downSamplingCalculator.addDecimalsSample(childChunk.getDecimalValues(index))
      downSamplingCalculator.addEnumSample(childChunk.getEnumValues(index))
      downSamplingCalculator.addReferenceEntrySample(
        newReferenceEntries = childChunk.getReferenceEntryIds(index),
        newDifferentIdsCount = childChunk.getReferenceEntryDifferentIdsCounts(index),
        newStatuses = childChunk.getReferenceEntryStatuses(index)
      )
    }
  }

  //the final point (if there is one)
  @ReferenceEntryIdInt val downSampledReferenceEntryIds = downSamplingCalculator.referenceEntryIds()
  downSampledValuesBuilder.setAllValuesForTimestamp(
    timestampIndex = timestampsIterator.index,
    decimalValues = downSamplingCalculator.averageValues(),
    minValues = downSamplingCalculator.minValues(),
    maxValues = downSamplingCalculator.maxValues(),
    enumValues = downSamplingCalculator.enumUnionValues(),
    enumOrdinalsMostTime = downSamplingCalculator.enumMostTimeOrdinalValues(),
    referenceEntryIds = downSampledReferenceEntryIds,
    referenceEntryStatuses = downSamplingCalculator.referenceEntryStatuses(),
    referenceEntryDifferentIdsCount = downSamplingCalculator.referenceEntryDifferentIdsCount(),
    entryDataSet = childChunks.collectReferenceEntryData(downSampledReferenceEntryIds),
  )
  downSamplingCalculator.reset()

  val downSampledValues = downSampledValuesBuilder.build()

  //Instantiate the newly created objects
  val downSampledChunk = HistoryChunk(firstChunk.configuration, timestampsIterator.timeStamps, downSampledValues, RecordingType.Calculated)
  return HistoryBucket(this, downSampledChunk)
}

/**
 * Collects all entry data for the provided IDs
 */
fun SizedProvider<HistoryChunk>.collectReferenceEntryData(referenceEntryIds: @ReferenceEntryIdInt @MayBeNoValueOrPending IntArray): Set<ReferenceEntryData> {
  return referenceEntryIds.mapIndexed { referenceEntryDataSeriesIdAsInt, referenceEntryIdAsInt ->
    val referenceEntryDataSeriesIndex = ReferenceEntryDataSeriesIndex(referenceEntryDataSeriesIdAsInt)
    val referenceEntryId = ReferenceEntryId(referenceEntryIdAsInt)

    findData(referenceEntryDataSeriesIndex, referenceEntryId)
  }.filterNotNull()
    .toSet()
}

private fun SizedProvider<HistoryChunk>.findData(referenceEntryDataSeriesIndex: ReferenceEntryDataSeriesIndex, referenceEntryId: ReferenceEntryId): ReferenceEntryData? {
  fastForEach { historyChunk ->
    val referenceEntryData = historyChunk.getReferenceEntryData(referenceEntryDataSeriesIndex, referenceEntryId)
    if (referenceEntryData != null) {
      return referenceEntryData
    }
  }

  return null
}

/**
 * Ensures that the child buckets are sorted
 */
private fun List<HistoryBucket>.ensureSorted() {
  if (this.size <= 1) {
    return
  }

  for (i in 1 until size) {
    val previous = get(i - 1)
    val current = get(i)

    require(previous.end <= current.start) {
      "Requires sorted children. But element ${i - 1} has end: ${previous.end.formatUtc()} while ${i} has start ${current.start.formatUtc()}"
    }
  }
}

/**
 * Returns the "ideal" time stamps for the descriptor.
 * These time stamps are used for down sampling.
 */
fun HistoryBucketDescriptor.calculateTimeStamps(): DoubleArray {
  return DoubleArray(bucketRange.entriesCount) {
    start + (it + 0.5) * bucketRange.distance
  }
}

/**
 * Returns a new array with the mean values.
 * The returned array has a smaller height: original height / numberToCombine
 *
 * @param numberToCombine the number of values that are used to calculate one mean value
 */
fun IntArray2.calculateMeanValues(numberToCombine: Int): IntArray2 {
  val newHeight = (width.toDouble() / numberToCombine).toIntCeil()

  //TODO is it possible to use the initializer to calculate the values?
  val meanArray = IntArray2(width, newHeight) { 0 }

  for (columnIndex in 0 until width) {
    for (rowIndex in 0 until newHeight) {
      var sum = 0
      var count = 0 //to ensure the average is calculated correctly on segments that are not full

      val baseIndexForSegment = rowIndex * numberToCombine
      for (i in baseIndexForSegment until min(baseIndexForSegment + numberToCombine, height)) {
        sum += this[columnIndex, i]
        count++
      }

      meanArray[columnIndex, rowIndex] = (sum.toDouble() / count).roundToInt()
    }
  }

  return meanArray
}

/**
 * Calculates the average array
 */
fun DoubleArray.calculateMeanValues(numberToCombine: Int): DoubleArray {
  val count = (size.toDouble() / numberToCombine).toIntCeil()

  //Contains the results
  val results = DoubleArray(count)
  for (segmentIndex in 0 until count) {
    var sum = 0.0
    var count = 0 //to ensure the average is calculated correctly on segments that are not full

    val baseIndexForSegment = segmentIndex * numberToCombine
    for (i in baseIndexForSegment until min(baseIndexForSegment + numberToCombine, size)) {
      sum += this[i]
      count++
    }

    results[segmentIndex] = sum / count
  }

  return results
}

/**
 * Calculates the max value for each segment
 */
fun IntArray.calculateMax(numberToCombine: Int): IntArray {
  val count = (size.toDouble() / numberToCombine).toIntCeil()

  //Contains the results
  val results = IntArray(count)
  for (segmentIndex in 0 until count) {
    var max = Int.MIN_VALUE

    val baseIndexForSegment = segmentIndex * numberToCombine
    for (i in baseIndexForSegment until baseIndexForSegment + numberToCombine) {
      max = kotlin.math.max(max, this[i])
    }

    results[segmentIndex] = max
  }

  return results
}

/**
 * Calculates the min values for each segment
 */
fun IntArray.calculateMin(numberToCombine: Int): IntArray {
  val count = (size.toDouble() / numberToCombine).toIntCeil()

  //Contains the results
  val results = IntArray(count)
  for (segmentIndex in 0 until count) {
    var min = Int.MAX_VALUE

    val baseIndexForSegment = segmentIndex * numberToCombine
    for (i in baseIndexForSegment until baseIndexForSegment + numberToCombine) {
      min = min(min, this[i])
    }

    results[segmentIndex] = min
  }

  return results
}

/**
 * Calculates the standard deviation.
 * The mean for each segment is provided.
 */
fun DoubleArray.calculateStandardDeviation(numberToCombine: Int, means: DoubleArray): DoubleArray {
  val count = (size.toDouble() / numberToCombine).toIntCeil()

  require(means.size == count) {
    "Invalid mean size provided. Was <${means.size}> but expected <$count>"
  }

  //Contains the results
  val results = DoubleArray(count)

  for (segmentIndex in 0 until count) {
    val mean = means[segmentIndex]

    var sum = 0.0

    val baseIndexForSegment = segmentIndex * numberToCombine
    for (i in baseIndexForSegment until baseIndexForSegment + numberToCombine) {
      val delta = this[i] - mean
      sum += delta * delta
    }

    results[segmentIndex] = sqrt(sum)
  }

  return results
}


/**
 * Merges n standard deviations.
 *
 * THIS IS A ROUGH ESTIMATE!
 *
 * The mean for each segment is provided.
 */
fun DoubleArray.combineStandardDeviations(numberToCombine: Int): DoubleArray {
  val count = (size.toDouble() / numberToCombine).toIntCeil()

  val results = DoubleArray(count)

  for (segmentIndex in 0 until count) {
    var sum = 0.0
    var count = 0

    val baseIndexForSegment = segmentIndex * numberToCombine
    for (i in baseIndexForSegment until baseIndexForSegment + numberToCombine) {
      val stdDeviationToCombine = this[i]

      sum += stdDeviationToCombine * stdDeviationToCombine
      count++
    }

    results[segmentIndex] = sqrt(sum / count)
  }

  return results
}
