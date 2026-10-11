package com.archive.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Pawchive API v1, exactly as documented in
 * https://pawchive.pw/api/swagger_schema (OpenAPI 3.0.1, captured in
 * docs/api/pawchive_openapi.json). Pagination is offset based: `o` in steps
 * of 50 (verified live; the server ignores `q` shorter than 3 chars).
 */
interface PawchiveApi {

    @GET("creators")
    suspend fun creators(): List<CreatorDto>

    @GET("posts")
    suspend fun posts(
        @Query("q") q: String? = null,
        @Query("o") offset: Int = 0,
    ): List<PostDto>

    @GET("{service}/user/{creatorId}")
    suspend fun creatorPosts(
        @Path("service") service: String,
        @Path("creatorId") creatorId: String,
        @Query("q") q: String? = null,
        @Query("tag") tag: String? = null,
        @Query("o") offset: Int = 0,
    ): List<PostDto>

    @GET("{service}/user/{creatorId}/post/{postId}")
    suspend fun postDetail(
        @Path("service") service: String,
        @Path("creatorId") creatorId: String,
        @Path("postId") postId: String,
    ): PostDto

    @GET("{service}/user/{creatorId}/profile")
    suspend fun profile(
        @Path("service") service: String,
        @Path("creatorId") creatorId: String,
    ): ProfileDto

    @GET("{service}/user/{creatorId}/announcements")
    suspend fun announcements(
        @Path("service") service: String,
        @Path("creatorId") creatorId: String,
    ): List<AnnouncementDto>

    @GET("{service}/user/{creatorId}/tags")
    suspend fun creatorTags(
        @Path("service") service: String,
        @Path("creatorId") creatorId: String,
    ): List<CreatorTagDto>

    @GET("{service}/user/{creatorId}/links")
    suspend fun creatorLinks(
        @Path("service") service: String,
        @Path("creatorId") creatorId: String,
    ): List<ProfileDto>

    @GET("{service}/user/{creatorId}/post/{postId}/comments")
    suspend fun comments(
        @Path("service") service: String,
        @Path("creatorId") creatorId: String,
        @Path("postId") postId: String,
    ): List<CommentDto>

    @GET("account/favorites")
    suspend fun accountFavorites(
        @Query("type") type: String? = null,
    ): List<FavoriteDto>
}
