package io.github.aixtin.nyrallite.kernel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceOwnershipTest {

    private interface Demo

    private class DemoImpl(val name: String) : Demo

    @Test
    fun releaseRemovesOnlyOwnInstances() {
        val registry = DefaultServiceRegistry()
        val owner = OwnedServices(
            PluginServiceView(registry, PluginManifest(id = "p1")),
            registry,
        )
        val keep = DemoImpl("keep")
        val mine = DemoImpl("mine")
        registry.provide(Demo::class, keep)
        owner.provide(Demo::class, mine)

        assertEquals(2, registry.getAll(Demo::class).size)

        owner.release()

        val left = registry.getAll(Demo::class)
        assertEquals(1, left.size)
        assertTrue(left.single() === keep)
    }

    @Test
    fun releaseDropsEmptyTypeSlot() {
        val registry = DefaultServiceRegistry()
        val owner = OwnedServices(
            PluginServiceView(registry, PluginManifest(id = "p2")),
            registry,
        )
        owner.provide(Demo::class, DemoImpl("only"))

        owner.release()

        assertNull(registry.getOrNull(Demo::class))
    }

    @Test
    fun releaseIsIdempotent() {
        val registry = DefaultServiceRegistry()
        val owner = OwnedServices(
            PluginServiceView(registry, PluginManifest(id = "p3")),
            registry,
        )
        owner.provide(Demo::class, DemoImpl("once"))

        owner.release()
        owner.release()

        assertNull(registry.getOrNull(Demo::class))
    }
}
