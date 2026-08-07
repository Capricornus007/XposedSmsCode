package com.github.magisk317.smscode.entitlement

import org.koin.dsl.module

val mobileEntitlementGoogleSignInModule = module {
    single<MobileEntitlementGoogleSignIn> { CredentialManagerMobileEntitlementGoogleSignIn() }
}
