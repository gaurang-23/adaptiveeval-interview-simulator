package com.interviewsimulator.runner.service;

import com.interviewsimulator.runner.dto.ExecutionRequest;
import com.interviewsimulator.runner.dto.ExecutionResponse;
import com.interviewsimulator.runner.dto.TestCaseExecutionResult;
import com.interviewsimulator.runner.dto.TestCaseSpec;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JavaSandboxExecutor {

  private static final Logger log = LoggerFactory.getLogger(
    JavaSandboxExecutor.class
  );

  private final CodeSecurityValidator securityValidator;

  @Value("${runner.isolation-mode:AUTO}")
  private String configuredIsolationMode;

  @Value("${runner.docker-image:interview-simulator-sandbox:latest}")
  private String dockerImage;

  @Value("${runner.compile-timeout-ms:8000}")
  private long compileTimeoutMs;

  @Value("${runner.execution-timeout-ms:5000}")
  private long executionTimeoutMs;

  @Value("${runner.per-test-timeout-ms:2000}")
  private long perTestTimeoutMs;

  @Value("${runner.max-output-chars:16384}")
  private int maxOutputChars;

  @Value("${runner.max-memory-mb:128}")
  private int maxMemoryMb;

  public JavaSandboxExecutor(CodeSecurityValidator securityValidator) {
    this.securityValidator = securityValidator;
  }

  public boolean isDockerAvailable() {
    try {
      Process p = new ProcessBuilder("docker", "info")
        .redirectErrorStream(true)
        .start();
      boolean finished = p.waitFor(2, TimeUnit.SECONDS);
      return finished && p.exitValue() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  public String getEffectiveIsolationMode() {
    if ("DOCKER_CONTAINER".equalsIgnoreCase(configuredIsolationMode)) {
      return "DOCKER_CONTAINER";
    }
    if ("RESTRICTED_LOCAL_SANDBOX".equalsIgnoreCase(configuredIsolationMode)) {
      return "RESTRICTED_LOCAL_SANDBOX";
    }
    return isDockerAvailable()
      ? "DOCKER_CONTAINER"
      : "RESTRICTED_LOCAL_SANDBOX";
  }

  public ExecutionResponse execute(ExecutionRequest request) {
    Optional<String> securityError = securityValidator.validate(
      request.sourceCode()
    );
    if (securityError.isPresent()) {
      return new ExecutionResponse(
        "SECURITY_VIOLATION",
        securityError.get(),
        request.testCases().size(),
        0,
        request.testCases().size(),
        0L,
        0L,
        "SECURITY_VIOLATION",
        securityError.get(),
        getEffectiveIsolationMode(),
        List.of()
      );
    }

    Path workDir = null;
    try {
      workDir = Files.createTempDirectory(
        "interview-sandbox-" + UUID.randomUUID()
      );
      writeHelperFiles(workDir, request);

      String effectiveMode = getEffectiveIsolationMode();
      if ("DOCKER_CONTAINER".equals(effectiveMode)) {
        return executeInDocker(workDir, request);
      } else {
        return executeInLocalSandbox(workDir, request);
      }
    } catch (Exception e) {
      log.error("Sandbox execution failed unexpectedly", e);
      return new ExecutionResponse(
        "INTERNAL_ERROR",
        "Execution service error: " + sanitizeText(e.getMessage()),
        request.testCases().size(),
        0,
        request.testCases().size(),
        0L,
        0L,
        "RUNTIME_EXCEPTION",
        sanitizeText(e.getMessage()),
        getEffectiveIsolationMode(),
        List.of()
      );
    } finally {
      if (workDir != null) {
        cleanupDirectory(workDir);
      }
    }
  }

  private void writeHelperFiles(Path workDir, ExecutionRequest request)
    throws IOException {
    Files.writeString(
      workDir.resolve("Solution.java"),
      request.sourceCode(),
      StandardCharsets.UTF_8
    );
    Files.writeString(
      workDir.resolve("ListNode.java"),
      getListNodeSource(),
      StandardCharsets.UTF_8
    );
    Files.writeString(
      workDir.resolve("TreeNode.java"),
      getTreeNodeSource(),
      StandardCharsets.UTF_8
    );
    Files.writeString(
      workDir.resolve("RunnerHarness.java"),
      getRunnerHarnessSource(request),
      StandardCharsets.UTF_8
    );
  }

  private ExecutionResponse executeInDocker(
    Path workDir,
    ExecutionRequest request
  ) throws IOException, InterruptedException {
    String hostPath = workDir.toAbsolutePath().toString();
    ProcessResult compileRes = runProcess(
      workDir,
      List.of(
        "docker",
        "run",
        "--rm",
        "--network",
        "none",
        "--memory",
        maxMemoryMb + "m",
        "--cpus",
        "1.0",
        "--pids-limit",
        "64",
        "-v",
        hostPath + ":/workspace:rw",
        "-w",
        "/workspace",
        dockerImage,
        "javac",
        "-encoding",
        "UTF-8",
        "ListNode.java",
        "TreeNode.java",
        "Solution.java",
        "RunnerHarness.java"
      ),
      compileTimeoutMs
    );

    if (compileRes.timedOut || compileRes.exitCode != 0) {
      String sanitizedCompiler = sanitizePathReferences(
        compileRes.output,
        workDir
      );
      return new ExecutionResponse(
        "COMPILATION_ERROR",
        sanitizedCompiler,
        request.testCases().size(),
        0,
        request.testCases().size(),
        0L,
        0L,
        "COMPILATION_ERROR",
        sanitizedCompiler,
        "DOCKER_CONTAINER",
        List.of()
      );
    }

    ProcessResult runRes = runProcess(
      workDir,
      List.of(
        "docker",
        "run",
        "--rm",
        "--network",
        "none",
        "--memory",
        maxMemoryMb + "m",
        "--cpus",
        "1.0",
        "--pids-limit",
        "64",
        "--read-only",
        "-v",
        hostPath + ":/workspace:ro",
        "-w",
        "/workspace",
        dockerImage,
        "java",
        "-Xmx" + maxMemoryMb + "m",
        "-XX:MaxMetaspaceSize=64m",
        "RunnerHarness"
      ),
      executionTimeoutMs
    );

    return parseExecutionResult(runRes, request, workDir, "DOCKER_CONTAINER");
  }

  private ExecutionResponse executeInLocalSandbox(
    Path workDir,
    ExecutionRequest request
  ) throws IOException, InterruptedException {
    String javacBin = resolveJavaTool("javac");
    log.info("Java home: {}", System.getProperty("java.home"));
    log.info("Javac path: {}", javacBin);
    log.info("Javac exists: {}", new File(javacBin).exists());
    String javaBin = resolveJavaTool("java");

    ProcessResult compileRes = runProcess(
      workDir,
      List.of(
        javacBin,
        "-encoding",
        "UTF-8",
        "ListNode.java",
        "TreeNode.java",
        "Solution.java",
        "RunnerHarness.java"
      ),
      compileTimeoutMs
    );

    if (compileRes.timedOut || compileRes.exitCode != 0) {
      log.error("========== COMPILATION FAILURE ==========");
      log.error("Java home: {}", System.getProperty("java.home"));
      log.error("Java version: {}", System.getProperty("java.version"));
      log.error("javac executable: {}", javacBin);
      log.error("Working directory: {}", workDir);
      log.error("Exit code: {}", compileRes.exitCode);
      log.error("Timed out: {}", compileRes.timedOut);
      log.error("Compiler output: [{}]", compileRes.output);

      String compilerMessage = compileRes.output;

      if (compileRes.timedOut) {
        compilerMessage =
          "Java compilation timed out after " + compileTimeoutMs + " ms.";
      } else if (compilerMessage == null || compilerMessage.isBlank()) {
        compilerMessage =
          "Compiler exited with code " +
          compileRes.exitCode +
          " but produced no diagnostic output. " +
          "Check javac availability and generated source files.";
      }

      String sanitizedCompiler = sanitizePathReferences(
        compilerMessage,
        workDir
      );

      return new ExecutionResponse(
        "COMPILATION_ERROR",
        sanitizedCompiler,
        request.testCases().size(),
        0,
        request.testCases().size(),
        0L,
        0L,
        "COMPILATION_ERROR",
        sanitizedCompiler,
        "RESTRICTED_LOCAL_SANDBOX",
        List.of()
      );
    }

    ProcessResult runRes = runProcess(
      workDir,
      List.of(
        javaBin,
        "-Xmx" + maxMemoryMb + "m",
        "-XX:MaxMetaspaceSize=64m",
        "-XX:+ExitOnOutOfMemoryError",
        "-cp",
        workDir.toAbsolutePath().toString(),
        "RunnerHarness"
      ),
      executionTimeoutMs
    );

    return parseExecutionResult(
      runRes,
      request,
      workDir,
      "RESTRICTED_LOCAL_SANDBOX"
    );
  }

  private ExecutionResponse parseExecutionResult(
    ProcessResult runRes,
    ExecutionRequest request,
    Path workDir,
    String isolationMode
  ) {
    int totalTests = request.testCases().size();
    if (runRes.timedOut) {
      return new ExecutionResponse(
        "SUCCESS",
        "",
        totalTests,
        0,
        totalTests,
        executionTimeoutMs,
        0L,
        "TIME_LIMIT_EXCEEDED",
        "Execution exceeded global time limit of " +
          executionTimeoutMs +
          " ms.",
        isolationMode,
        List.of()
      );
    }

    List<TestCaseExecutionResult> results = new ArrayList<>();
    long totalRuntimeMs = 0L;
    long maxMemoryKb = 0L;
    boolean hadTimeout = false;
    boolean hadRuntimeException = false;
    StringBuilder errorCollector = new StringBuilder();

    String[] lines = runRes.output.split("\\R");
    for (String line : lines) {
      if (line.startsWith("__HARNESS_MEM_KB__:")) {
        try {
          maxMemoryKb = Math.max(
            maxMemoryKb,
            Long.parseLong(
              line.substring("__HARNESS_MEM_KB__:".length()).trim()
            )
          );
        } catch (NumberFormatException ignored) {}
      } else if (line.startsWith("__HARNESS_TEST__:")) {
        String payload = line.substring("__HARNESS_TEST__:".length());
        String[] parts = payload.split("\\|", -1);
        if (parts.length >= 6) {
          int idx = Integer.parseInt(parts[0]);
          boolean passed = Boolean.parseBoolean(parts[1]);
          long runtime = Long.parseLong(parts[2]);
          String actual = decodeB64(parts[3]);
          String stdout = decodeB64(parts[4]);
          String err = decodeB64(parts[5]);

          TestCaseSpec spec =
            idx < request.testCases().size()
              ? request.testCases().get(idx)
              : null;
          Long tcId = spec != null ? spec.id() : (long) idx;
          boolean sample = spec != null && spec.sample();
          String inputData = spec != null ? spec.inputData() : "";
          String expectedOutput = spec != null ? spec.expectedOutput() : "";

          totalRuntimeMs += runtime;
          if (!err.isBlank()) {
            if (err.contains("TIME_LIMIT_EXCEEDED")) {
              hadTimeout = true;
            } else {
              hadRuntimeException = true;
            }
            if (errorCollector.length() < 2048) {
              errorCollector
                .append("Test #")
                .append(idx + 1)
                .append(": ")
                .append(err)
                .append("\n");
            }
          }

          results.add(
            new TestCaseExecutionResult(
              tcId,
              idx,
              sample,
              passed,
              inputData,
              expectedOutput,
              actual,
              stdout,
              err,
              runtime
            )
          );
        }
      }
    }

    if (results.isEmpty() && runRes.exitCode != 0) {
      String rawErr = sanitizePathReferences(runRes.output, workDir);
      return new ExecutionResponse(
        "SUCCESS",
        "",
        totalTests,
        0,
        totalTests,
        0L,
        maxMemoryKb,
        "RUNTIME_EXCEPTION",
        rawErr,
        isolationMode,
        List.of()
      );
    }

    int passedTests = (int) results
      .stream()
      .filter(TestCaseExecutionResult::passed)
      .count();
    int failedTests = totalTests - passedTests;

    String failureCategory = "NONE";
    if (failedTests > 0) {
      if (hadTimeout) {
        failureCategory = "TIME_LIMIT_EXCEEDED";
      } else if (hadRuntimeException) {
        failureCategory = "RUNTIME_EXCEPTION";
      } else {
        boolean allSamplesPassed = results
          .stream()
          .filter(TestCaseExecutionResult::sample)
          .allMatch(TestCaseExecutionResult::passed);
        boolean hasSamples = results
          .stream()
          .anyMatch(TestCaseExecutionResult::sample);
        if (hasSamples && allSamplesPassed) {
          failureCategory = "EDGE_CASE_FAILURE";
        } else {
          failureCategory = "WRONG_ANSWER";
        }
      }
    }

    return new ExecutionResponse(
      "SUCCESS",
      "",
      totalTests,
      passedTests,
      failedTests,
      totalRuntimeMs,
      maxMemoryKb,
      failureCategory,
      sanitizePathReferences(errorCollector.toString().trim(), workDir),
      isolationMode,
      results
    );
  }

  private record ProcessResult(int exitCode, boolean timedOut, String output) {}

  private ProcessResult runProcess(
    Path workDir,
    List<String> command,
    long timeoutMs
  ) throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.directory(workDir.toFile());
    pb.redirectErrorStream(true);
    Process process = pb.start();

    StringBuilder output = new StringBuilder();
    Thread readerThread = Thread.ofVirtual().start(() -> {
      try (
        BufferedReader reader = new BufferedReader(
          new InputStreamReader(
            process.getInputStream(),
            StandardCharsets.UTF_8
          )
        )
      ) {
        char[] buf = new char[1024];
        int read;
        while ((read = reader.read(buf)) != -1) {
          if (output.length() < maxOutputChars) {
            output.append(
              buf,
              0,
              Math.min(read, maxOutputChars - output.length())
            );
          }
        }
      } catch (IOException ignored) {}
    });

    boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
    if (!finished) {
      process.descendants().forEach(ProcessHandle::destroyForcibly);
      process.destroyForcibly();
      readerThread.join(500);
      return new ProcessResult(-1, true, output.toString());
    }
    readerThread.join(1000);
    return new ProcessResult(process.exitValue(), false, output.toString());
  }

  private String resolveJavaTool(String toolName) {
    String javaHome = System.getProperty("java.home");
    if (javaHome != null) {
      File binFile = new File(
        javaHome,
        "bin/" +
          toolName +
          (System.getProperty("os.name").toLowerCase().contains("win")
            ? ".exe"
            : "")
      );
      if (binFile.exists()) {
        return binFile.getAbsolutePath();
      }
    }
    return toolName;
  }

  private String sanitizePathReferences(String text, Path workDir) {
    if (text == null) return "";
    String sanitized = text.replace(
      workDir.toAbsolutePath().toString(),
      "/sandbox"
    );
    sanitized = sanitized.replace(workDir.toString(), "/sandbox");
    return sanitizeText(sanitized);
  }

  private String sanitizeText(String text) {
    if (text == null) return "";
    if (text.length() > maxOutputChars) {
      return text.substring(0, maxOutputChars) + "... [truncated]";
    }
    return text;
  }

  private String encodeB64(String s) {
    if (s == null) return "";
    return Base64.getEncoder().encodeToString(
      s.getBytes(StandardCharsets.UTF_8)
    );
  }

  private String decodeB64(String b64) {
    if (b64 == null || b64.isEmpty()) return "";
    try {
      return new String(
        Base64.getDecoder().decode(b64),
        StandardCharsets.UTF_8
      );
    } catch (Exception e) {
      return "";
    }
  }

  private void cleanupDirectory(Path dir) {
    try (var stream = Files.walk(dir)) {
      stream
        .sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(File::delete);
    } catch (IOException ignored) {}
  }

  private String getListNodeSource() {
    return """
    import java.util.*;

    public class ListNode {
        public int val;
        public ListNode next;
        public ListNode() {}
        public ListNode(int val) { this.val = val; }
        public ListNode(int val, ListNode next) { this.val = val; this.next = next; }

        public static ListNode fromArray(int[] arr) {
            if (arr == null || arr.length == 0) return null;
            ListNode dummy = new ListNode(0);
            ListNode curr = dummy;
            for (int v : arr) {
                curr.next = new ListNode(v);
                curr = curr.next;
            }
            return dummy.next;
        }

        public static String toBracketString(ListNode head) {
            if (head == null) return "[]";
            StringBuilder sb = new StringBuilder("[");
            ListNode curr = head;
            int guard = 0;
            while (curr != null && guard < 10000) {
                if (guard > 0) sb.append(",");
                sb.append(curr.val);
                curr = curr.next;
                guard++;
            }
            sb.append("]");
            return sb.toString();
        }
    }
    """;
  }

  private String getTreeNodeSource() {
    return """
    import java.util.*;

    public class TreeNode {
        public int val;
        public TreeNode left;
        public TreeNode right;
        public TreeNode() {}
        public TreeNode(int val) { this.val = val; }
        public TreeNode(int val, TreeNode left, TreeNode right) {
            this.val = val;
            this.left = left;
            this.right = right;
        }

        public static TreeNode fromLevelOrder(String raw) {
            String trimmed = raw == null ? "" : raw.trim();
            if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
            if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);
            trimmed = trimmed.trim();
            if (trimmed.isEmpty()) return null;
            String[] tokens = trimmed.split(",");
            if (tokens[0].trim().equals("null")) return null;
            TreeNode root = new TreeNode(Integer.parseInt(tokens[0].trim()));
            Queue<TreeNode> q = new LinkedList<>();
            q.offer(root);
            int i = 1;
            while (!q.isEmpty() && i < tokens.length) {
                TreeNode parent = q.poll();
                if (i < tokens.length) {
                    String leftTok = tokens[i++].trim();
                    if (!leftTok.equals("null") && !leftTok.isEmpty()) {
                        parent.left = new TreeNode(Integer.parseInt(leftTok));
                        q.offer(parent.left);
                    }
                }
                if (i < tokens.length) {
                    String rightTok = tokens[i++].trim();
                    if (!rightTok.equals("null") && !rightTok.isEmpty()) {
                        parent.right = new TreeNode(Integer.parseInt(rightTok));
                        q.offer(parent.right);
                    }
                }
            }
            return root;
        }

        public static String toBracketString(TreeNode root) {
            if (root == null) return "[]";
            List<String> out = new ArrayList<>();
            Queue<TreeNode> q = new LinkedList<>();
            q.offer(root);
            while (!q.isEmpty()) {
                TreeNode node = q.poll();
                if (node == null) {
                    out.add("null");
                } else {
                    out.add(String.valueOf(node.val));
                    q.offer(node.left);
                    q.offer(node.right);
                }
            }
            while (!out.isEmpty() && out.get(out.size() - 1).equals("null")) {
                out.remove(out.size() - 1);
            }
            return "[" + String.join(",", out) + "]";
        }
    }
    """;
  }

  private String getRunnerHarnessSource(ExecutionRequest request) {
    StringBuilder tcBuilder = new StringBuilder();
    List<TestCaseSpec> cases = request.testCases();
    for (int i = 0; i < cases.size(); i++) {
      TestCaseSpec tc = cases.get(i);
      tcBuilder
        .append("        inputs.add(decodeB64(\"")
        .append(encodeB64(tc.inputData()))
        .append("\"));\n");
      tcBuilder
        .append("        expected.add(decodeB64(\"")
        .append(encodeB64(tc.expectedOutput()))
        .append("\"));\n");
    }
    String entryMethod =
      request.entryMethodName() == null ? "" : request.entryMethodName().trim();

    return (
      """
      import java.io.*;
      import java.lang.reflect.*;
      import java.nio.charset.StandardCharsets;
      import java.util.*;
      import java.util.concurrent.*;

      public class RunnerHarness {
          private static String encodeB64(String s) {
              if (s == null) return "";
              return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
          }
          private static String decodeB64(String s) {
              if (s == null || s.isEmpty()) return "";
              return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
          }

          public static void main(String[] args) {
              PrintStream originalOut = System.out;
              List<String> inputs = new ArrayList<>();
              List<String> expected = new ArrayList<>();
      """ +
      tcBuilder +
      """
      String targetMethodName = \"""" +
      entryMethod +
      """
      \";
              Method targetMethod = findTargetMethod(Solution.class, targetMethodName);
              if (targetMethod == null) {
                  originalOut.println("No public entry method found on Solution class.");
                  System.exit(2);
              }
              targetMethod.setAccessible(true);

              ExecutorService executor = Executors.newCachedThreadPool(r -> {
                  Thread t = new Thread(r);
                  t.setDaemon(true);
                  return t;
              });

              long maxMemKb = 0L;
              for (int i = 0; i < inputs.size(); i++) {
                  final String rawInput = inputs.get(i);
                  final String rawExpected = expected.get(i);
                  ByteArrayOutputStream userOutBuffer = new ByteArrayOutputStream();
                  System.setOut(new PrintStream(userOutBuffer, true, StandardCharsets.UTF_8));

                  long startNs = System.nanoTime();
                  String actualStr = "";
                  String errStr = "";
                  boolean passed = false;

                  Future<Object> future = executor.submit(() -> {
                      Solution instance = new Solution();
                      Object[] parsedArgs = parseArguments(targetMethod.getGenericParameterTypes(), rawInput);
                      return targetMethod.invoke(instance, parsedArgs);
                  });

                  try {
                      Object result = future.get(""" +
      perTestTimeoutMs +
      """
      , TimeUnit.MILLISECONDS);
                      actualStr = serializeOutput(result, targetMethod.getReturnType());
                      passed = compareOutputs(actualStr, rawExpected);
                  } catch (TimeoutException te) {
                      future.cancel(true);
                      errStr = "TIME_LIMIT_EXCEEDED: Test timed out after """ +
      perTestTimeoutMs +
      """
       ms";
                  } catch (ExecutionException ee) {
                      Throwable cause = ee.getCause();
                      if (cause instanceof InvocationTargetException ite && ite.getTargetException() != null) {
                          cause = ite.getTargetException();
                      }
                      errStr = cause.getClass().getSimpleName() + ": " + (cause.getMessage() != null ? cause.getMessage() : "");
                  } catch (Throwable t) {
                      errStr = t.getClass().getSimpleName() + ": " + (t.getMessage() != null ? t.getMessage() : "");
                  } finally {
                      System.setOut(originalOut);
                  }

                  long elapsedMs = Math.max(0L, (System.nanoTime() - startNs) / 1_000_000L);
                  long usedKb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024L;
                  maxMemKb = Math.max(maxMemKb, usedKb);

                  String userStdout = userOutBuffer.toString(StandardCharsets.UTF_8);
                  if (userStdout.length() > 2048) {
                      userStdout = userStdout.substring(0, 2048) + "...";
                  }

                  originalOut.println("__HARNESS_TEST__:" + i + "|" + passed + "|" + elapsedMs + "|"
                          + encodeB64(actualStr) + "|" + encodeB64(userStdout) + "|" + encodeB64(errStr));
              }
              originalOut.println("__HARNESS_MEM_KB__:" + maxMemKb);
              System.exit(0);
          }

          private static Method findTargetMethod(Class<?> clazz, String preferredName) {
              Method[] methods = clazz.getDeclaredMethods();
              if (preferredName != null && !preferredName.isEmpty()) {
                  for (Method m : methods) {
                      if (Modifier.isPublic(m.getModifiers()) && m.getName().equals(preferredName)) {
                          return m;
                      }
                  }
              }
              for (Method m : methods) {
                  if (Modifier.isPublic(m.getModifiers()) && !m.getName().equals("main")) {
                      return m;
                  }
              }
              return null;
          }

          private static Object[] parseArguments(Type[] paramTypes, String rawInput) {
              if (paramTypes.length == 0) return new Object[0];
              String[] lines = rawInput == null ? new String[0] : rawInput.split("\\\\R", -1);
              Object[] args = new Object[paramTypes.length];
              for (int i = 0; i < paramTypes.length; i++) {
                  String line = i < lines.length ? lines[i].trim() : "";
                  args[i] = parseSingleValue(paramTypes[i], line);
              }
              return args;
          }

          private static Object parseSingleValue(Type type, String raw) {
              String s = raw == null ? "" : raw.trim();
              if (type == int.class || type == Integer.class) {
                  return Integer.parseInt(s);
              }
              if (type == long.class || type == Long.class) {
                  return Long.parseLong(s);
              }
              if (type == double.class || type == Double.class) {
                  return Double.parseDouble(s);
              }
              if (type == boolean.class || type == Boolean.class) {
                  return Boolean.parseBoolean(s);
              }
              if (type == String.class) {
                  if (s.length() >= 2 && s.startsWith("\\"") && s.endsWith("\\"")) {
                      return s.substring(1, s.length() - 1);
                  }
                  return s;
              }
              if (type == int[].class) {
                  return parseIntArray(s);
              }
              if (type == String[].class) {
                  return parseStringArray(s);
              }
              if (type == int[][].class) {
                  return parseInt2DArray(s);
              }
              if (type == char[][].class) {
                  return parseChar2DArray(s);
              }
              if (type == ListNode.class) {
                  return ListNode.fromArray(parseIntArray(s));
              }
              if (type == TreeNode.class) {
                  return TreeNode.fromLevelOrder(s);
              }
              if (type instanceof ParameterizedType pt && pt.getRawType() == List.class) {
                  Type elemType = pt.getActualTypeArguments()[0];
                  if (elemType == Integer.class) {
                      int[] arr = parseIntArray(s);
                      List<Integer> list = new ArrayList<>();
                      for (int v : arr) list.add(v);
                      return list;
                  }
                  if (elemType == String.class) {
                      return new ArrayList<>(Arrays.asList(parseStringArray(s)));
                  }
              }
              return s;
          }

          private static int[] parseIntArray(String s) {
              String inner = stripBrackets(s);
              if (inner.isEmpty()) return new int[0];
              String[] parts = inner.split(",");
              int[] res = new int[parts.length];
              for (int i = 0; i < parts.length; i++) {
                  res[i] = Integer.parseInt(parts[i].trim());
              }
              return res;
          }

          private static String[] parseStringArray(String s) {
              String inner = stripBrackets(s);
              if (inner.isEmpty()) return new String[0];
              String[] parts = inner.split(",");
              String[] res = new String[parts.length];
              for (int i = 0; i < parts.length; i++) {
                  String item = parts[i].trim();
                  if (item.length() >= 2 && item.startsWith("\\"") && item.endsWith("\\"")) {
                      item = item.substring(1, item.length() - 1);
                  }
                  res[i] = item;
              }
              return res;
          }

          private static int[][] parseInt2DArray(String s) {
              String inner = stripBrackets(s);
              if (inner.isEmpty()) return new int[0][0];
              List<int[]> rows = new ArrayList<>();
              int depth = 0;
              int start = -1;
              for (int i = 0; i < inner.length(); i++) {
                  char c = inner.charAt(i);
                  if (c == '[') {
                      if (depth == 0) start = i;
                      depth++;
                  } else if (c == ']') {
                      depth--;
                      if (depth == 0 && start != -1) {
                          rows.add(parseIntArray(inner.substring(start, i + 1)));
                          start = -1;
                      }
                  }
              }
              return rows.toArray(new int[0][]);
          }

          private static char[][] parseChar2DArray(String s) {
              String inner = stripBrackets(s);
              if (inner.isEmpty()) return new char[0][0];
              List<char[]> rows = new ArrayList<>();
              int depth = 0;
              int start = -1;
              for (int i = 0; i < inner.length(); i++) {
                  char c = inner.charAt(i);
                  if (c == '[') {
                      if (depth == 0) start = i;
                      depth++;
                  } else if (c == ']') {
                      depth--;
                      if (depth == 0 && start != -1) {
                          String[] tokens = parseStringArray(inner.substring(start, i + 1));
                          char[] row = new char[tokens.length];
                          for (int k = 0; k < tokens.length; k++) {
                              String tok = tokens[k].replace("'", "");
                              row[k] = tok.isEmpty() ? '0' : tok.charAt(0);
                          }
                          rows.add(row);
                          start = -1;
                      }
                  }
              }
              return rows.toArray(new char[0][]);
          }

          private static String stripBrackets(String s) {
              String trimmed = s == null ? "" : s.trim();
              if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
              if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);
              return trimmed.trim();
          }

          private static String serializeOutput(Object val, Class<?> returnType) {
              if (val == null) {
                  if (returnType == ListNode.class || returnType == TreeNode.class) return "[]";
                  return "null";
              }
              if (val instanceof int[] arr) {
                  StringBuilder sb = new StringBuilder("[");
                  for (int i = 0; i < arr.length; i++) {
                      if (i > 0) sb.append(",");
                      sb.append(arr[i]);
                  }
                  sb.append("]");
                  return sb.toString();
              }
              if (val instanceof int[][] mat) {
                  StringBuilder sb = new StringBuilder("[");
                  for (int i = 0; i < mat.length; i++) {
                      if (i > 0) sb.append(",");
                      sb.append(serializeOutput(mat[i], int[].class));
                  }
                  sb.append("]");
                  return sb.toString();
              }
              if (val instanceof ListNode ln) {
                  return ListNode.toBracketString(ln);
              }
              if (val instanceof TreeNode tn) {
                  return TreeNode.toBracketString(tn);
              }
              if (val instanceof Collection<?> col) {
                  StringBuilder sb = new StringBuilder("[");
                  int idx = 0;
                  for (Object item : col) {
                      if (idx++ > 0) sb.append(",");
                      sb.append(serializeOutput(item, Object.class));
                  }
                  sb.append("]");
                  return sb.toString();
              }
              return String.valueOf(val);
          }

          private static boolean compareOutputs(String actual, String expected) {
              String normActual = normalizeOutput(actual);
              String normExpected = normalizeOutput(expected);
              return normActual.equals(normExpected);
          }

          private static String normalizeOutput(String s) {
              if (s == null) return "";
              String trimmed = s.trim();
              if (trimmed.length() >= 2 && trimmed.startsWith("\\"") && trimmed.endsWith("\\"")) {
                  trimmed = trimmed.substring(1, trimmed.length() - 1);
              }
              if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                  return trimmed.replaceAll("\\\\s+", "");
              }
              return trimmed;
          }
      }
      """
    );
  }
}
