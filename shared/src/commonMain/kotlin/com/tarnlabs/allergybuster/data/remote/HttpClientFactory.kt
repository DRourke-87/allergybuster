package com.tarnlabs.allergybuster.data.remote

import io.ktor.client.HttpClient

internal expect fun createHttpClient(): HttpClient
