package com.interviewsimulator.runner.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class CodeSecurityValidator {

    @Value("${runner.max-source-bytes:65536}")
    private int maxSourceBytes;

    private record Rule(Pattern pattern, String reason) {}

    private static final List<Rule> FORBIDDEN_RULES = List.of(
            new Rule(Pattern.compile("\\bRuntime\\s*\\.\\s*getRuntime\\b"), "Process execution via Runtime.getRuntime() is prohibited in sandbox."),
            new Rule(Pattern.compile("\\bProcessBuilder\\b"), "Process creation via ProcessBuilder is prohibited in sandbox."),
            new Rule(Pattern.compile("\\bSystem\\s*\\.\\s*exit\\b"), "Terminating the JVM via System.exit() is prohibited."),
            new Rule(Pattern.compile("\\bjava\\.io\\.(File|FileInputStream|FileOutputStream|FileReader|FileWriter|RandomAccessFile)\\b"), "Direct host filesystem access is prohibited."),
            new Rule(Pattern.compile("\\bnew\\s+(File|FileInputStream|FileOutputStream|FileReader|FileWriter|RandomAccessFile)\\s*\\("), "Direct host filesystem access is prohibited."),
            new Rule(Pattern.compile("\\bjava\\.nio\\.file\\b"), "Filesystem access via java.nio.file is prohibited."),
            new Rule(Pattern.compile("\\bFiles\\s*\\.\\s*(read|write|walk|list|delete|copy|move|newBuffered|newInputStream|newOutputStream)"), "Filesystem access via Files utility is prohibited."),
            new Rule(Pattern.compile("\\bjava\\.net\\b"), "Network access via java.net is prohibited."),
            new Rule(Pattern.compile("\\b(Socket|ServerSocket|HttpURLConnection|URL|URI\\.create)\\b"), "Network socket/HTTP access is prohibited."),
            new Rule(Pattern.compile("\\bjava\\.lang\\.reflect\\b"), "Reflection manipulation is prohibited in candidate solutions."),
            new Rule(Pattern.compile("\\bClassLoader\\b"), "Custom classloader manipulation is prohibited."),
            new Rule(Pattern.compile("\\bsun\\.misc\\.Unsafe\\b"), "Unsafe memory access is prohibited."),
            new Rule(Pattern.compile("\\bSystem\\s*\\.\\s*(getenv|getProperties|setProperty|load|loadLibrary)\\b"), "Reading host environment variables or loading native libraries is prohibited.")
    );

    public Optional<String> validate(String sourceCode) {
        if (sourceCode == null || sourceCode.isBlank()) {
            return Optional.of("Source code cannot be empty.");
        }
        int byteLength = sourceCode.getBytes(StandardCharsets.UTF_8).length;
        if (byteLength > maxSourceBytes) {
            return Optional.of("Source code exceeds maximum allowed size of " + maxSourceBytes + " bytes.");
        }
        if (!sourceCode.contains("class Solution")) {
            return Optional.of("Submission must define a public 'class Solution'.");
        }
        for (Rule rule : FORBIDDEN_RULES) {
            if (rule.pattern().matcher(sourceCode).find()) {
                return Optional.of("Security policy violation: " + rule.reason());
            }
        }
        return Optional.empty();
    }
}
