package com.dearby.nativeapp.entities.catalog.api

import com.dearby.nativeapp.entities.catalog.model.CatalogModel
import com.dearby.nativeapp.entities.catalog.model.CatalogLocalModel
import com.dearby.nativeapp.shared.api.HttpClient
import com.dearby.nativeapp.shared.api.wireJson
import com.dearby.nativeapp.shared.storage.DearbyDao
import com.dearby.nativeapp.shared.storage.DocumentRecord
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString

class CatalogRepository(private val http: HttpClient, private val dao: DearbyDao) {
    private val mutex = Mutex()
    private val cacheKey = "catalog:v1:${http.cacheNamespace}"
    private val localKey = "catalog:local:v1:${http.cacheNamespace}"
    private var memory: CatalogModel? = null
    suspend fun cached(): CatalogModel? = mutex.withLock {
        memory ?: dao.document(cacheKey)?.let { wireJson.decodeFromString<CatalogModel>(it.json).validated() }?.also { memory = it }
    }
    suspend fun refresh(): CatalogModel = mutex.withLock {
        val response = http.request("GET", "/catalog", authenticated = false)
        val catalog = wireJson.decodeFromString<CatalogModel>(response).validated()
        dao.put(DocumentRecord(cacheKey, wireJson.encodeToString(catalog)))
        memory = catalog
        catalog
    }
    suspend fun local(): CatalogLocalModel = dao.document(localKey)?.let { wireJson.decodeFromString(it.json) } ?: CatalogLocalModel()
    suspend fun save(local: CatalogLocalModel) { dao.put(DocumentRecord(localKey, wireJson.encodeToString(local))) }
}
