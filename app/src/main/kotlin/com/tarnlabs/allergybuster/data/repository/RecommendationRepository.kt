package com.tarnlabs.allergybuster.data.repository

import com.tarnlabs.allergybuster.data.local.db.dao.RecommendationDao
import com.tarnlabs.allergybuster.data.local.db.entity.toDomain
import com.tarnlabs.allergybuster.data.local.db.entity.toEntity
import com.tarnlabs.allergybuster.domain.model.Recommendation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecommendationRepository @Inject constructor(
    private val dao: RecommendationDao
) {
    suspend fun save(rec: Recommendation) = dao.insertOrReplace(rec.toEntity())

    suspend fun getForDate(date: String): Recommendation? =
        dao.getForDate(date)?.toDomain()

    fun observeRecent(limit: Int = 90): Flow<List<Recommendation>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }
}
