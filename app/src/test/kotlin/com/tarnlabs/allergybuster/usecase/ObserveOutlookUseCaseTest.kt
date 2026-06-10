package com.tarnlabs.allergybuster.usecase

import app.cash.turbine.test
import com.tarnlabs.allergybuster.data.repository.FeedbackRepository
import com.tarnlabs.allergybuster.data.repository.PollenRepository
import com.tarnlabs.allergybuster.domain.model.DailyPollen
import com.tarnlabs.allergybuster.domain.model.UserWeights
import com.tarnlabs.allergybuster.domain.usecase.ObserveOutlookUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ObserveOutlookUseCaseTest {

    private val pollenRepository  = mockk<PollenRepository>()
    private val feedbackRepository = mockk<FeedbackRepository>()
    private val useCase = ObserveOutlookUseCase(pollenRepository, feedbackRepository)

    private val now = System.currentTimeMillis()
    private val today = LocalDate.now()

    private fun pollen(date: String, grass: Float = 0f, fetchedAt: Long = now) =
        DailyPollen(date, 0f, 0f, grass, 0f, 0f, 0f, fetchedAt)

    @Test fun `emits scored outlook for days after today`() = runTest {
        every { pollenRepository.observeFromDate(any()) } returns flowOf(
            listOf(
                pollen(today.toString(), grass = 50f),
                pollen(today.plusDays(1).toString(), grass = 50f),
                pollen(today.plusDays(2).toString(), grass = 5f)
            )
        )
        every { feedbackRepository.observeWeights() } returns flowOf(UserWeights())

        useCase().test {
            val outlook = awaitItem()
            assertEquals(2, outlook.size)
            assertEquals(today.plusDays(1).toString(), outlook[0].date)
            assertEquals(2, outlook[0].level)
            assertEquals(0, outlook[1].level)
            assertTrue(outlook[0].topContributors.contains("Grass"))
            awaitComplete()
        }
    }

    @Test fun `excludes today and days with stale data`() = runTest {
        val stale = now - (ObserveOutlookUseCase.MAX_AGE_MS + 1)
        every { pollenRepository.observeFromDate(any()) } returns flowOf(
            listOf(
                pollen(today.toString(), grass = 20f),
                pollen(today.plusDays(1).toString(), grass = 20f, fetchedAt = stale)
            )
        )
        every { feedbackRepository.observeWeights() } returns flowOf(UserWeights())

        useCase().test {
            assertTrue(awaitItem().isEmpty())
            awaitComplete()
        }
    }

    @Test fun `falls back to default weights when none saved`() = runTest {
        every { pollenRepository.observeFromDate(any()) } returns flowOf(
            listOf(pollen(today.plusDays(1).toString(), grass = 10f))
        )
        every { feedbackRepository.observeWeights() } returns flowOf(null)

        useCase().test {
            val outlook = awaitItem()
            assertEquals(1, outlook.size)
            assertEquals(1, outlook[0].level)
            awaitComplete()
        }
    }

    @Test fun `emits empty list when no forecasts cached`() = runTest {
        every { pollenRepository.observeFromDate(any()) } returns flowOf(emptyList())
        every { feedbackRepository.observeWeights() } returns flowOf(UserWeights())

        useCase().test {
            assertTrue(awaitItem().isEmpty())
            awaitComplete()
        }
    }
}
