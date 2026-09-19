package edu.fudan.elearning.sync.office

import org.apache.poi.hslf.usermodel.HSLFSlideShow
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Dimension
import java.awt.geom.Rectangle2D
import java.io.File
import java.io.FileOutputStream

/**
 * Office 解析层单元测试：用 POI 写 API 在测试内现造合成夹具（docx/pptx/xlsx/xls），
 * 喂给我们的提取器，验证页数、文本与坐标换算。不使用真实课程资料。
 *
 * 说明：PageRenderer.paginate 依赖 Android 的 StaticLayout，无法在 JVM 单测中执行，
 * 因此 Word 只验证到 [FlowDocument]；分页与位图渲染由模拟器插桩测试覆盖。
 */
class OfficeExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun write(name: String, block: (FileOutputStream) -> Unit): File {
        val file = tempFolder.newFile(name)
        FileOutputStream(file).use(block)
        return file
    }

    private fun pageTexts(pages: List<DocPage>): String = buildString {
        for (page in pages) {
            for (item in page.items) {
                when (item) {
                    is PageItem.TextBlock -> append(item.paragraphs.joinToString { p -> p.text })
                    is PageItem.Table -> append(item.rows.joinToString { row ->
                        row.cells.joinToString { c -> c.text }
                    })
                    else -> {}
                }
            }
        }
    }

    @Test
    fun pptx_slides_textAndScaling() {
        val file = write("slides.pptx") { out ->
            XMLSlideShow().use { show ->
                // 10in x 5.625in（16:9）= 720pt x 405pt
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

        val pages = SlideExtractor.extract(file, 1080)

        assertEquals(2, pages.size)
        // 页面宽度等于目标宽度，高度按 16:9 等比
        assertEquals(1080, pages[0].widthPx)
        assertEquals(608, pages[0].heightPx)
        val texts = pageTexts(pages)
        assertTrue(texts.contains("第一章 概述"))
        assertTrue(texts.contains("第二章 结论"))
        // 形状坐标按 磅→像素 换算：50pt * (1080/720pt) = 75px
        val box = pages[0].items.filterIsInstance<PageItem.TextBlock>().first()
        assertEquals(75f, box.rect.left, 1f)
        assertEquals(60f, box.rect.top, 1f)
    }

    @Test
    fun pptx_corrupt_throws() {
        val file = write("broken.pptx") { out ->
            out.write(ByteArray(1024) { (it % 251).toByte() })
        }
        // 损坏文件由解析器抛出，交给 OfficeExtractor 报告「解析失败」
        var threw = false
        try {
            SlideExtractor.extract(file, 1080)
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun docx_paragraphsAndFormatting() {
        val file = write("doc.docx") { out ->
            XWPFDocument().use { doc ->
                val p1 = doc.createParagraph()
                val r1 = p1.createRun()
                r1.setText("这是第一段正文内容，用于验证提取。")
                r1.setBold(true)
                val p2 = doc.createParagraph()
                val r2 = p2.createRun()
                r2.setText("第二段正文。")
                doc.write(out)
            }
        }

        val flow = WordExtractor.extract(file, 1080)

        assertEquals(1080, flow.pageWidthPx)
        assertFalse(flow.blocks.isEmpty())
        val texts = flow.blocks.filterIsInstance<FlowBlock.Paragraph>()
            .joinToString { it.para.text }
        assertTrue(texts.contains("第一段正文内容"))
        assertTrue(texts.contains("第二段正文。"))
        // 加粗格式被保留
        val boldRun = flow.blocks.filterIsInstance<FlowBlock.Paragraph>()
            .flatMap { it.para.runs }
            .first { it.text.contains("第一段") }
        assertTrue(boldRun.bold)
    }

    @Test
    fun xlsx_cellsAndMergedRegion() {
        val file = write("sheet.xlsx") { out ->
            XSSFWorkbook().use { wb ->
                val sheet = wb.createSheet("数据")
                // 注意：同一行只能 createRow 一次并复用引用。重复 createRow(n) 会让
                // 前一次拿到的行对象（及其已加单元格）在落盘时丢失。
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

        val pages = SheetExtractor.extract(file, 1080)
        assertTrue(pages.isNotEmpty())
        val texts = pageTexts(pages)
        assertTrue(texts.contains("姓名"))
        assertTrue(texts.contains("张三"))
        // 数值单元被格式化为可读文本
        assertTrue(texts.contains("95"))
    }

    @Test
    fun xls_legacy_workbook() {
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

        val pages = SheetExtractor.extract(file, 1080)

        assertTrue(pages.isNotEmpty())
        val texts = pageTexts(pages)
        assertTrue(texts.contains("旧版单元格"))
        assertTrue(texts.contains("42"))
    }
}