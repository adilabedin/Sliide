package com.androidlearning.sliide.users.domain.validation

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test

class UserDetailsValidatorTest {
    @Test
    fun emptyNameIsRequired() {
        assertThat(UserDetailsValidator.validateName("")).isEqualTo(NameError.REQUIRED)
    }

    @Test
    fun whitespaceOnlyNameIsRequired() {
        assertThat(UserDetailsValidator.validateName("   \t ")).isEqualTo(NameError.REQUIRED)
    }

    @Test
    fun oneCharacterNameIsTooShort() {
        assertThat(UserDetailsValidator.validateName("J")).isEqualTo(NameError.TOO_SHORT)
    }

    @Test
    fun oneCharacterAfterTrimmingIsTooShort() {
        assertThat(UserDetailsValidator.validateName("  J  ")).isEqualTo(NameError.TOO_SHORT)
    }

    @Test
    fun twoCharacterNameIsValid() {
        assertThat(UserDetailsValidator.validateName("Jo")).isNull()
    }

    @Test
    fun fullNameIsValid() {
        assertThat(UserDetailsValidator.validateName(" Jane Doe ")).isNull()
    }

    @Test
    fun emptyEmailIsRequired() {
        assertThat(UserDetailsValidator.validateEmail("")).isEqualTo(EmailError.REQUIRED)
        assertThat(UserDetailsValidator.validateEmail("   ")).isEqualTo(EmailError.REQUIRED)
    }

    @Test
    fun emailWithoutAtIsInvalid() {
        assertThat(UserDetailsValidator.validateEmail("jane.example.com")).isEqualTo(EmailError.INVALID)
    }

    @Test
    fun emailWithTwoAtsIsInvalid() {
        assertThat(UserDetailsValidator.validateEmail("jane@doe@example.com")).isEqualTo(EmailError.INVALID)
    }

    @Test
    fun emailWithoutLocalPartIsInvalid() {
        assertThat(UserDetailsValidator.validateEmail("@example.com")).isEqualTo(EmailError.INVALID)
    }

    @Test
    fun domainWithoutDotIsInvalid() {
        assertThat(UserDetailsValidator.validateEmail("jane@example")).isEqualTo(EmailError.INVALID)
    }

    @Test
    fun domainWithEmptyLabelIsInvalid() {
        assertThat(UserDetailsValidator.validateEmail("jane@example..com")).isEqualTo(EmailError.INVALID)
        assertThat(UserDetailsValidator.validateEmail("jane@.example.com")).isEqualTo(EmailError.INVALID)
        assertThat(UserDetailsValidator.validateEmail("jane@example.com.")).isEqualTo(EmailError.INVALID)
    }

    @Test
    fun innerWhitespaceIsInvalid() {
        assertThat(UserDetailsValidator.validateEmail("jane doe@example.com")).isEqualTo(EmailError.INVALID)
    }

    @Test
    fun validEmailIsAccepted() {
        assertThat(UserDetailsValidator.validateEmail("jane@example.com")).isNull()
        assertThat(UserDetailsValidator.validateEmail("jane.doe+tag@mail.example.co.uk")).isNull()
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertThat(UserDetailsValidator.validateEmail("  jane@example.com  ")).isNull()
    }
}
