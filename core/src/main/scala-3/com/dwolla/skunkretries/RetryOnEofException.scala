package com.dwolla.skunkretries

import cats.*
import cats.effect.*
import cats.effect.std.*
import com.dwolla.tracing.LowPriorityTraceableValueInstances.*
import io.circe.*
import io.circe.literal.*
import io.circe.syntax.*
import natchez.Trace
import retry.*
import retry.RetryPolicies.*
import retry.syntax.*
import skunk.exception.EofException

import scala.concurrent.duration.*

class RetryOnEofException[F[_] : Random : Temporal : Trace](maxRetries: Int = 3,
                                                            initialDelay: FiniteDuration = 100.millis,
                                                            maxDelay: FiniteDuration = 5.seconds,
                                                           ) extends (F ~> F):
  private implicit val finiteDurationEncoder: Encoder[FiniteDuration] = Encoder[Long].contramap(_.toMillis)

  private implicit val retryDetailsEncoder: Encoder[RetryDetails] = Encoder.instance: rd =>
    json"""{
            "retriesSoFar": ${rd.retriesSoFar},
            "cumulativeDelayMs": ${rd.cumulativeDelay},
            "nextStepIfUnsuccessful": ${rd.nextStepIfUnsuccessful}
          }"""

  private implicit val nextStepIfUnsuccessfulEncoder: Encoder[RetryDetails.NextStep] = Encoder.instance:
    case RetryDetails.NextStep.GiveUp => "GiveUp".asJson
    case RetryDetails.NextStep.DelayAndRetry(nextDelay) =>
      json"""{
               "nextDelay": $nextDelay
             }"""

  private def addRetryDetailsToTrace(ex: Throwable, rd: RetryDetails): F[Unit] =
    Trace[F].attachError(ex, "retryDetails" -> rd)

  override def apply[A](fa: F[A]): F[A] = fa.retryingOnErrors(
    capDelay(maxDelay, fullJitter(initialDelay)).join(limitRetries(maxRetries)),
    ResultHandler.retryOnSomeErrors(_.isInstanceOf[EofException], addRetryDetailsToTrace)
  )

object RetryOnEofException:
  def apply[F[_] : Random : Temporal : Trace](maxRetries: Int = 3,
                                              initialDelay: FiniteDuration = 100.millis,
                                              maxDelay: FiniteDuration = 5.seconds,
                                             ): RetryOnEofException[F] = new RetryOnEofException(maxRetries, initialDelay, maxDelay)
