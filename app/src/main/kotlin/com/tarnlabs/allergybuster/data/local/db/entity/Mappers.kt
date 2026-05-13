package com.tarnlabs.allergybuster.data.local.db.entity

import com.tarnlabs.allergybuster.domain.model.DailyPollen
import com.tarnlabs.allergybuster.domain.model.Recommendation
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun PollenForecastEntity.toDomain() = DailyPollen(
    date       = date,
    alderMax   = alderMax,
    birchMax   = birchMax,
    grassMax   = grassMax,
    mugwortMax = mugwortMax,
    oliveMax   = oliveMax,
    ragweedMax = ragweedMax,
    fetchedAt  = fetchedAt
)

fun RecommendationEntity.toDomain() = Recommendation(
    date             = date,
    level            = level,
    score            = score,
    advice           = advice,
    topContributors  = Json.decodeFromString(topContributors),
    computedAt       = computedAt,
    isStale          = isStale,
    locationName     = locationName
)

fun Recommendation.toEntity() = RecommendationEntity(
    date            = date,
    level           = level,
    score           = score,
    advice          = advice,
    topContributors = Json.encodeToString(topContributors),
    computedAt      = computedAt,
    isStale         = isStale,
    locationName    = locationName
)
