package com.ethran.notable.io

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ethran.notable.data.events.AppEventBus
import com.ethran.notable.data.events.DefaultAppEventBus
import com.ethran.notable.data.db.*
import com.ethran.notable.data.model.PageLayer
import com.ethran.notable.data.model.PageLayers
import com.ethran.notable.testing.TestDatabaseFactory
import com.ethran.notable.utils.AppResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class XoppImportTest {

    private lateinit var db: AppDatabase
    private lateinit var importEngine: ImportEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = TestDatabaseFactory.createInMemory(context)

        // Manual DI for a focused integration test
        val pageRepo = PageRepository(db.pageDao())
        val bookRepo = BookRepository(db.notebookDao(), db.pageDao())
        val strokeRepo = StrokeRepository(db.strokeDao())
        val imageRepo = ImageRepository(db.ImageDao())
        val appEventBus = DefaultAppEventBus()

        val xoppFile = XoppFile(context, pageRepo, bookRepo, appEventBus)

        importEngine = ImportEngine(
            context, pageRepo, bookRepo, strokeRepo, imageRepo, appEventBus
        ).apply {
            this.xoppFile = xoppFile
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test(timeout = 120000)
    fun importNotableXopp_fromAssets_createsNotebookAndStrokes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        
        // 1. Extract asset to a temporary file to get a Uri
        val testFile = File(context.cacheDir, "test_notebook.xopp")
        testContext.assets.open("test_notebook.xopp").use { input ->
            testFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        val uri = Uri.fromFile(testFile)

        // 2. Perform import (passing .xopp title helps recognition if mime-type is unknown)
        val result = importEngine.import(uri, ImportOptions(bookTitle = "test_notebook.xopp"))

        // 3. Assertions
        assertTrue("Import failed: $result", result is AppResult.Success)
        val importedPageIds = (result as AppResult.Success).data
        assertTrue("No pages imported", importedPageIds.isNotEmpty())

        val notebooks = db.notebookDao().getAll()
        assertEquals("Should create one notebook", 1, notebooks.size)
        
        val book = notebooks.first()
        // ImportEngine should have stripped .xopp if it was present in the name or detected by type
        assertEquals("test notebook", book.title)
        assertEquals("Page count mismatch", importedPageIds.size, book.pageIds.size)
        
        // Verify strokes were imported for at least one page
        var strokesFound = false
        for (pageId in importedPageIds) {
            val pageWithData = db.pageDao().getPageWithDataById(pageId)
            if (pageWithData?.strokes?.isNotEmpty() == true) {
                strokesFound = true
                break
            }
        }
        assertTrue("No strokes found in any of the imported pages", strokesFound)
        
        // Detailed check on the first notebook entry
        assertNotNull("Book ID should not be null", book.id)
        assertEquals("Notebook ID mismatch in page", book.id, db.pageDao().getById(importedPageIds.first())?.notebookId)
    }
    
    @Test(timeout = 120000)
    fun importNotableXopp_withExplicitTitle_stripsExtension() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        
        val testFile = File(context.cacheDir, "Notable_Title.xopp")
        testContext.assets.open("test_notebook.xopp").use { input ->
            testFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        val uri = Uri.fromFile(testFile)

        // Pass a title WITH extension explicitly
        val result = importEngine.import(uri, ImportOptions(bookTitle = "ManualTitle.xopp"))
        assertTrue("Import failed: $result", result is AppResult.Success)
        
        val book = db.notebookDao().getAll().first { it.title == "ManualTitle" }
        assertNotNull("Should find notebook with stripped title", book)
    }
    
    @Test(timeout = 120000)
    fun importNotableXopp_verifiesDataIntegrity() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testContext = InstrumentationRegistry.getInstrumentation().context
        
        val testFile = File(context.cacheDir, "Notable_Integrity.xopp")
        testContext.assets.open("test_notebook.xopp").use { input ->
            testFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        val uri = Uri.fromFile(testFile)

        val result = importEngine.import(uri, ImportOptions(bookTitle = "IntegrityCheck.xopp"))
        assertTrue("Import failed: $result", result is AppResult.Success)
        val importedPageIds = (result as AppResult.Success).data
        
        val book = db.notebookDao().getAll().first { it.title == "IntegrityCheck" }
        
        // Check that page order in notebook matches imported order
        assertEquals("Page sequence mismatch", importedPageIds, book.pageIds)
        
        // Check that each page actually belongs to the notebook
        importedPageIds.forEach { pageId ->
            val page = db.pageDao().getById(pageId)
            assertEquals("Page $pageId does not point to correct notebook", book.id, page?.notebookId)
        }
    }

    @Test(timeout = 120000)
    fun importXopp_turnsEachLayerIntoAPageLayer() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val stroke = """<stroke tool="pen" color="black" width="1">10 10 20 20 30 30</stroke>"""
        val xml = """<?xml version="1.0" standalone="no"?>
            <xournal creator="test" fileversion="4">
            <page width="595" height="842"><background type="solid" color="#ffffffff" style="plain"/>
            <layer name="Sketch">$stroke</layer><layer>$stroke$stroke</layer>
            </page>
            <page width="595" height="842"><background type="solid" color="#ffffffff" style="plain"/>
            <layer>$stroke</layer>
            </page>
            </xournal>"""
        val testFile = File(context.cacheDir, "Layers.xopp")
        java.util.zip.GZIPOutputStream(testFile.outputStream()).use { it.write(xml.toByteArray()) }

        val result = importEngine.import(Uri.fromFile(testFile), ImportOptions(bookTitle = "Layers.xopp"))
        assertTrue("Import failed: $result", result is AppResult.Success)
        val (layered, plain) = (result as AppResult.Success).data

        val layeredPage = db.pageDao().getPageWithDataById(layered)!!
        assertEquals(
            listOf(PageLayer(id = 0, name = "Sketch"), PageLayer(id = 1, name = "Layer 2")),
            PageLayers.decode(layeredPage.page.layers)
        )
        assertEquals(listOf(0, 1, 1), layeredPage.strokes.map { it.layer }.sorted())

        // A single unnamed layer stays the implicit default layer.
        val plainPage = db.pageDao().getPageWithDataById(plain)!!
        assertEquals(null, plainPage.page.layers)
        assertEquals(listOf(0), plainPage.strokes.map { it.layer })
    }
}
