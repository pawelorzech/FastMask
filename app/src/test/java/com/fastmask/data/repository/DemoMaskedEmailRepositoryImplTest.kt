package com.fastmask.data.repository

import com.fastmask.domain.model.CreateMaskedEmailParams
import com.fastmask.domain.model.UpdateMaskedEmailParams
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Demo mode must treat parameters the way the real repository does, otherwise
 * it shows behaviour an account would never produce.
 */
class DemoMaskedEmailRepositoryImplTest {

    private val repository = DemoMaskedEmailRepositoryImpl()

    private suspend fun create(params: CreateMaskedEmailParams) =
        repository.createMaskedEmail(params).getOrThrow()

    @Test
    fun `create normalises the origin and turns blank fields into null`() = runTest {
        val mask = create(
            CreateMaskedEmailParams(
                forDomain = "Shop.Example.com/cart?x=1",
                description = "   ",
                emailPrefix = " ",
                url = "",
            )
        )

        assertEquals("https://shop.example.com", mask.forDomain)
        assertNull(mask.description)
        assertNull(mask.url)
        assertTrue("a blank prefix is generated instead", !mask.emailPrefix.isNullOrBlank())
    }

    @Test
    fun `create refuses an origin the real repository would refuse`() = runTest {
        val result = repository.createMaskedEmail(CreateMaskedEmailParams(forDomain = "ftp://example.com"))

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `update normalises the origin`() = runTest {
        val mask = create(CreateMaskedEmailParams(forDomain = "old.example.com"))

        repository.updateMaskedEmail(mask.id, UpdateMaskedEmailParams(forDomain = "NEW.example.com/page")).getOrThrow()

        assertEquals("https://new.example.com", repository.getMaskedEmail(mask.id).getOrThrow()!!.forDomain)
    }

    @Test
    fun `update with an empty url clears it and a null url leaves it`() = runTest {
        val mask = create(CreateMaskedEmailParams(url = "https://example.com/a"))

        repository.updateMaskedEmail(mask.id, UpdateMaskedEmailParams(description = "note")).getOrThrow()
        assertEquals("https://example.com/a", repository.getMaskedEmail(mask.id).getOrThrow()!!.url)

        repository.updateMaskedEmail(mask.id, UpdateMaskedEmailParams(url = "")).getOrThrow()
        assertNull(repository.getMaskedEmail(mask.id).getOrThrow()!!.url)
    }

    @Test
    fun `update with a new url sets it`() = runTest {
        val mask = create(CreateMaskedEmailParams(url = "https://example.com/a"))

        repository.updateMaskedEmail(mask.id, UpdateMaskedEmailParams(url = "https://example.com/b")).getOrThrow()

        assertEquals("https://example.com/b", repository.getMaskedEmail(mask.id).getOrThrow()!!.url)
    }

    @Test
    fun `update refuses an invalid origin and changes nothing`() = runTest {
        val mask = create(CreateMaskedEmailParams(forDomain = "example.com"))

        val result = repository.updateMaskedEmail(mask.id, UpdateMaskedEmailParams(forDomain = "ftp://x.example"))

        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        assertEquals("https://example.com", repository.getMaskedEmail(mask.id).getOrThrow()!!.forDomain)
    }
}
