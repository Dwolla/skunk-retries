package com.dwolla.skunkretries

import cats.effect.kernel.Temporal
import cats.syntax.all.*
import cats.tagless.*
import cats.tagless.syntax.all.*
import cats.~>
import com.dwolla.tracing.LowPriorityTraceableValueInstances.*
import io.circe.*
import io.circe.literal.*
import natchez.Trace
import retry.RetryDetails
import retry.RetryPolicies.*
import retry.syntax.all.*
import skunk.exception.EofException

import scala.concurrent.duration.*

/**
 * Middleware that retries operations that fail with EofException.
 * This is useful for handling database connection issues that might be temporary.
 */
object RetryMiddleware {
  implicit class AlgebraRetryOps[F[_]: Temporal: Trace, Alg[_[_]]: FunctorK](algebra: Alg[F]) {
    private def addRetryDetailsToTrace(ex: Throwable, rd: RetryDetails): F[Unit] =
      Trace[F].attachError(ex, "retryDetails" -> rd)

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
      algebra.mapK(new (F ~> F) {
        override def apply[A](fa: F[A]): F[A] = fa.retryingOnSomeErrors(
          _.isInstanceOf[EofException].pure[F],
          capDelay(maxDelay, fullJitter(initialDelay)).join(limitRetries(maxRetries)),
          addRetryDetailsToTrace
        )
      })
    }
  }

  private implicit val finiteDurationEncoder: Encoder[FiniteDuration] = Encoder[Long].contramap(_.toMillis)

  private implicit val retryDetailsEncoder: Encoder[RetryDetails] = Encoder.instance { rd =>
    json"""{
            "retriesSoFar": ${rd.retriesSoFar},
            "cumulativeDelayMs": ${rd.cumulativeDelay},
            "givingUp": ${rd.givingUp},
            "upcomingDelay":${rd.upcomingDelay}
          }"""
  }
}
