package com.androidlearning.sliide.users.presentation.composables

import com.androidlearning.sliide.users.presentation.users.UserUi
import com.androidlearning.sliide.users.presentation.util.RelativeTime

/** Realistic sample data for previews only. */
internal val previewUsers =
    listOf(
        UserUi(7_401_001, "Jane Doe", "jane.doe@example.com", "JD", true, RelativeTime.Minutes(5), false),
        UserUi(7_401_002, "Rahul Sharma", "rahul.sharma@example.in", "RS", false, RelativeTime.Hours(3), false),
        UserUi(-1, "Amélie Laurent", "amelie.laurent@example.fr", "AL", true, RelativeTime.JustNow, true),
        UserUi(
            7_401_004,
            "Maximilian Alexander von Hohenberg-Lichtenstein",
            "maximilian.alexander.von.hohenberg@extremely-long-domain.example.com",
            "MH",
            true,
            RelativeTime.Days(2),
            false,
        ),
        UserUi(7_401_005, "Chen Wei", "chen.wei@example.cn", "CW", true, RelativeTime.Months(1), false),
        UserUi(7_401_006, "Fatima Al-Sayed", "fatima.alsayed@example.ae", "FA", false, RelativeTime.Years(1), false),
    )
