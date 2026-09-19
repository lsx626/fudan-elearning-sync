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
import org.apache.poi.xwpf.usermodel.Document
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.FileOutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater

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
    @Test
    fun docx_table_extractedAsTableBlock() {
        val file = write("tbl.docx") { out ->
            XWPFDocument().use { doc ->
                doc.createParagraph().createRun().setText("表前段落")
                val table = doc.createTable()
                val r0 = table.getRow(0)
                r0.getCell(0).setText("姓名")
                r0.addNewTableCell().setText("分数")
                val r1 = table.createRow()
                r1.getCell(0).setText("张三")
                r1.getCell(1).setText("95")
                doc.createParagraph().createRun().setText("表后段落")
                doc.write(out)
            }
        }

        val flow = WordExtractor.extract(file, 1080)

        // 段落 -> 表格 -> 段落 的正文顺序被保留
        assertTrue(flow.blocks.filterIsInstance<FlowBlock.Table>().isNotEmpty())
        assertTrue(flow.blocks.filterIsInstance<FlowBlock.Paragraph>()
            .joinToString { it.para.text }.contains("表前段落"))
        assertTrue(flow.blocks.filterIsInstance<FlowBlock.Paragraph>()
            .joinToString { it.para.text }.contains("表后段落"))
        val tbl = flow.blocks.filterIsInstance<FlowBlock.Table>().first()
        val texts = tbl.rows.joinToString { row -> row.cells.joinToString { it.text } }
        assertTrue(texts.contains("姓名"))
        assertTrue(texts.contains("分数"))
        assertTrue(texts.contains("张三"))
        assertTrue(tbl.columnWeights.isNotEmpty())
    }

    @Test
    fun docx_inlineImage_extractedAsPicture() {
        // 合成 100x50 RGB PNG（单测不依赖 AWT：Android 编译期无 java.desktop）
        val png = makePng(100, 50)
        val file = write("img.docx") { out ->
            XWPFDocument().use { doc ->
                val p = doc.createParagraph()
                val r = p.createRun()
                r.setText("图片说明")
                r.addPicture(ByteArrayInputStream(png), Document.PICTURE_TYPE_PNG, "dot.png",
                1270000, 635000)   // EMU：100pt x 50pt
                doc.write(out)
            }
        }

        val flow = WordExtractor.extract(file, 1080)

        val pics = flow.blocks.filterIsInstance<FlowBlock.Picture>()
        assertTrue(pics.isNotEmpty())
        val pic = pics.first()
        assertTrue(pic.bytes.size > 100)
        // EMU->px 换算必须保持原始宽高比（100:50 = 2:1）
        assertTrue("widthPx=" + pic.widthPx + " heightPx=" + pic.heightPx,
            pic.widthPx > 0 && pic.heightPx > 0)
        val ratio = pic.widthPx.toFloat() / pic.heightPx
        assertTrue("ratio=" + ratio, ratio in 1.9f..2.1f)
    }

    /** 手写最小合法 PNG（IHDR/IDAT/IEND），避免单测依赖 AWT 解码器。 */
    private fun makePng(w: Int, h: Int): ByteArray {
        val ihdr = ByteArrayOutputStream(13).let { bos ->
            DataOutputStream(bos).use {
                it.writeInt(w); it.writeInt(h)
                it.writeByte(8)   // 位深
                it.writeByte(2)   // 色彩类型：真彩 RGB
                it.writeByte(0); it.writeByte(0); it.writeByte(0)
            }
            bos.toByteArray()
        }
        // 原始扫描线：每行 = 1 字节过滤位 + w*3 字节 RGB
        val raw = ByteArrayOutputStream()
        for (y in 0 until h) {
            raw.write(0)
            for (x in 0 until w) {
                raw.write(0x4F); raw.write(0x46); raw.write(0xE5)
            }
        }
        // android.jar 的 Deflater 未必是 Closeable，不用 .use；测试内创建后即丢弃
        val deflater = Deflater()
        deflater.setInput(raw.toByteArray())
        deflater.finish()
        val idatBos = ByteArrayOutputStream()
        val idatBuf = ByteArray(4096)
        while (!deflater.finished()) idatBos.write(idatBuf, 0, deflater.deflate(idatBuf))
        val idat = idatBos.toByteArray()
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(),
            0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()))  // PNG 签名
        out.write(pngChunk("IHDR", ihdr))
        out.write(pngChunk("IDAT", idat))
        out.write(pngChunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val crc = CRC32()
        crc.update(type.toByteArray(Charsets.US_ASCII))
        crc.update(data)
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use {
            it.writeInt(data.size)
            it.write(type.toByteArray(Charsets.US_ASCII))
            it.write(data)
            it.writeInt(crc.value.toInt())
        }
        return bos.toByteArray()
    }
}