package com.dwolla.skunkretries

import cats.effect.kernel.Temporal
import cats.syntax.all.*
import cats.~>
import com.dwolla.tracing.LowPriorityTraceableValueInstances.traceValueViaJson
import io.circe.*
import io.circe.literal.*
import natchez.Trace
import retry.RetryDetails
import retry.RetryPolicies.*
import retry.syntax.all.*
import skunk.exception.EofException

import scala.concurrent.duration.*

class RetryOnEofException[F[_] : Temporal : Trace](maxRetries: Int = 3,
                                                   initialDelay: FiniteDuration = 100.millis,
                                                   maxDelay: FiniteDuration = 5.seconds,
                                                  ) extends (F ~> F) {
  private implicit val finiteDurationEncoder: Encoder[FiniteDuration] = Encoder[Long].contramap(_.toMillis)

  private implicit val retryDetailsEncoder: Encoder[RetryDetails] = Encoder.instance { rd =>
    json"""{
            "retriesSoFar": ${rd.retriesSoFar},
            "cumulativeDelayMs": ${rd.cumulativeDelay},
            "givingUp": ${rd.givingUp},
            "upcomingDelay":${rd.upcomingDelay}
          }"""
  }

  private def addRetryDetailsToTrace(ex: Throwable, rd: RetryDetails): F[Unit] =
    Trace[F].attachError(ex, "retryDetails" -> rd)

  override def apply[A](fa: F[A]): F[A] = fa.retryingOnSomeErrors(
    _.isInstanceOf[EofException].pure[F],
    capDelay(maxDelay, fullJitter(initialDelay)).join(limitRetries(maxRetries)),
    addRetryDetailsToTrace
  )
}

object RetryOnEofException {
  def apply[F[_] : Temporal : Trace](maxRetries: Int = 3,
                                     initialDelay: FiniteDuration = 100.millis,
                                     maxDelay: FiniteDuration = 5.seconds,
                                    ): RetryOnEofException[F] = new RetryOnEofException(maxRetries, initialDelay, maxDelay)
}
