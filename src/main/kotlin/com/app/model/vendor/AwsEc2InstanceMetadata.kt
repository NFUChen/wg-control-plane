package com.app.model.vendor

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import java.util.*

/**
 * Represents AWS EC2 instance metadata collected during infrastructure deployment
 */
@Entity
@Table(
    name = "aws_ec2_instance_metadata",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_aws_ec2_instance_id",
            columnNames = ["instance_id"]
        )
    ]
)
data class AwsEc2InstanceMetadata(
    @Id
    val id: UUID = UUID.randomUUID(),

    @Column(name = "instance_id", nullable = false, unique = true)
    val instanceId: String,

    @Column(name = "private_ip", nullable = false)
    val privateIp: String,

    @Column(name = "region", nullable = false)
    val region: String,

    @Column(name = "availability_zone", nullable = false)
    val availabilityZone: String,

    @Column(name = "network_interface_id", nullable = false)
    val networkInterfaceId: String,

    @Column(name = "vpc_id", nullable = false)
    val vpcId: String,

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
}