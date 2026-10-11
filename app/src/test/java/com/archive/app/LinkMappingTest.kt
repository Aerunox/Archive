package com.archive.app

import com.archive.app.data.remote.ProfileDto
import com.archive.app.data.toLinkDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 回归：/links 列表必须保留**每条关联记录自身的 service/id**。
 *
 * 曾经复用 profile 端点的映射（用路径参数当身份），导致所有关联账号都被映射成当前
 * 作者，Lazy 列表出现重复 key 而崩溃，且点击跳转到当前作者页。
 */
class LinkMappingTest {

    @Test
    fun `link entries keep their own identity`() {
        val links = listOf(
            ProfileDto(id = "111", service = "fanbox", name = "Alt Account", updated = "2026-01-01T00:00:00"),
            ProfileDto(id = "222", service = "patreon", name = "Main", updated = null),
        )
        val mapped = links.mapNotNull { it.toLinkDomain() }
        assertEquals(listOf("fanbox/111", "patreon/222"), mapped.map { it.service + "/" + it.id })
        assertEquals("Alt Account", mapped[0].name)
    }

    @Test
    fun `entries without id or service are dropped instead of collapsing to the path creator`() {
        assertNull(ProfileDto(id = null, service = "patreon", name = "x").toLinkDomain())
        assertNull(ProfileDto(id = "", service = "patreon", name = "x").toLinkDomain())
        assertNull(ProfileDto(id = "1", service = null, name = "x").toLinkDomain())
    }

    @Test
    fun `name falls back to the id when the API omits it`() {
        val mapped = ProfileDto(id = "999", service = "patreon", name = null).toLinkDomain()
        assertEquals("999", mapped?.name)
    }
}
