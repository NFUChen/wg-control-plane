package com.app.model.vendor

import com.app.model.AnsibleHost
import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.NotFound
import org.hibernate.annotations.NotFoundAction
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
        ),
        UniqueConstraint(
            name = "uk_aws_ec2_network_interface_id",
            columnNames = ["network_interface_id"]
        ),
        // VPC ID and primary flag combination should be unique to prevent multiple primary instances in the same VPC
        UniqueConstraint(
            name = "uk_aws_ec2_vpc_primary",
            columnNames = ["vpc_id", "is_primary"]
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

    @Column(name = "is_primary", nullable = false)
    val isPrimary: Boolean,

    @CreationTimestamp
    @Column(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @JsonIgnore
    @OneToOne(fetch = FetchType.EAGER, optional = true)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "ansible_host_id", nullable = true)
    val ansibleHost: AnsibleHost? = null
) {
}