package com.dwolla.skunkretries

import cats.*
import cats.effect.*
import cats.effect.std.*
import cats.tagless.*
import cats.tagless.syntax.all.*
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

extension[F[_]: Random : Temporal: Trace, Alg[_[_]]: FunctorK](algebra: Alg[F])
  /**
   * Creates a new instance of a final tagless algebra that retries operations on EofException.
   *
   * @param maxRetries   Maximum number of retry attempts
   * @param initialDelay Initial delay before the first retry
   * @param maxDelay     Maximum delay between retries
   * @return A new instance of the algebra that retries operations on EofException
   */
  def withRetries(maxRetries: Int = 3,
                  initialDelay: FiniteDuration = 100.millis,
                  maxDelay: FiniteDuration = 5.seconds,
                 ): Alg[F] =
    algebra.mapK:
      new(F ~> F):
        override def apply[A](fa: F[A]): F[A] = fa.retryingOnErrors(
          capDelay(maxDelay, fullJitter(initialDelay)).join(limitRetries(maxRetries)),
          ResultHandler.retryOnSomeErrors(_.isInstanceOf[EofException], addRetryDetailsToTrace)
        )

private def addRetryDetailsToTrace[F[_] : Trace](ex: Throwable, rd: RetryDetails): F[Unit] =
  Trace[F].attachError(ex, "retryDetails" -> rd)

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
