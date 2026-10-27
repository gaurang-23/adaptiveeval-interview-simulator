package com.interviewsimulator.backend.resume;

import com.interviewsimulator.backend.common.BadRequestException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

@Service
public class ResumeTextExtractorService {

    private static final int MAX_EXTRACTED_CHARS = 32_000;

    private final long maxUploadBytes;

    @Autowired
    public ResumeTextExtractorService(
            @Value("${app.resume.max-upload-bytes:5242880}") long maxUploadBytes
    ) {
        this.maxUploadBytes = maxUploadBytes > 0 ? maxUploadBytes : 5_242_880L;
    }

    public ExtractedResume extractResumeText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please upload a non-empty PDF or DOCX resume file.");
        }

        long size = file.getSize();
        if (size <= 0) {
            throw new BadRequestException("The uploaded resume file is empty.");
        }
        if (size > maxUploadBytes) {
            long maxMb = Math.max(1, maxUploadBytes / (1024 * 1024));
            throw new BadRequestException("Resume file exceeds the maximum allowed size of " + maxMb + " MB.");
        }

        String rawName = file.getOriginalFilename();
        String safeFilename = sanitizeFilename(rawName);
        String lowerName = safeFilename.toLowerCase(Locale.ROOT);

        boolean isPdfExt = lowerName.endsWith(".pdf");
        boolean isDocxExt = lowerName.endsWith(".docx");
        if (!isPdfExt && !isDocxExt) {
            throw new BadRequestException("Unsupported file format. Only PDF (.pdf) and Word (.docx) resumes are supported.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("Unable to read the uploaded resume file.");
        }

        if (bytes.length < 5) {
            throw new BadRequestException("The uploaded resume file is corrupt or truncated.");
        }

        String extractedText;
        String resolvedContentType;

        if (isPdfExt) {
            if (!hasPdfSignature(bytes)) {
                throw new BadRequestException("Invalid PDF file signature. Ensure the file is a genuine PDF document and not a renamed file.");
            }
            extractedText = extractFromPdfBytes(bytes);
            resolvedContentType = "application/pdf";
        } else {
            if (!hasZipSignature(bytes)) {
                throw new BadRequestException("Invalid DOCX file signature. Ensure the file is a genuine Word (.docx) document.");
            }
            extractedText = extractFromDocxBytes(bytes);
            resolvedContentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }

        String normalized = normalizeExtractedText(extractedText);
        if (normalized.isBlank() || normalized.length() < 20) {
            if (isPdfExt) {
                throw new BadRequestException(
                        "No readable text could be extracted from this PDF. It appears to be a scanned image PDF that would require OCR. Please upload a text-selectable PDF or DOCX resume."
                );
            }
            throw new BadRequestException("The uploaded DOCX document does not contain sufficient readable resume text.");
        }

        if (normalized.length() > MAX_EXTRACTED_CHARS) {
            normalized = normalized.substring(0, MAX_EXTRACTED_CHARS);
        }

        return new ExtractedResume(safeFilename, resolvedContentType, size, normalized);
    }

    private String extractFromPdfBytes(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            if (document.isEncrypted()) {
                throw new BadRequestException("This PDF resume is password-protected or encrypted. Please upload an unencrypted PDF.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            throw new BadRequestException("This PDF resume is password-protected or encrypted. Please upload an unencrypted PDF.");
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("Unable to parse the PDF file. The file may be corrupted or malformed.");
        }
    }

    private String extractFromDocxBytes(byte[] bytes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
             XWPFDocument doc = new XWPFDocument(bais);
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            return extractor.getText();
        } catch (Exception e) {
            throw new BadRequestException("Unable to parse the DOCX file. The file may be corrupted or not a valid Word (.docx) document.");
        }
    }

    private static boolean hasPdfSignature(byte[] bytes) {
        // %PDF-
        return bytes.length >= 5
                && bytes[0] == 0x25
                && bytes[1] == 0x50
                && bytes[2] == 0x44
                && bytes[3] == 0x46
                && bytes[4] == 0x2D;
    }

    private static boolean hasZipSignature(byte[] bytes) {
        // PK\x03\x04
        return bytes.length >= 4
                && bytes[0] == 0x50
                && bytes[1] == 0x4B
                && bytes[2] == 0x03
                && bytes[3] == 0x04;
    }

    private static String sanitizeFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            return "resume.pdf";
        }
        String name = raw.replace('\\', '/');
        int slashIdx = name.lastIndexOf('/');
        if (slashIdx >= 0) {
            name = name.substring(slashIdx + 1);
        }
        name = name.replaceAll("[^a-zA-Z0-9._\\- ()]", "_").trim();
        if (name.length() > 200) {
            name = name.substring(name.length() - 200);
        }
        return name.isBlank() ? "resume.pdf" : name;
    }

    private static String normalizeExtractedText(String text) {
        if (text == null) return "";
        return text.replace("\u0000", "")
                .replaceAll("[\\r\\n]{3,}", "\n\n")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();
    }

    public record ExtractedResume(
            String filename,
            String contentType,
            long sizeBytes,
            String extractedText
    ) {}
}
