package com.androidlearning.sliide.core.data.networking

/**
 * Every GoRest API string in one place: routes, query parameters, headers and paging. Routes are relative with no
 * leading slash, so they resolve under the base URL's `/public/v2/` path (see [HttpClientFactory]). The base URL and
 * token themselves are build-time values in the generated `BuildKonfig`.
 */
object GoRestApi {
    /** `GET`/`POST` users collection. */
    const val USERS = "users"

    /** `PATCH`/`DELETE` a single user. */
    fun user(id: Long): String = "$USERS/$id"

    /** Users per page for both last-page requests. */
    const val PAGE_SIZE = 20

    const val FIRST_PAGE = 1

    object Query {
        const val PAGE = "page"
        const val PER_PAGE = "per_page"
    }

    object Headers {
        /** Total page count of a paged collection. */
        const val PAGE_COUNT = "X-Pagination-Pages"
    }

    const val BEARER_PREFIX = "Bearer "
}
