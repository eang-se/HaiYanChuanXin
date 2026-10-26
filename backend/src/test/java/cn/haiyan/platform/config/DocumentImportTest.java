package cn.haiyan.platform.config;
import cn.haiyan.platform.web.UploadController;
import java.io.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.*;
import static org.junit.jupiter.api.Assertions.*;

class DocumentImportTest {
    private final UploadController uploads=new UploadController(null,org.mockito.Mockito.mock(cn.haiyan.platform.service.AccountPermissions.class));
    @Test void importsChineseDocx()throws Exception{
        try(var doc=new XWPFDocument();var bytes=new ByteArrayOutputStream()){
            doc.createParagraph().createRun().setText("风经过校园，文字留在书页。");doc.write(bytes);
            var result=uploads.importFile(new MockMultipartFile("file","sample.docx","application/octet-stream",bytes.toByteArray()),()->"test");
            assertTrue(result.get("text").contains("文字留在书页"));
        }
    }
    @Test void importsTextPdf()throws Exception{
        try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()){
            var page=new PDPage();doc.addPage(page);
            try(var text=new PDPageContentStream(doc,page)){text.beginText();text.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);text.newLineAtOffset(30,700);text.showText("Letters from the campus");text.endText();}
            doc.save(bytes);
            var result=uploads.importFile(new MockMultipartFile("file","sample.pdf","application/pdf",bytes.toByteArray()),()->"test");
            assertTrue(result.get("text").contains("Letters from the campus"));
        }
    }
    @Test void rejectsUnsupportedAndCorruptDocuments(){
        assertThrows(ResponseStatusException.class,()->uploads.importFile(new MockMultipartFile("file","sample.exe","application/octet-stream",new byte[]{1,2,3}),()->"test"));
        assertThrows(ResponseStatusException.class,()->uploads.importFile(new MockMultipartFile("file","sample.pdf","application/pdf",new byte[]{1,2,3}),()->"test"));
    }
}
