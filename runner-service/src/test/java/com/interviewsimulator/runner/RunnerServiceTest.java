package com.interviewsimulator.runner;

import com.interviewsimulator.runner.dto.ExecutionRequest;
import com.interviewsimulator.runner.dto.ExecutionResponse;
import com.interviewsimulator.runner.dto.TestCaseSpec;
import com.interviewsimulator.runner.service.JavaSandboxExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "runner.isolation-mode=RESTRICTED_LOCAL_SANDBOX",
        "runner.per-test-timeout-ms=1000"
})
class RunnerServiceTest {

    @Autowired
    private JavaSandboxExecutor executor;

    @Test
    void executesValidTwoSumSolutionAndCapturesStdout() {
        String code = """
                import java.util.*;
                public class Solution {
                    public int[] twoSum(int[] nums, int target) {
                        System.out.println("Checking target=" + target);
                        Map<Integer, Integer> map = new HashMap<>();
                        for (int i = 0; i < nums.length; i++) {
                            int comp = target - nums[i];
                            if (map.containsKey(comp)) {
                                return new int[]{map.get(comp), i};
                            }
                            map.put(nums[i], i);
                        }
                        return new int[]{};
                    }
                }
                """;

        ExecutionRequest req = new ExecutionRequest(
                code,
                "JAVA",
                "twoSum",
                List.of(
                        new TestCaseSpec(1L, "[2,7,11,15]\n9", "[0,1]", true),
                        new TestCaseSpec(2L, "[3,2,4]\n6", "[1,2]", false)
                )
        );

        ExecutionResponse res = executor.execute(req);
        assertEquals("SUCCESS", res.compilationStatus());
        assertEquals(2, res.totalTests());
        assertEquals(2, res.passedTests());
        assertEquals(0, res.failedTests());
        assertEquals("NONE", res.failureCategory());
        assertTrue(res.testResults().get(0).stdout().contains("Checking target=9"));
    }

    @Test
    void blocksForbiddenFilesystemAccess() {
        String maliciousCode = """
                import java.io.File;
                public class Solution {
                    public int solve(int x) {
                        File f = new File("/etc/passwd");
                        return x;
                    }
                }
                """;
        ExecutionRequest req = new ExecutionRequest(
                maliciousCode,
                "JAVA",
                "solve",
                List.of(new TestCaseSpec(1L, "5", "5", true))
        );
        ExecutionResponse res = executor.execute(req);
        assertEquals("SECURITY_VIOLATION", res.compilationStatus());
        assertEquals("SECURITY_VIOLATION", res.failureCategory());
    }

    @Test
    void enforcesPerTestTimeout() {
        String infiniteLoopCode = """
                public class Solution {
                    public int solve(int x) {
                        while (true) {
                            x = x + 1 - 1;
                        }
                    }
                }
                """;
        ExecutionRequest req = new ExecutionRequest(
                infiniteLoopCode,
                "JAVA",
                "solve",
                List.of(new TestCaseSpec(1L, "5", "5", true))
        );
        ExecutionResponse res = executor.execute(req);
        assertEquals("SUCCESS", res.compilationStatus());
        assertEquals(0, res.passedTests());
        assertEquals("TIME_LIMIT_EXCEEDED", res.failureCategory());
    }
}
