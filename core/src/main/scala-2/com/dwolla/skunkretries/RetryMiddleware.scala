package com.dwolla.skunkretries

import cats.effect.kernel.Temporal
import cats.tagless.*
import cats.tagless.syntax.all.*
import natchez.Trace

import scala.concurrent.duration.*

/**
 * Middleware that retries operations that fail with EofException.
 * This is useful for handling database connection issues that might be temporary.
 */
object RetryMiddleware {
  implicit class AlgebraRetryOps[F[_]: Temporal: Trace, Alg[_[_]]: FunctorK](algebra: Alg[F]) {
    /**
     * Creates a new instance of a final tagless algebra that retries operations on EofException.
     *
     * @param maxRetries    Maximum number of retry attempts
     * @param initialDelay  Initial delay before the first retry
     * @param maxDelay      Maximum delay between retries
     * @return A new instance of the algebra that retries operations on EofException
     */
    def withRetries(maxRetries: Int = 3,
                    initialDelay: FiniteDuration = 100.millis,
                    maxDelay: FiniteDuration = 5.seconds,
                   ): Alg[F] = {
      algebra.mapK(RetryOnEofException(maxRetries, initialDelay, maxDelay))
    }
  }
}
