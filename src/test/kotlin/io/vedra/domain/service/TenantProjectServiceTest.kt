package io.vedra.domain.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.vedra.domain.model.ChannelProject
import io.vedra.domain.model.ProjectVisibility
import io.vedra.domain.model.TenantProject
import io.vedra.domain.repository.project.ChannelProjectRepository
import io.vedra.domain.repository.project.TenantProjectRepository
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TenantProjectServiceTest {
    private val tenantProjectRepository: TenantProjectRepository = mockk()
    private val channelProjectRepository: ChannelProjectRepository = mockk()
    private val service = TenantProjectService(tenantProjectRepository, channelProjectRepository)

    @Test
    fun `createPrivateAndLink creates project and links channel`() {
        val tenantId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        every { tenantProjectRepository.findByTenantIdAndName(tenantId, "P1") } returns null
        every { tenantProjectRepository.findPublicByTenantId(tenantId) } returns null
        every { tenantProjectRepository.save(any()) } returns
            TenantProject(id = projectId, tenantId = tenantId, name = "P1", visibility = ProjectVisibility.PRIVATE)
        every { channelProjectRepository.findByChannelId("C1") } returns null
        every { channelProjectRepository.save(any()) } answers { firstArg() }

        val result = service.createPrivateAndLink(tenantId, "C1", "P1")

        assertTrue(result is TenantProjectService.CreateResult.Success)
        verify { channelProjectRepository.save(match { it.channelId == "C1" && it.projectId == projectId }) }
    }

    @Test
    fun `createPrivateAndLink with AlreadyExists does not touch link`() {
        val tenantId = UUID.randomUUID()
        every { tenantProjectRepository.findByTenantIdAndName(tenantId, "P1") } returns
            TenantProject(id = UUID.randomUUID(), tenantId = tenantId, name = "P1")

        val result = service.createPrivateAndLink(tenantId, "C1", "P1")

        assertTrue(result is TenantProjectService.CreateResult.AlreadyExists)
        verify(exactly = 0) { channelProjectRepository.save(any()) }
    }

    @Test
    fun `second create on same channel overwrites link but keeps old project`() {
        val tenantId = UUID.randomUUID()
        val oldProjectId = UUID.randomUUID()
        val newProjectId = UUID.randomUUID()
        every { tenantProjectRepository.findByTenantIdAndName(tenantId, "P2") } returns null
        every { tenantProjectRepository.findPublicByTenantId(tenantId) } returns null
        every { tenantProjectRepository.save(any()) } returns
            TenantProject(id = newProjectId, tenantId = tenantId, name = "P2", visibility = ProjectVisibility.PRIVATE)
        every { channelProjectRepository.findByChannelId("C1") } returns ChannelProject("C1", oldProjectId)
        every { channelProjectRepository.deleteByChannelId("C1") } returns Unit
        every { channelProjectRepository.save(any()) } answers { firstArg() }

        val result = service.createPrivateAndLink(tenantId, "C1", "P2")

        assertTrue(result is TenantProjectService.CreateResult.Success)
        verify { channelProjectRepository.deleteByChannelId("C1") }
        verify { channelProjectRepository.save(match { it.channelId == "C1" && it.projectId == newProjectId }) }
    }

    @Test
    fun `getProjectIdsForChannel returns linked plus public`() {
        val tenantId = UUID.randomUUID()
        val linkedId = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        every { channelProjectRepository.findByChannelId("C1") } returns ChannelProject("C1", linkedId)
        every { tenantProjectRepository.findPublicByTenantId(tenantId) } returns
            TenantProject(id = publicId, tenantId = tenantId, name = "Baza ogólna", visibility = ProjectVisibility.PUBLIC)

        assertEquals(listOf(linkedId, publicId), service.getProjectIdsForChannel(tenantId, "C1"))
    }

    @Test
    fun `getProjectIdsForChannel returns only public when unlinked`() {
        val tenantId = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        every { channelProjectRepository.findByChannelId("C1") } returns null
        every { tenantProjectRepository.findPublicByTenantId(tenantId) } returns
            TenantProject(id = publicId, tenantId = tenantId, name = "Baza ogólna", visibility = ProjectVisibility.PUBLIC)

        assertEquals(listOf(publicId), service.getProjectIdsForChannel(tenantId, "C1"))
    }

    @Test
    fun `getProjectIdsForChannel returns empty when no link nor public`() {
        val tenantId = UUID.randomUUID()
        every { channelProjectRepository.findByChannelId("C1") } returns null
        every { tenantProjectRepository.findPublicByTenantId(tenantId) } returns null

        assertEquals(emptyList(), service.getProjectIdsForChannel(tenantId, "C1"))
    }

    @Test
    fun `getPrivateLinkedProjectId returns null for public link`() {
        val tenantId = UUID.randomUUID()
        val publicId = UUID.randomUUID()
        every { channelProjectRepository.findByChannelId("C1") } returns ChannelProject("C1", publicId)
        every { tenantProjectRepository.findByTenantIdAndId(tenantId, publicId.toString()) } returns
            TenantProject(id = publicId, tenantId = tenantId, name = "Baza ogólna", visibility = ProjectVisibility.PUBLIC)

        assertEquals(null, service.getPrivateLinkedProjectId("C1", tenantId))
    }

    @Test
    fun `getChannelStatus resolves name by tenantId`() {
        val tenantId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        every { channelProjectRepository.findByChannelId("C1") } returns ChannelProject("C1", projectId)
        every { tenantProjectRepository.findByTenantId(tenantId) } returns
            listOf(TenantProject(id = projectId, tenantId = tenantId, name = "P1"))

        val status = service.getChannelStatus("C1", tenantId)

        assertEquals("P1", status.projectName)
        assertTrue(status.isLinked)
    }
}
