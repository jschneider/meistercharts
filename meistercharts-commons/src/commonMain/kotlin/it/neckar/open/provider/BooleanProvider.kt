package it.neckar.open.provider

import it.neckar.open.annotations.NotBoxed

/**
 * Provides a *single* boolean - to avoid boxing
 */
fun interface BooleanProvider {
  /**
   * Provides the boolean value
   */
  operator fun invoke(): @NotBoxed Boolean

  companion object {
    /**
     * Always returns false
     */
    val False: BooleanProvider = BooleanProvider { false }

    /**
     * Always returns true
     */
    val True: BooleanProvider = BooleanProvider { true }

    operator fun invoke(value: Boolean): BooleanProvider {
      return if (value) True else False
    }
  }
}
