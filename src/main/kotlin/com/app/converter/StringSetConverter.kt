package com.app.converter

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

/**
 * JPA Attribute Converter for Set<String>
 * Stores string sets as JSON string in database
 */
@Converter
class StringSetConverter : AttributeConverter<Set<String>, String> {

    private val objectMapper = ObjectMapper()

    override fun convertToDatabaseColumn(attribute: Set<String>?): String? {
        if (attribute.isNullOrEmpty()) return null

        return try {
            objectMapper.writeValueAsString(attribute.toList())
        } catch (e: Exception) {
            throw RuntimeException("Failed to convert string set to JSON", e)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): Set<String>? {
        if (dbData.isNullOrBlank()) return emptySet()

        return try {
            val stringList: List<String> = objectMapper.readValue(dbData, object : TypeReference<List<String>>() {})
            stringList.toSet()
        } catch (e: Exception) {
            throw RuntimeException("Failed to convert JSON to string set: $dbData", e)
        }
    }
}