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
package com.meistercharts.algorithms.layers.slippymap

import com.meistercharts.algorithms.tile.TileIndex
import kotlin.math.abs
import kotlin.reflect.KProperty0

/**
 * Provides urls to slippy map tile servers.
 *
 * For a list of tile servers see [TileServers](https://wiki.openstreetmap.org/wiki/Tile_servers)
 */
interface SlippyMapProvider {
  /**
   * Compute the url of a slippy map tile for the given tile index and zoom
   */
  fun url(tileIndex: TileIndex, zoom: Int): String

  /**
   * Retrieve the legal notice for this provider
   */
  val legalNotice: String?
}

/**
 * Creates a [SlippyMapProvider] that delegates all calls to the current value of this property.
 */
fun KProperty0<SlippyMapProvider>.delegate(): SlippyMapProvider {
  return object : SlippyMapProvider {
    override fun url(tileIndex: TileIndex, zoom: Int): String {
      return get().url(tileIndex, zoom)
    }

    override val legalNotice: String?
      get() {
        return get().legalNotice
      }
  }
}

/**
 * Retrieve slippy map tiles from an openstreetmap server
 *
 * [Policies](https://operations.osmfoundation.org/policies/tiles/)
 */
data object OpenStreetMap : SlippyMapProvider {
  override fun url(tileIndex: TileIndex, zoom: Int): String {
    val subDomain = when (val modulo = (abs(tileIndex.x) + abs(tileIndex.y)) % 3) {
      0 -> "a"
      1 -> "b"
      2 -> "c"
      else -> throw IllegalStateException("fix modulo computation: $modulo")
    }
    return "https://$subDomain.tile.openstreetmap.org/$zoom/${tileIndex.x}/${tileIndex.y}.png"
  }

  // see also https://www.openstreetmap.org/copyright/en
  override val legalNotice: String = "© OpenStreetMap contributors"

}

/**
 * Retrieve slippy map tiles from an openstreetmap server with german location names
 *
 * [Policies](https://operations.osmfoundation.org/policies/tiles/)
 */
data object OpenStreetMapDe : SlippyMapProvider {
  override fun url(tileIndex: TileIndex, zoom: Int): String {
    val subDomain = when (val modulo = (abs(tileIndex.x) + abs(tileIndex.y)) % 3) {
      0 -> "a"
      1 -> "b"
      2 -> "c"
      else -> throw IllegalStateException("fix modulo computation: $modulo")
    }
    return "https://$subDomain.tile.openstreetmap.de/$zoom/${tileIndex.x}/${tileIndex.y}.png"
  }

  // see also https://www.openstreetmap.org/copyright/en
  override val legalNotice: String? = "© OpenStreetMap contributors"

}

/**
 * Retrieve slippy map tiles from an openstreetmap server using the humanitarian map style
 *
 * [Policies](https://operations.osmfoundation.org/policies/tiles/)
 * [HumanitarianMap](https://wiki.openstreetmap.org/wiki/Humanitarian_map_style)
 */
data object OpenStreetMapHumanitarian : SlippyMapProvider {
  override fun url(tileIndex: TileIndex, zoom: Int): String {
    val subDomain = when (val modulo = (abs(tileIndex.x) + abs(tileIndex.y)) % 2) {
      0 -> "a"
      1 -> "b"
      else -> throw IllegalStateException("fix modulo computation: $modulo")
    }
    return "http://$subDomain.tile.openstreetmap.fr/hot/${zoom}/${tileIndex.x}/${tileIndex.y}.png"
  }

  // see also https://www.openstreetmap.org/copyright/en
  override val legalNotice: String? = "© OpenStreetMap contributors"

}

/**
 * wmflabs OSM B&W
 *
 * mapnik map grayscale
 *
 * [Policies](https://operations.osmfoundation.org/policies/tiles/)
 */
data object OpenStreetMapGrayscale : SlippyMapProvider {
  override fun url(tileIndex: TileIndex, zoom: Int): String {
    return "https://tiles.wmflabs.org/bw-mapnik/${zoom}/${tileIndex.x}/${tileIndex.y}.png"
  }

  // see also https://www.openstreetmap.org/copyright/en
  override val legalNotice: String? = "© OpenStreetMap contributors"

}

/**
 * High-contrast B+W (black and white) maps
 *
 * [Stamen](http://maps.stamen.com/#toner/12/37.7706/-122.3782)
 */
data object OpenStreetMapBlackAndWhite : SlippyMapProvider {
  override fun url(tileIndex: TileIndex, zoom: Int): String {
    val subDomain = when (val modulo = (abs(tileIndex.x) + abs(tileIndex.y)) % 4) {
      0 -> "a"
      1 -> "b"
      2 -> "c"
      3 -> "d"
      else -> throw IllegalStateException("fix modulo computation: $modulo")
    }
    return "http://$subDomain.tile.stamen.com/toner/${zoom}/${tileIndex.x}/${tileIndex.y}.png"
  }

  // see also https://www.openstreetmap.org/copyright/en
  override val legalNotice: String? = "Map tiles by Stamen Design, under CC BY 3.0. Data by OpenStreetMap, under ODbL"

}

/**
 * Terrain maps
 *
 * [Stamen](http://maps.stamen.com/#toner/12/37.7706/-122.3782)
 *
 * Does only support zoom levels up to 16!
 */
data object OpenStreetMapTerrain : SlippyMapProvider {
  override fun url(tileIndex: TileIndex, zoom: Int): String {
    val subDomain = when (val modulo = (abs(tileIndex.x) + abs(tileIndex.y)) % 4) {
      0 -> "a"
      1 -> "b"
      2 -> "c"
      3 -> "d"
      else -> throw IllegalStateException("fix modulo computation: $modulo")
    }
    return "http://$subDomain.tile.stamen.com/terrain/${zoom}/${tileIndex.x}/${tileIndex.y}.png"
  }

  // see also https://www.openstreetmap.org/copyright/en
  override val legalNotice: String? = "Map tiles by Stamen Design, under CC BY 3.0. Data by OpenStreetMap, under ODbL"

}

/**
 * Retrieve slippy map tiles from the wikimedia server
 *
 * [TermsOfUse](https://foundation.wikimedia.org/wiki/Maps_Terms_of_Use)
 */
data object WikimediaMaps : SlippyMapProvider {
  override fun url(tileIndex: TileIndex, zoom: Int): String {
    return "https://maps.wikimedia.org/osm-intl/$zoom/${tileIndex.x}/${tileIndex.y}.png"
  }

  // https://foundation.wikimedia.org/wiki/Maps_Terms_of_Use
  override val legalNotice: String? = "© OpenStreetMap contributors / Wikimedia"

}
