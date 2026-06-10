package com.tarnlabs.allergybuster.usecase

import com.tarnlabs.allergybuster.data.remote.OpenMeteoApiClient
import com.tarnlabs.allergybuster.data.remote.dto.AirQualityResponse
import com.tarnlabs.allergybuster.data.remote.dto.HourlyData
import com.tarnlabs.allergybuster.data.repository.FeedbackRepository
import com.tarnlabs.allergybuster.domain.model.UserWeights
import com.tarnlabs.allergybuster.domain.usecase.CheckLocationOutlookUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckLocationOutlookUseCaseTest {

    private val api = mockk<OpenMeteoApiClient>()
    private val feedbackRepository = mockk<FeedbackRepository>()
    private val useCase = CheckLocationOutlookUseCase(api, feedbackRepository)

    private fun response(vararg hours: Pair<String, Float>) = AirQualityResponse(
        hourly = HourlyData(
            time          = hours.map { it.first },
            alderPollen   = hours.map { 0f },
            birchPollen   = hours.map { 0f },
            grassPollen   = hours.map { it.second },
            mugwortPollen = hours.map { 0f },
            olivePollen   = hours.map { 0f },
            ragweedPollen = hours.map { 0f }
        )
    )

    @Test fun `scores each returned day with user weights, sorted by date`() = runTest {
        coEvery { api.getAirQuality(any(), any(), any(), any(), any()) } returns response(
            "2026-06-11T08:00" to 5f,
            "2026-06-10T08:00" to 50f,
            "2026-06-10T14:00" to 30f
        )
        coEvery { feedbackRepository.getWeights() } returns UserWeights()

        val outlook = useCase(41.9, 12.5)

        assertEquals(2, outlook.size)
        assertEquals("2026-06-10", outlook[0].date)
        assertEquals(2, outlook[0].level)
        assertTrue(outlook[0].topContributors.contains("Grass"))
        assertEquals("2026-06-11", outlook[1].date)
        assertEquals(0, outlook[1].level)
    }

    @Test(expected = RuntimeException::class)
    fun `propagates network failure to the caller`() = runTest {
        coEvery { api.getAirQuality(any(), any(), any(), any(), any()) } throws RuntimeException("offline")
        coEvery { feedbackRepository.getWeights() } returns UserWeights()
        useCase(41.9, 12.5)
    }
}
