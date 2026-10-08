package com.notiforge.app.data.repository

import com.notiforge.app.data.local.EventLogDao
import com.notiforge.app.data.local.EventLogEntity
import com.notiforge.app.data.local.TemplateDao
import com.notiforge.app.data.local.TemplateEntity
import com.notiforge.app.domain.model.NotiTemplate
import com.notiforge.app.domain.model.PresetCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TemplateRepository(
    private val templateDao: TemplateDao,
    private val eventLogDao: EventLogDao
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val templatesFlow: Flow<List<NotiTemplate>> =
        templateDao.observeAllTemplates().map { list ->
            list.map { it.toDomain(json) }
        }

    val recentLogsFlow: Flow<List<EventLogEntity>> =
        eventLogDao.observeRecentLogs()

    suspend fun ensurePresetsSeeded() {
        val entities = PresetCatalog.defaultPresets.map { TemplateEntity.fromDomain(it, json) }
        templateDao.insertAllIgnore(entities)

        // Ensure any legacy typo ("Quick Noe" -> "Quick Note") is repaired
        // and upgrade existing records lacking populated blocksJson
        val existing = templateDao.getAllTemplates()
        for (entity in existing) {
            val fixedName = entity.name.replace(Regex("Quick Noe", RegexOption.IGNORE_CASE), "Quick Note")
                .let { if (entity.slug == "quick_note" && entity.isPreset) "Quick Note" else it }
            val fixedDesc = entity.description.replace(Regex("Quick Noe", RegexOption.IGNORE_CASE), "Quick Note")
            val fixedTitle = entity.defaultTitle.replace(Regex("Quick Noe", RegexOption.IGNORE_CASE), "Quick Note")
            val needsBlocksUpgrade = entity.blocksJson.isBlank()
            if (fixedName != entity.name || fixedDesc != entity.description || fixedTitle != entity.defaultTitle || needsBlocksUpgrade) {
                val domain = entity.copy(
                    name = fixedName,
                    description = fixedDesc,
                    defaultTitle = fixedTitle
                ).toDomain(json)
                templateDao.update(TemplateEntity.fromDomain(domain, json))
            }
        }
    }

    suspend fun getTemplateById(id: Long): NotiTemplate? {
        return templateDao.getById(id)?.toDomain(json)
    }

    suspend fun getTemplateBySlugOrFallback(slug: String?): NotiTemplate {
        ensurePresetsSeeded()
        if (!slug.isNullOrBlank()) {
            templateDao.getBySlug(slug.trim())?.toDomain(json)?.let { return it }
            PresetCatalog.defaultPresets.find { it.slug.equals(slug.trim(), ignoreCase = true) }?.let { return it }
        }
        return PresetCatalog.defaultPresets.first()
    }

    suspend fun saveTemplate(template: NotiTemplate): Long {
        val baseSlug = template.slug.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .take(64)
            .ifEmpty { "custom_${System.currentTimeMillis() % 10000}" }

        val existingBySlug = templateDao.getBySlug(baseSlug)
        val resolvedTemplate = when {
            existingBySlug != null && existingBySlug.id == template.id -> {
                template.copy(
                    slug = baseSlug,
                    isPreset = existingBySlug.isPreset
                )
            }
            existingBySlug != null && existingBySlug.isPreset && !template.isPreset -> {
                // Disambiguate custom template slug so it never overwrites a built-in preset
                val uniqueSlug = "${baseSlug}_custom_${(100..999).random()}"
                template.copy(slug = uniqueSlug, isPreset = false)
            }
            existingBySlug != null -> {
                template.copy(
                    id = existingBySlug.id,
                    slug = baseSlug,
                    isPreset = existingBySlug.isPreset
                )
            }
            else -> template.copy(slug = baseSlug)
        }

        val entity = TemplateEntity.fromDomain(resolvedTemplate, json)
        return templateDao.insertOrReplace(entity)
    }

    suspend fun duplicateTemplate(template: NotiTemplate): Long {
        val copySlug = "${template.slug}_copy_${(100..999).random()}"
        val duplicate = template.copy(
            id = 0L,
            slug = copySlug,
            name = "${template.name} (Copy)",
            isPreset = false
        )
        return saveTemplate(duplicate)
    }

    suspend fun deleteTemplate(template: NotiTemplate) {
        if (template.id != 0L && !template.isPreset) {
            templateDao.delete(TemplateEntity.fromDomain(template, json))
        }
    }

    suspend fun exportTemplatesJson(): String {
        ensurePresetsSeeded()
        val all = templateDao.getAllTemplates().map { it.toDomain(json) }
        return json.encodeToString(all)
    }

    suspend fun importTemplatesJson(rawJson: String): Int {
        ensurePresetsSeeded()
        val imported = json.decodeFromString<List<NotiTemplate>>(rawJson)
        val presetSlugs = PresetCatalog.defaultPresets.map { it.slug }.toSet()
        var count = 0
        for (item in imported) {
            val normalizedSlug = item.slug.trim().lowercase()
            val existing = templateDao.getBySlug(normalizedSlug)
            val isBuiltInPreset = normalizedSlug in presetSlugs || existing?.isPreset == true
            saveTemplate(
                item.copy(
                    id = existing?.id ?: 0L,
                    slug = normalizedSlug,
                    isPreset = isBuiltInPreset
                )
            )
            count++
        }
        return count
    }

    suspend fun logEvent(
        direction: String,
        action: String,
        notificationTag: String,
        summary: String
    ) {
        eventLogDao.insertLog(
            EventLogEntity(
                direction = direction,
                action = action,
                notificationTag = notificationTag,
                summary = summary
            )
        )
        eventLogDao.pruneOldLogs()
    }

    suspend fun clearLogs() {
        eventLogDao.clearAll()
    }
}
