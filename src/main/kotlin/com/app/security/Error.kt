package com.app.security

class SecurityException(message: String) : RuntimeException(message)

val UserNotFound = SecurityException("UserNotFound")
val PasswordNotMatch = SecurityException("PasswordNotMatch")
val LocalLoginNotAllowed = SecurityException("LocalLoginNotAllowed")
val UserAlreadyRegistered = SecurityException("UserAlreadyRegistered")
val UserAlreadyVerified = SecurityException("UserAlreadyVerified")
val InvalidVerificationToken = SecurityException("InvalidVerificationToken")
val InvalidPasswordResetToken = SecurityException("InvalidPasswordResetToken")
val UserAlreadyRegisteredWithExternalProvider = SecurityException("UserAlreadyRegisteredWithExternalProvider")
val ServiceAccountNotFound = SecurityException("ServiceAccountNotFound")
val ServiceAccountNotEnabled = SecurityException("ServiceAccountNotEnabled")
val RegistrationProviderNotMatchedWithExistingUser = SecurityException("RegistrationProviderNotMatchedWithExistingUser")

val ServiceAccountSecretNotMatch = SecurityException("ServiceAccountSecretNotMatch")
val ServiceAccountAlreadyExists = SecurityException("ServiceAccountAlreadyExists")

val InvalidExchangeToken = SecurityException("InvalidExchangeToken")
val InvalidJwtToken = SecurityException("InvalidJwtToken")

val OnlyLocalAccountCanResetPassword = SecurityException("OnlyLocalAccountCanResetPassword")
val UserDoesNotSetEmail = SecurityException("UserMustHaveEmail")
val UserMustEitherSetEmailOrOAuthId = SecurityException("UserMustHaveEmailOrOAuthId")