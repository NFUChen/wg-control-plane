package com.app.repository

import com.app.model.vendor.AwsEc2InstanceMetadata
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

/**
 * Repository interface for AwsEc2InstanceMetadata entities
 */
@Repository
interface AwsEc2InstanceMetadataRepository : JpaRepository<AwsEc2InstanceMetadata, UUID> {

    /**
     * Find metadata by AWS instance ID
     */
    fun findByInstanceId(instanceId: String): AwsEc2InstanceMetadata?

    /**
     * Check if metadata exists for an instance ID
     */
    fun existsByInstanceId(instanceId: String): Boolean

    /**
     * Find metadata by private IP address
     */
    fun findByPrivateIp(privateIp: String): AwsEc2InstanceMetadata?

    /**
     * Find all instances in a specific region
     */
    fun findByRegion(region: String): List<AwsEc2InstanceMetadata>

    /**
     * Find all instances in a specific availability zone
     */
    fun findByAvailabilityZone(availabilityZone: String): List<AwsEc2InstanceMetadata>

    /**
     * Find all instances in a specific VPC
     */
    fun findByVpcId(vpcId: String): List<AwsEc2InstanceMetadata>

    /**
     * Find metadata by network interface ID
     */
    fun findByNetworkInterfaceId(networkInterfaceId: String): AwsEc2InstanceMetadata?
    /**
     * Find all instances in multiple regions
     */
    fun findByRegionIn(regions: List<String>): List<AwsEc2InstanceMetadata>

    /**
     * Custom query to find instances by IP range (basic implementation)
     */
    @Query("SELECT e FROM AwsEc2InstanceMetadata e WHERE e.privateIp LIKE :ipPrefix")
    fun findByPrivateIpPrefix(@Param("ipPrefix") ipPrefix: String): List<AwsEc2InstanceMetadata>

    /**
     * Count instances by region
     */
    @Query("SELECT COUNT(e) FROM AwsEc2InstanceMetadata e WHERE e.region = :region")
    fun countByRegion(@Param("region") region: String): Long

    /**
     * Count instances by VPC
     */
    @Query("SELECT COUNT(e) FROM AwsEc2InstanceMetadata e WHERE e.vpcId = :vpcId")
    fun countByVpcId(@Param("vpcId") vpcId: String): Long
}