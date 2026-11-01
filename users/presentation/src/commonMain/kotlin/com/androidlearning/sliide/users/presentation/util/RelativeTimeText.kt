package com.androidlearning.sliide.users.presentation.util

import androidx.compose.runtime.Composable
import com.androidlearning.sliide.users.presentation.generated.resources.Res
import com.androidlearning.sliide.users.presentation.generated.resources.users_added_days
import com.androidlearning.sliide.users.presentation.generated.resources.users_added_hours
import com.androidlearning.sliide.users.presentation.generated.resources.users_added_just_now
import com.androidlearning.sliide.users.presentation.generated.resources.users_added_minutes
import com.androidlearning.sliide.users.presentation.generated.resources.users_added_months
import com.androidlearning.sliide.users.presentation.generated.resources.users_added_years
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** "Added 5 minutes ago", with the plural form chosen per locale. */
@Composable
fun RelativeTime.asAddedText(): String =
    when (this) {
        RelativeTime.JustNow -> stringResource(Res.string.users_added_just_now)
        is RelativeTime.Minutes -> pluralStringResource(Res.plurals.users_added_minutes, count, count)
        is RelativeTime.Hours -> pluralStringResource(Res.plurals.users_added_hours, count, count)
        is RelativeTime.Days -> pluralStringResource(Res.plurals.users_added_days, count, count)
        is RelativeTime.Months -> pluralStringResource(Res.plurals.users_added_months, count, count)
        is RelativeTime.Years -> pluralStringResource(Res.plurals.users_added_years, count, count)
    }
