package com.app.converter

import com.app.model.IPAddress
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter


/**
 * JPA Attribute Converter for MutableList<IPAddress>
 * Stores IP addresses as JSON string in database
 */
@Converter
class IPAddressListConverter : AttributeConverter<MutableList<IPAddress>, String> {

    private val objectMapper = ObjectMapper()

    override fun convertToDatabaseColumn(attribute: MutableList<IPAddress>?): String? {
        if (attribute.isNullOrEmpty()) return null

        return try {
            // Convert list of IPAddress to list of address strings
            val addressStrings = attribute.map { it.address }
            objectMapper.writeValueAsString(addressStrings)
        } catch (e: Exception) {
            throw RuntimeException("Failed to convert IP addresses to JSON", e)
        }
    }

    override fun convertToEntityAttribute(dbData: String?): MutableList<IPAddress> {
        if (dbData.isNullOrBlank()) return mutableListOf()

        return try {
            val addressStrings: List<String> = objectMapper.readValue(
                dbData,
                object : TypeReference<List<String>>() {}
            )
            addressStrings.map { IPAddress(it) }.toMutableList()
        } catch (e: Exception) {
            throw RuntimeException("Failed to convert JSON to IP addresses", e)
        }
    }
}