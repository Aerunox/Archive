package com.archive.app.util

/**
 * 搜索结果的相关度打分（纯函数，便于单测）。
 *
 * 站点接口不提供排序参数，所以「相关度」在客户端计算：命中位置越"标题化"权重越高，
 * 全词命中（整串关键词）权重最高；同一分数时由调用方按发布时间兜底排序。
 *
 * 权重设计：
 *  - 标题包含完整关键词：+100
 *  - 标题包含单个关键词：每词 +40（最多计 4 个词）
 *  - 创作者名/ID 包含单词：每词 +20
 *  - 摘要/正文包含单词：每词 +10
 * 分值区间不做归一化，只用来排序。
 */
object SearchRelevance {

    fun score(
        query: String,
        title: String?,
        creatorName: String?,
        excerpt: String?,
    ): Int {
        val q = query.trim()
        if (q.isEmpty()) return 0
        val titleLower = title.orEmpty().lowercase()
        val creatorLower = creatorName.orEmpty().lowercase()
        val bodyLower = excerpt.orEmpty().lowercase()

        var score = 0
        val whole = q.lowercase()
        if (titleLower.contains(whole)) score += 100

        val terms = whole.split(Regex("""\s+""")).filter { it.isNotBlank() }.take(4)
        terms.forEach { term ->
            if (titleLower.contains(term)) score += 40
            if (creatorLower.contains(term)) score += 20
            if (bodyLower.contains(term)) score += 10
        }
        return score
    }
}
