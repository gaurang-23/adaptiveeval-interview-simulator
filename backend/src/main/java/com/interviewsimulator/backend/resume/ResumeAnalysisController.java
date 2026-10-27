package com.interviewsimulator.backend.resume;

import com.interviewsimulator.backend.auth.SecurityUtils;
import com.interviewsimulator.backend.resume.dto.ResumeAnalysisPageDto;
import com.interviewsimulator.backend.resume.dto.ResumeAnalysisReportDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/resume-analyses")
@Tag(name = "AI Resume Screener", description = "Endpoints for uploading resumes, running structured LLM alignment evaluations, and managing persistent screening history")
public class ResumeAnalysisController {

    private final ResumeAnalysisService resumeAnalysisService;

    public ResumeAnalysisController(ResumeAnalysisService resumeAnalysisService) {
        this.resumeAnalysisService = resumeAnalysisService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a PDF or DOCX resume and run a live LLM alignment analysis against a job description",
            description = "Extracts text on the backend, evaluates against the 100-point rubric using the configured LLM, persists the result in MySQL, and returns the structured report."
    )
    public ResponseEntity<ResumeAnalysisReportDto> createResumeAnalysis(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "jobTitle", required = false) String jobTitle,
            @RequestParam("jobDescription") String jobDescription
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        ResumeAnalysisReportDto report = resumeAnalysisService.analyzeResume(userId, file, jobTitle, jobDescription);
        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    @GetMapping
    @Operation(
            summary = "Retrieve the authenticated user's persistent resume screening history",
            description = "Supports search by filename or job role/description, score range filtering, status filtering, sorting, and pagination."
    )
    public ResponseEntity<ResumeAnalysisPageDto> listResumeAnalyses(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
            @RequestParam(value = "minScore", required = false) Integer minScore,
            @RequestParam(value = "maxScore", required = false) Integer maxScore,
            @RequestParam(value = "sortBy", required = false, defaultValue = "NEWEST") String sortBy,
            @RequestParam(value = "page", required = false, defaultValue = "0") int page,
            @RequestParam(value = "size", required = false, defaultValue = "10") int size
    ) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(resumeAnalysisService.listUserHistory(
                userId, search, status, minScore, maxScore, sortBy, page, size
        ));
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Retrieve a specific saved resume analysis report by ID",
            description = "Verifies ownership and returns the persisted snapshot without invoking the LLM again."
    )
    public ResponseEntity<ResumeAnalysisReportDto> getResumeAnalysisById(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(resumeAnalysisService.getOwnedReport(userId, id));
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a resume analysis record owned by the authenticated user",
            description = "Permanently removes the persisted analysis record from MySQL after verifying ownership."
    )
    public ResponseEntity<Map<String, Object>> deleteResumeAnalysis(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        resumeAnalysisService.deleteOwnedAnalysis(userId, id);
        return ResponseEntity.ok(Map.of(
                "deleted", true,
                "id", id,
                "message", "Resume analysis deleted from MySQL."
        ));
    }
}
