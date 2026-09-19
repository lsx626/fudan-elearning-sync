package edu.fudan.elearning.sync.office

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.awt.Dimension
import java.awt.geom.Rectangle2D
import java.io.File
import java.io.FileOutputStream

/**
 * Office 渲染插桩测试：在真实 Android 运行时（ART）上验证 POI 解析 + Canvas 渲染。
 *
 * 这是「POI 能否在 Android 上运行」的端到端闸门：本机 JVM 无法加载 java.awt 桩
 * （禁止定义 java.* 包），只有设备上的 PathClassLoader 可以。本测试同时覆盖：
 *  - xmlbeans 的 OOXML 解析路径（依赖 javax.xml.stream 桩）；
 *  - POIFS 的旧格式路径（.xls）；
 *  - java.awt.* 桩类在解析坐标/颜色时被正确解析；
 *  - [PageRenderer] 用 Android Canvas 真正产出位图。
 *
 * 夹具全部由 POI 写 API 在测试内现造，不引入二进制文件，也不使用真实课程资料。
 * .doc/.ppt 由本机 Office COM 另存为旧格式后 adb push 到外部专属目录，存在时
 * 才执行（assume），缺失时跳过该用例。
 */
@RunWith(AndroidJUnit4::class)
class OfficeRendererInstrumentedTest {

    private lateinit var ctx: Context
    private lateinit var fixtures: File

    @Before
    fun setup() {
        ctx = ApplicationProvider.getApplicationContext()
        fixtures = File(ctx.cacheDir, "office_fixtures").apply { mkdirs() }
    }

    private fun write(name: String, block: (FileOutputStream) -> Unit): File {
        val file = File(fixtures, name)
        FileOutputStream(file).use(block)
        return file
    }

    /** 断言可解析且第一页能渲染成合法位图。 */
    private fun assertRenderable(file: File, minPages: Int) {
        val result = OfficeExtractor.extract(ctx, file)
        assertTrue("解析 ${file.name} 应成功，实际: $result", result is OfficeParseResult.Success)
        val pages = (result as OfficeParseResult.Success).pages
        assertTrue("${file.name} 页数应 >= $minPages，实际 ${pages.size}", pages.size >= minPages)
        // 关键：在 Android 上真正把第一页画成位图（触发全部 java.awt 桩与 Canvas 路径）
        val bmp = PageRenderer.renderPage(pages.first())
        assertNotNull("渲染 ${file.name} 第一页不应返回 null", bmp)
        assertTrue("${file.name} 位图宽度应 > 0", bmp!!.width > 0)
        assertTrue("${file.name} 位图高度应 > 0", bmp.height > 0)
        assertTrue("${file.name} 位图应可绘制", !bmp.isRecycled)
        bmp.recycle()
    }

    @Test
    fun pptx_parsesAndRendersOnDevice() {
        val file = write("slides.pptx") { out ->
            XMLSlideShow().use { show ->
                show.setPageSize(Dimension(720, 405))
                val slide1 = show.createSlide()
                val box1 = slide1.createTextBox()
                box1.setAnchor(Rectangle2D.Double(50.0, 40.0, 400.0, 80.0))
                box1.setText("第一章 概述")
                val slide2 = show.createSlide()
                val box2 = slide2.createTextBox()
                box2.setAnchor(Rectangle2D.Double(50.0, 40.0, 400.0, 80.0))
                box2.setText("第二章 结论")
                show.write(out)
            }
        }
        assertRenderable(file, minPages = 2)
        // 坐标换算：50pt * (目标宽/720pt) 应与目标宽成正比
        val pages = (OfficeExtractor.extract(ctx, file) as OfficeParseResult.Success).pages
        assertEquals(2, pages.size)
        val box = pages[0].items.filterIsInstance<PageItem.TextBlock>().first()
        val targetW = OfficeExtractor.renderWidth(ctx)
        assertEquals(50f * targetW / 720f, box.rect.left, 2f)
    }

    @Test
    fun docx_parsesPaginatesAndRendersOnDevice() {
        val file = write("doc.docx") { out ->
            XWPFDocument().use { doc ->
                val p1 = doc.createParagraph()
                p1.createRun().setText("这是第一段正文内容，用于验证 Word 分页与渲染。")
                p1.runs.first().setBold(true)
                repeat(60) { i ->
                    val p = doc.createParagraph()
                    p.createRun().setText("填充段落 $i：用于产生多页文档，验证分页器在真机上的行为。")
                }
                doc.write(out)
            }
        }
        // Word 走分页路径（StaticLayout），多段至少一页
        assertRenderable(file, minPages = 1)
    }

    @Test
    fun xlsx_parsesAndRendersOnDevice() {
        val file = write("sheet.xlsx") { out ->
            XSSFWorkbook().use { wb ->
                val sheet = wb.createSheet("数据")
                val header = sheet.createRow(0)
                header.createCell(0).setCellValue("姓名")
                header.createCell(1).setCellValue("分数")
                val first = sheet.createRow(1)
                first.createCell(0).setCellValue("张三")
                first.createCell(1).setCellValue(95.0)
                sheet.addMergedRegion(CellRangeAddress(0, 0, 0, 1))
                wb.write(out)
            }
        }
        assertRenderable(file, minPages = 1)
    }

    @Test
    fun xls_legacy_parsesAndRendersOnDevice() {
        val file = write("legacy.xls") { out ->
            HSSFWorkbook().use { wb ->
                val sheet = wb.createSheet("旧格式")
                val r0 = sheet.createRow(0)
                r0.createCell(0).setCellValue("旧版单元格")
                r0.createCell(1).setCellValue("表头")
                val r1 = sheet.createRow(1)
                r1.createCell(0).setCellValue("数据行")
                r1.createCell(1).setCellValue(42.0)
                wb.write(out)
            }
        }
        assertRenderable(file, minPages = 1)
    }

    /** 旧格式 .doc：由本机 Office COM 另存后 adb push，缺失时跳过。 */
    @Test
    fun doc_legacy_parsesAndRendersOnDevice() {
        val file = File(ctx.getExternalFilesDir("fixtures"), "legacy.doc")
        assumeTrue("跳过：未在 ${file.parent} 发现 legacy.doc", file.exists())
        assertRenderable(file, minPages = 1)
    }

    /**
     * 旧格式 .ppt：POI 的 HSLFSlideShow 提供无参构造，可直接在设备上造出旧格式
     * 幻灯片（走 scratchpad 的 POIFS 路径），无需宿主机 Office COM。
     */
    @Test
    fun ppt_legacy_parsesAndRendersOnDevice() {
        val file = write("legacy.ppt") { out ->
            HSLFSlideShow().use { show ->
                show.setPageSize(Dimension(720, 540))
                val slide1 = show.createSlide()
                val box1 = slide1.createTextBox()
                box1.setAnchor(Rectangle2D.Double(50.0, 40.0, 400.0, 80.0))
                box1.setText("Legacy slide one")
                val slide2 = show.createSlide()
                val box2 = slide2.createTextBox()
                box2.setAnchor(Rectangle2D.Double(50.0, 40.0, 400.0, 80.0))
                box2.setText("Legacy slide two")
                show.write(out)
            }
        }
        assertRenderable(file, minPages = 2)
    }

    @Test
    fun corruptFile_reportsFailureNotCrash() {
        val file = write("broken.pptx") { out ->
            out.write(ByteArray(1024) { (it % 251).toByte() })
        }
        val result = OfficeExtractor.extract(ctx, file)
        assertTrue("损坏文件应返回 Failed，实际: $result", result is OfficeParseResult.Failed)
    }

    @Test
    fun unsupportedExtension_reportsUnsupported() {
        val file = write("notes.odt") { out -> out.write("dummy".toByteArray()) }
        val result = OfficeExtractor.extract(ctx, file)
        assertTrue("不支持的扩展名应返回 Unsupported，实际: $result", result is OfficeParseResult.Unsupported)
    }
    /**
     * 真实 Office 文档（本机 Word/PowerPoint COM 生成，含表格/图片/富文本）
     * 验证 doc/docx/ppt 的「完整页面」渲染：表格块、图片块都被解析并画出来。
     * 夹具由 adb push 放到 fixtures 目录，缺失时跳过。
     */
    @Test
    fun officeFixtures_renderTablesAndPictures() {
        val fixturesDir = ctx.getExternalFilesDir("fixtures")

        // ---- .doc（旧二进制）：表格 + 逐段字符格式（加粗/红色）----
        val doc = File(fixturesDir, "legacy.doc")
        assumeTrue("跳过：未在 ${doc.parent} 发现 legacy.doc", doc.exists())
        val docRes = OfficeExtractor.extract(ctx, doc)
        assertTrue("解析 legacy.doc 应成功，实际: $docRes", docRes is OfficeParseResult.Success)
        val docPages = (docRes as OfficeParseResult.Success).pages
        val docTables = docPages.flatMap { it.items }.filterIsInstance<PageItem.Table>()
        assertTrue(".doc 应至少渲染出一个表格，首页元素：${docPages.first().items.map { it::class.simpleName }}",
            docTables.isNotEmpty())
        val docCellText = docTables.joinToString { tbl ->
            tbl.rows.joinToString { it.cells.joinToString { c -> c.text } }
        }
        assertTrue(".doc 表格单元应含 Alice 与 Score：$docCellText",
            docCellText.contains("Alice") && docCellText.contains("Score"))
        val docBmp = PageRenderer.renderPage(docPages.first())
        assertTrue(".doc 首页位图应合法", docBmp.width > 0 && docBmp.height > 0)
        docBmp.recycle()

        // ---- .docx：内嵌图片 + 表格 ----
        val docx = File(fixturesDir, "modern.docx")
        assumeTrue("跳过：未在 ${docx.parent} 发现 modern.docx", docx.exists())
        val docxRes = OfficeExtractor.extract(ctx, docx)
        assertTrue("解析 modern.docx 应成功，实际: $docxRes", docxRes is OfficeParseResult.Success)
        val docxItems = (docxRes as OfficeParseResult.Success).pages.flatMap { it.items }
        assertTrue(".docx 应含图片块", docxItems.any { it is PageItem.Image })
        assertTrue(".docx 应含表格块", docxItems.any { it is PageItem.Table })

        // ---- .ppt（真实旧格式）：形状 + 表格 + 图片 ----
        val ppt = File(fixturesDir, "legacy.ppt")
        assumeTrue("跳过：未在 ${ppt.parent} 发现 legacy.ppt", ppt.exists())
        val pptRes = OfficeExtractor.extract(ctx, ppt)
        assertTrue("解析 legacy.ppt 应成功，实际: $pptRes", pptRes is OfficeParseResult.Success)
        val pptPages = (pptRes as OfficeParseResult.Success).pages
        assertEquals("legacy.ppt 应为 2 页，实际 ${pptPages.size}", 2, pptPages.size)
        val pptItems = pptPages.flatMap { it.items }
        assertTrue(".ppt 应含图片块", pptItems.any { it is PageItem.Image })
        assertTrue(".ppt 应含表格块", pptItems.any { it is PageItem.Table })
        val pptBmp = PageRenderer.renderPage(pptPages.first())
        assertTrue(".ppt 首页位图应合法", pptBmp.width > 0 && pptBmp.height > 0)
        pptBmp.recycle()
    }
}
