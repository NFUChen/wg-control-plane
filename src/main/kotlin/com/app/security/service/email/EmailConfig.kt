package com.app.security.service.email

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.SesClient



@ConfigurationProperties(prefix = "aws.ses")
data class SesProperties(
    val accessKey: String,
    val secretKey: String
)

@Configuration
class EmailConfig(
    private val sesProperties: SesProperties
) {

    @Bean
    fun sesClient(): SesClient {
        val credentials = AwsBasicCredentials.create(
            sesProperties.accessKey,
            sesProperties.secretKey
        )
        return SesClient.builder()
            .region(Region.US_EAST_1) // 或從配置讀取
            .credentialsProvider( StaticCredentialsProvider.create(credentials) )
            .build()
    }
}



@ConfigurationProperties(prefix = "email")
data class EmailProperties(
    val emailAddress: String,
    val serviceName: String
)