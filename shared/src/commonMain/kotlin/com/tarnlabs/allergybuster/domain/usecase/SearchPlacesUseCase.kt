package com.tarnlabs.allergybuster.domain.usecase

import com.tarnlabs.allergybuster.data.remote.GeocodingApiClient
import com.tarnlabs.allergybuster.data.remote.dto.toPlaceResults
import com.tarnlabs.allergybuster.domain.model.PlaceResult

class SearchPlacesUseCase(private val api: GeocodingApiClient) {

    suspend operator fun invoke(query: String): List<PlaceResult> {
        if (query.isBlank()) return emptyList()
        return api.search(query.trim()).toPlaceResults()
    }
}
