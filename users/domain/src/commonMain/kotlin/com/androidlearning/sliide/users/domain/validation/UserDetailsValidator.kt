package com.androidlearning.sliide.users.domain.validation

/** Why a name is not acceptable. */
enum class NameError {
    REQUIRED,
    TOO_SHORT,
}

/** Why an email is not acceptable. */
enum class EmailError {
    REQUIRED,
    INVALID,
}

/**
 * Shared rules for the editable user fields (spec: user-profile). Inputs are trimmed before checking; callers save the
 * trimmed values. A `null` result means the value is valid.
 */
object UserDetailsValidator {
    const val MIN_NAME_LENGTH = 2

    fun validateName(name: String): NameError? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> NameError.REQUIRED
            trimmed.length < MIN_NAME_LENGTH -> NameError.TOO_SHORT
            else -> null
        }
    }

    /**
     * Valid when the trimmed email has a non-empty local part, exactly one "@", no whitespace, and a domain made of at
     * least two dot-separated labels, none of them empty.
     */
    fun validateEmail(email: String): EmailError? {
        val trimmed = email.trim()
        if (trimmed.isEmpty()) return EmailError.REQUIRED
        if (trimmed.any { it.isWhitespace() }) return EmailError.INVALID
        val parts = trimmed.split('@')
        if (parts.size != 2) return EmailError.INVALID
        val (local, domain) = parts
        if (local.isEmpty()) return EmailError.INVALID
        val labels = domain.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() }) return EmailError.INVALID
        return null
    }
}
