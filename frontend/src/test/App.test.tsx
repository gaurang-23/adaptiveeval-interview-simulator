import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { App } from '../App';
import { api, storage } from '../services/api';
import type {
  DashboardData,
  InterviewReport,
  InterviewWorkspaceState,
  ResumeAnalysisReport,
} from '../types/api';

const mockUser = {
  id: 1,
  email: 'alex@example.com',
  displayName: 'Alex Chen',
  targetRole: 'Senior Backend Engineer',
  experienceLevel: 'SENIOR',
  preferredLanguage: 'JAVA',
  themePreference: 'dark' as const,
  createdAt: '2026-10-24T10:00:00Z',
};

const emptyDashboard: DashboardData = {
  hasAssessmentData: false,
  totalInterviewsCompleted: 0,
  activeInterviewsCount: 0,
  averageInterviewScore: null,
  currentOverallProficiency: null,
  improvementSincePrevious: null,
  aiMode: 'FALLBACK_RUBRIC',
  liveLlmConfigured: false,
  runnerHealth: { status: 'UP', isolationMode: 'RESTRICTED_LOCAL_SANDBOX' },
  skillProfile: [
    {
      competency: 'PATTERN_RECOGNITION',
      label: 'Pattern Recognition',
      score: 50,
      confidence: 0,
      assessmentsCount: 0,
      sufficientEvidence: false,
      updatedAt: '2026-10-24T10:00:00Z',
    },
    {
      competency: 'IMPLEMENTATION',
      label: 'Implementation',
      score: 50,
      confidence: 0,
      assessmentsCount: 0,
      sufficientEvidence: false,
      updatedAt: '2026-10-24T10:00:00Z',
    },
    {
      competency: 'COMPLEXITY_ANALYSIS',
      label: 'Complexity Analysis',
      score: 50,
      confidence: 0,
      assessmentsCount: 0,
      sufficientEvidence: false,
      updatedAt: '2026-10-24T10:00:00Z',
    },
    {
      competency: 'DEBUGGING',
      label: 'Debugging',
      score: 50,
      confidence: 0,
      assessmentsCount: 0,
      sufficientEvidence: false,
      updatedAt: '2026-10-24T10:00:00Z',
    },
    {
      competency: 'COMMUNICATION',
      label: 'Communication & Explanation',
      score: 50,
      confidence: 0,
      assessmentsCount: 0,
      sufficientEvidence: false,
      updatedAt: '2026-10-24T10:00:00Z',
    },
  ],
  weakestCompetencies: [],
  strongestCompetencies: [],
  skillProgression: [],
  topicPerformance: [],
  correctnessAndEfficiencyTrends: [],
  recentActivity: [],
  recommendedPractice: [],
};

const populatedDashboard: DashboardData = {
  ...emptyDashboard,
  hasAssessmentData: true,
  totalInterviewsCompleted: 3,
  activeInterviewsCount: 0,
  averageInterviewScore: 84.5,
  currentOverallProficiency: 81.2,
  improvementSincePrevious: 6.4,
  skillProfile: emptyDashboard.skillProfile.map((s, idx) => ({
    ...s,
    score: 75 + idx * 4,
    confidence: 0.85,
    assessmentsCount: 3,
    sufficientEvidence: true,
  })),
  weakestCompetencies: [
    {
      competency: 'COMPLEXITY_ANALYSIS',
      label: 'Complexity Analysis',
      score: 62.0,
      confidence: 0.8,
      assessmentsCount: 3,
      sufficientEvidence: true,
      updatedAt: '2026-10-24T10:00:00Z',
    },
  ],
  recommendedPractice: [
    {
      id: 10,
      questionId: 1,
      questionTitle: 'Two Sum',
      questionSlug: 'two-sum',
      topic: 'Hash Maps',
      suggestedDifficulty: 'EASY',
      questionType: 'DSA',
      targetCompetency: 'COMPLEXITY_ANALYSIS',
      targetCompetencyLabel: 'Complexity Analysis',
      reason: 'Strengthen Big-O time/space trade-off derivation.',
      learningObjective: 'Justify O(n) hash map lookup vs O(n^2) nested loops.',
      status: 'PENDING',
      createdAt: '2026-10-24T10:00:00Z',
    },
  ],
};

const mockResumeReport: ResumeAnalysisReport = {
  id: 99,
  originalFilename: 'alex_chen_resume.pdf',
  fileContentType: 'application/pdf',
  fileSizeBytes: 124800,
  jobTitle: 'Senior Java Backend Engineer',
  jobDescription:
    'Looking for a Senior Java Backend Engineer experienced with Spring Boot, MySQL, distributed systems, and REST APIs.',
  overallScore: 86,
  analysisStatus: 'COMPLETED',
  aiProvider: 'gemini',
  aiModel: 'gemini-2.5-flash',
  errorCategory: null,
  errorMessage: null,
  createdAt: '2026-10-26T15:00:00Z',
  completedAt: '2026-10-26T15:00:03Z',
  dimensionScores: [
    {
      dimensionKey: 'REQUIRED_TECHNICAL_SKILLS',
      label: 'Required Technical Skills',
      score: 27,
      maxScore: 30,
      rationale: 'Demonstrates Java 21, Spring Boot, and MySQL in production.',
    },
    {
      dimensionKey: 'RELEVANT_EXPERIENCE',
      label: 'Relevant Experience',
      score: 22,
      maxScore: 25,
      rationale: '5+ years building high-throughput backend services.',
    },
    {
      dimensionKey: 'PROJECTS_AND_ACCOMPLISHMENTS',
      label: 'Projects & Technical Accomplishments',
      score: 17,
      maxScore: 20,
      rationale: 'Quantified latency reductions and database indexing improvements.',
    },
    {
      dimensionKey: 'ROLE_QUALIFICATIONS',
      label: 'Role Qualifications',
      score: 12,
      maxScore: 15,
      rationale: 'Meets senior engineering ownership criteria.',
    },
    {
      dimensionKey: 'COMMUNICATION_AND_IMPACT',
      label: 'Communication & Evidence of Impact',
      score: 8,
      maxScore: 10,
      rationale: 'Clear metric-driven bullet points.',
    },
  ],
  result: {
    overallScore: 86,
    summary:
      'Strong alignment with Java 21, Spring Boot microservices, and MySQL relational modeling requirements.',
    disclaimer:
      'AI resume screening is an advisory decision-support tool and does not automatically determine employment outcomes.',
    dimensionScores: [
      {
        dimensionKey: 'REQUIRED_TECHNICAL_SKILLS',
        label: 'Required Technical Skills',
        score: 27,
        maxScore: 30,
        rationale: 'Demonstrates Java 21, Spring Boot, and MySQL in production.',
      },
      {
        dimensionKey: 'RELEVANT_EXPERIENCE',
        label: 'Relevant Experience',
        score: 22,
        maxScore: 25,
        rationale: '5+ years building high-throughput backend services.',
      },
      {
        dimensionKey: 'PROJECTS_AND_ACCOMPLISHMENTS',
        label: 'Projects & Technical Accomplishments',
        score: 17,
        maxScore: 20,
        rationale: 'Quantified latency reductions and database indexing improvements.',
      },
      {
        dimensionKey: 'ROLE_QUALIFICATIONS',
        label: 'Role Qualifications',
        score: 12,
        maxScore: 15,
        rationale: 'Meets senior engineering ownership criteria.',
      },
      {
        dimensionKey: 'COMMUNICATION_AND_IMPACT',
        label: 'Communication & Evidence of Impact',
        score: 8,
        maxScore: 10,
        rationale: 'Clear metric-driven bullet points.',
      },
    ],
    strengths: [
      {
        title: 'Production Spring Boot & MySQL',
        resumeEvidence: 'Architected 12 Spring Boot services backed by MySQL 8 handling 15k RPS.',
        relatedRequirement: 'Java 21 & Spring Boot microservices',
      },
    ],
    weaknesses: [
      {
        gap: 'Kubernetes Operator Experience',
        detail: 'Resume does not mention writing custom Kubernetes operators.',
        relatedRequirement: 'Cloud-native infrastructure automation',
        actionableStep: 'Add any Helm or Kubernetes controller experience if applicable.',
      },
    ],
    matchedRequirements: [
      {
        requirement: 'Java 21 & Spring Boot',
        resumeEvidence: '5+ years leading Java/Spring backend services',
      },
    ],
    partiallyMatchedRequirements: [
      {
        requirement: 'Distributed Messaging',
        resumeEvidence: 'Used RabbitMQ for async jobs',
        missingDetail: 'Kafka consumer group tuning not explicitly stated',
      },
    ],
    missingRequirements: [
      {
        requirement: 'Custom Kubernetes Operators',
        statusNote: 'Not mentioned in resume',
      },
    ],
    technicalSkills: [
      {
        skill: 'Java 21',
        alignmentStatus: 'MATCHED',
        resumeEvidence: 'Primary backend language across 3 roles',
      },
      {
        skill: 'MySQL 8',
        alignmentStatus: 'MATCHED',
        resumeEvidence: 'Designed normalized schema and composite indexes',
      },
    ],
    experienceAssessment: {
      summary: 'Meets 5+ year senior backend expectation.',
      relevantResponsibilities: ['Owned high-throughput payment APIs'],
      seniorityAndScopeNote: 'Demonstrates technical leadership across multiple services.',
    },
    projectAssessment: {
      summary: 'Projects include concrete throughput and p99 latency metrics.',
      keyProjectsAndOutcomes: ['Reduced p99 query latency by 42%'],
      measurableImpactNote: 'Strong quantitative evidence.',
    },
    improvementRecommendations: [
      {
        priority: 'HIGH',
        targetSection: 'Experience',
        recommendation: 'Add concrete metrics from event-driven or queue-based architectures.',
        rationale: 'Addresses the distributed systems depth requirement.',
      },
    ],
    suggestedResumeEdits: [
      {
        section: 'Experience — Senior Backend Engineer',
        originalContext: 'Worked on database performance',
        suggestedTruthfulWording:
          'Optimized MySQL 8 composite indexes and query plans, reducing p99 API latency by 42%.',
        alignmentReason: 'Directly evidences relational database tuning for the target role.',
      },
    ],
    interviewPreparationTopics: [
      {
        topic: 'MySQL Transaction Isolation & Indexing',
        reason: 'Core requirement of the target Senior Backend role',
        sampleQuestion:
          'How does REPEATABLE READ in InnoDB handle phantom reads compared to READ COMMITTED?',
      },
    ],
  },
};

describe('Adaptive Technical Interview Simulator — Frontend Suite', () => {
  beforeEach(() => {
    localStorage.clear();
    document.documentElement.classList.remove('dark', 'light', 'theme-dark', 'theme-light');
    document.documentElement.removeAttribute('data-theme');
    vi.restoreAllMocks();
    vi.spyOn(api.dashboard, 'getSystemStatus').mockResolvedValue({
      ai: {
        aiMode: 'FALLBACK_RUBRIC',
        liveLlmConfigured: false,
        configured: false,
        connectionStatus: 'NOT_CONFIGURED',
        provider: 'gemini',
        model: 'gemini-2.5-flash',
      },
      runner: { status: 'UP', isolationMode: 'RESTRICTED_LOCAL_SANDBOX' },
    });
  });

  it('renders Landing Page with core 5-competency diagnostic highlights', () => {
    render(
      <MemoryRouter initialEntries={['/']}>
        <App />
      </MemoryRouter>
    );

    expect(
      screen.getAllByText(/Adaptive Technical Interview Simulator/i).length
    ).toBeGreaterThan(0);
    expect(screen.getAllByText(/Pattern Recognition/i).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Complexity Analysis/i).length).toBeGreaterThan(0);
  });

  it('validates and submits Login form and navigates to Dashboard empty state for new user', async () => {
    vi.spyOn(api.auth, 'login').mockResolvedValue({
      accessToken: 'jwt-test-token',
      tokenType: 'Bearer',
      expiresInMs: 86400000,
      user: mockUser,
    });
    vi.spyOn(api.dashboard, 'get').mockResolvedValue(emptyDashboard);

    render(
      <MemoryRouter initialEntries={['/login']}>
        <App />
      </MemoryRouter>
    );

    fireEvent.change(screen.getByLabelText(/Email Address/i), {
      target: { value: 'alex@example.com' },
    });
    fireEvent.change(screen.getByLabelText(/Password/i), {
      target: { value: 'StrongPass123!' },
    });
    fireEvent.click(screen.getByRole('button', { name: /Sign In/i }));

    await waitFor(() => {
      expect(api.auth.login).toHaveBeenCalledWith({
        email: 'alex@example.com',
        password: 'StrongPass123!',
      });
    });

    await waitFor(() => {
      expect(
        screen.getByText(
          /Complete your first adaptive interview to calibrate your 5-competency skill vector/i
        )
      ).toBeInTheDocument();
    });
  });

  it('renders populated Dashboard with real persisted metrics and competency profile', async () => {
    storage.setToken('jwt-test-token');
    storage.setUser(mockUser);
    vi.spyOn(api.users, 'getMe').mockResolvedValue(mockUser);
    vi.spyOn(api.dashboard, 'get').mockResolvedValue(populatedDashboard);

    render(
      <MemoryRouter initialEntries={['/dashboard']}>
        <App />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('84.5%')).toBeInTheDocument();
      expect(screen.getByText('81.2 / 100')).toBeInTheDocument();
      expect(screen.getByText('Two Sum')).toBeInTheDocument();
    });
  });

  it('configures and launches an adaptive interview session into the live workspace', async () => {
    storage.setToken('jwt-test-token');
    storage.setUser(mockUser);
    vi.spyOn(api.users, 'getMe').mockResolvedValue(mockUser);
    vi.spyOn(api.questions, 'list').mockResolvedValue([]);

    const createSpy = vi.spyOn(api.interviews, 'create').mockResolvedValue({
      id: 42,
      interviewType: 'DSA',
      configuredDifficulty: 'MEDIUM',
      interviewStyle: 'INTERVIEWER_LED',
      topics: ['Arrays', 'Hash Maps'],
      questionCount: 2,
      durationSeconds: 1800,
      startedAt: null,
      deadlineAt: null,
      completedAt: null,
      status: 'CREATED',
      overallScore: null,
      createdAt: '2026-10-24T10:00:00Z',
    });

    const mockWorkspace: InterviewWorkspaceState = {
      session: {
        id: 42,
        interviewType: 'DSA',
        configuredDifficulty: 'MEDIUM',
        interviewStyle: 'INTERVIEWER_LED',
        topics: ['Arrays'],
        questionCount: 2,
        durationSeconds: 1800,
        startedAt: '2026-10-24T10:00:00Z',
        deadlineAt: '2026-10-24T10:30:00Z',
        completedAt: null,
        status: 'IN_PROGRESS',
        overallScore: null,
        createdAt: '2026-10-24T10:00:00Z',
      },
      remainingSeconds: 1795,
      serverNow: '2026-10-24T10:00:05Z',
      aiMode: 'FALLBACK_RUBRIC',
      liveLlmConfigured: false,
      activeQuestion: {
        interviewQuestionId: 100,
        sequenceNumber: 1,
        totalQuestions: 2,
        selectionReason: 'Baseline calibration question for Arrays.',
        targetCompetency: 'IMPLEMENTATION',
        status: 'IN_PROGRESS',
        hintsUsed: 0,
        unlockedHints: [],
        explanationSubmitted: false,
        startedAt: '2026-10-24T10:00:00Z',
        question: {
          id: 1,
          title: 'Two Sum',
          slug: 'two-sum',
          topic: 'Arrays',
          difficulty: 'EASY',
          questionType: 'DSA',
          entryMethodName: 'twoSum',
          statement: 'Find two indices that sum to target.',
          constraintsText: '2 <= nums.length <= 10^4',
          examples: [{ input: 'nums = [2,7,11,15], target = 9', output: '[0,1]', explanation: '' }],
          starterCode:
            'public class Solution { public int[] twoSum(int[] nums, int target) { return new int[]{0,1}; } }',
          totalHintsAvailable: 3,
          sampleTestCases: [
            { id: 1, inputData: '[2,7,11,15]\n9', expectedOutput: '[0, 1]', orderIndex: 1 },
          ],
        },
      },
      currentQuestionAttempts: [],
      messages: [
        {
          id: 1,
          questionId: 1,
          role: 'INTERVIEWER',
          content: 'Welcome! Please walk through your approach for Two Sum.',
          messageType: 'PROMPT',
          sourceType: 'FALLBACK_RUBRIC',
          createdAt: '2026-10-24T10:00:00Z',
        },
      ],
    };

    vi.spyOn(api.interviews, 'start').mockResolvedValue(mockWorkspace);
    vi.spyOn(api.interviews, 'getWorkspace').mockResolvedValue(mockWorkspace);

    render(
      <MemoryRouter initialEntries={['/interviews/new']}>
        <App />
      </MemoryRouter>
    );

    const startBtn = await screen.findByRole('button', {
      name: /Launch Live Interview Workspace/i,
    });
    fireEvent.click(startBtn);

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalled();
      expect(screen.getByText('Two Sum')).toBeInTheDocument();
    });
  });

  it('renders Interview Report with observed facts separated from inferred hypothesis', async () => {
    storage.setToken('jwt-test-token');
    storage.setUser(mockUser);
    vi.spyOn(api.users, 'getMe').mockResolvedValue(mockUser);

    const mockReport: InterviewReport = {
      sessionId: 42,
      interviewType: 'DSA',
      configuredDifficulty: 'MEDIUM',
      interviewStyle: 'INTERVIEWER_LED',
      startedAt: '2026-10-24T10:00:00Z',
      completedAt: '2026-10-24T10:25:00Z',
      status: 'COMPLETED',
      overallScore: 86.0,
      aiModeUsed: 'FALLBACK_RUBRIC',
      previousComparison: {
        previousSessionId: 41,
        previousOverallScore: 79.0,
        scoreDelta: 7.0,
        previousCompletedAt: '2026-10-23T10:00:00Z',
      },
      competencyScores: [
        {
          competency: 'IMPLEMENTATION',
          label: 'Implementation',
          score: 92.0,
          previousScore: 84.0,
          delta: 8.0,
          confidence: 0.9,
          sufficientEvidence: true,
          sourceType: 'EXECUTION_ENGINE',
        },
      ],
      strongestCompetencies: ['Implementation'],
      weakestCompetencies: ['Complexity Analysis'],
      topicResults: [
        {
          topic: 'Arrays',
          questionsAttempted: 1,
          totalTestsPassed: 5,
          totalTestsRun: 5,
          averageScore: 92.0,
        },
      ],
      questionReports: [
        {
          questionId: 1,
          sequenceNumber: 1,
          title: 'Two Sum',
          topic: 'Arrays',
          difficulty: 'EASY',
          selectionReason: 'Initial calibration.',
          targetCompetency: 'IMPLEMENTATION',
          attemptsCount: 2,
          finalCompilationStatus: 'SUCCESS',
          testsPassed: 5,
          testsTotal: 5,
          bestRuntimeMs: 14,
          expectedTimeComplexity: 'O(n)',
          expectedSpaceComplexity: 'O(n)',
          hintsUsed: 0,
          explanationSubmitted: true,
        },
      ],
      diagnosedMistakes: [
        {
          questionId: 1,
          questionTitle: 'Two Sum',
          competency: 'COMPLEXITY_ANALYSIS',
          competencyLabel: 'Complexity Analysis',
          diagnosisCode: 'COMPLEXITY_MISMATCH',
          score: 68.0,
          confidence: 0.85,
          observedFacts: ['Candidate stated O(1) space while allocating HashMap of size n.'],
          inferredHypothesis: 'Candidate overlooked auxiliary hash table memory growth.',
          recommendedIntervention: 'Review auxiliary space vs in-place space complexity.',
          sourceType: 'FALLBACK_RUBRIC',
        },
      ],
      recommendedPractice: [],
    };

    vi.spyOn(api.interviews, 'getReport').mockResolvedValue(mockReport);

    render(
      <MemoryRouter initialEntries={['/interviews/42/report']}>
        <App />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(
        screen.getByText(/Candidate stated O\(1\) space while allocating HashMap of size n\./i)
      ).toBeInTheDocument();
      expect(
        screen.getByText(/Candidate overlooked auxiliary hash table memory growth\./i)
      ).toBeInTheDocument();
    });
  });

  it('switches between Dark and Light themes and persists preference in localStorage and DOM', async () => {
    storage.setToken('jwt-test-token');
    storage.setUser(mockUser);
    vi.spyOn(api.users, 'getMe').mockResolvedValue(mockUser);
    const updateThemeSpy = vi
      .spyOn(api.users, 'updateTheme')
      .mockResolvedValue({ ...mockUser, themePreference: 'light' });

    render(
      <MemoryRouter initialEntries={['/settings']}>
        <App />
      </MemoryRouter>
    );

    const lightBtn = await screen.findByTestId('select-light-theme-btn');
    fireEvent.click(lightBtn);

    await waitFor(() => {
      expect(document.documentElement.getAttribute('data-theme')).toBe('light');
      expect(document.documentElement.classList.contains('light')).toBe(true);
      expect(localStorage.getItem('atis_theme')).toBe('light');
      expect(updateThemeSpy).toHaveBeenCalledWith('light');
    });

    const darkBtn = screen.getByTestId('select-dark-theme-btn');
    fireEvent.click(darkBtn);

    await waitFor(() => {
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
      expect(document.documentElement.classList.contains('dark')).toBe(true);
      expect(localStorage.getItem('atis_theme')).toBe('dark');
    });
  });

  it('executes Test AI Connection in Profile & Settings and displays diagnostic states', async () => {
    storage.setToken('jwt-test-token');
    storage.setUser(mockUser);
    vi.spyOn(api.users, 'getMe').mockResolvedValue(mockUser);
    vi.spyOn(api.dashboard, 'getSystemStatus').mockResolvedValue({
      ai: {
        aiMode: 'FALLBACK_RUBRIC',
        liveLlmConfigured: true,
        configured: true,
        connectionStatus: 'CONFIGURED_UNVERIFIED',
        provider: 'gemini',
        model: 'gemini-2.5-flash',
      },
      runner: { status: 'UP', isolationMode: 'RESTRICTED_LOCAL_SANDBOX' },
    });

    const testSpy = vi
      .spyOn(api.dashboard, 'testAiConnection')
      .mockResolvedValueOnce({
        provider: 'gemini',
        model: 'gemini-2.5-flash',
        baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
        configured: true,
        liveLlmConfigured: false,
        connectionStatus: 'AUTH_ERROR',
        aiMode: 'FALLBACK_RUBRIC',
        latencyMs: 145,
        lastTestedAt: '2026-10-26T16:00:00Z',
        errorCategory: 'AUTH_ERROR',
        message: 'Provider rejected authentication (HTTP 401).',
        troubleshootingRecommendation:
          'Verify that AI_API_KEY is valid for gemini and has not been revoked.',
      })
      .mockResolvedValueOnce({
        provider: 'gemini',
        model: 'gemini-2.5-flash',
        baseUrl: 'https://generativelanguage.googleapis.com/v1beta/openai',
        configured: true,
        liveLlmConfigured: true,
        connectionStatus: 'CONNECTED',
        aiMode: 'LIVE_LLM',
        latencyMs: 310,
        lastTestedAt: '2026-10-26T16:01:00Z',
        errorCategory: null,
        message: 'Successfully verified live connection to gemini (gemini-2.5-flash).',
        troubleshootingRecommendation: 'AI provider is healthy and ready for live evaluations.',
      });

    render(
      <MemoryRouter initialEntries={['/settings']}>
        <App />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByTestId('settings-ai-connection-status')).toHaveTextContent(
        'CONFIGURED_UNVERIFIED'
      );
    });

    const testBtn = screen.getByTestId('test-ai-connection-btn');
    fireEvent.click(testBtn);

    await waitFor(() => {
      expect(testSpy).toHaveBeenCalledTimes(1);
      expect(screen.getByTestId('ai-connection-test-result')).toHaveTextContent(
        /AI Connection Test Failed \(AUTH_ERROR\)/i
      );
      expect(
        screen.getAllByText(/Verify that AI_API_KEY is valid for gemini/i).length
      ).toBeGreaterThan(0);
    });

    // Retry connection test -> succeeds
    fireEvent.click(screen.getByRole('button', { name: /Retry Connection Test/i }));

    await waitFor(() => {
      expect(testSpy).toHaveBeenCalledTimes(2);
      expect(screen.getByTestId('ai-connection-test-result')).toHaveTextContent(
        /Live AI Connection Verified/i
      );
      expect(screen.getByTestId('settings-ai-connection-status')).toHaveTextContent('CONNECTED');
    });
  });

  it('supports Resume Screener upload validation, live analysis, history browsing, reopening saved report without re-calling LLM, and deletion', async () => {
    storage.setToken('jwt-test-token');
    storage.setUser(mockUser);
    vi.spyOn(api.users, 'getMe').mockResolvedValue(mockUser);

    const listSpy = vi.spyOn(api.resumeAnalyses, 'list').mockResolvedValue({
      items: [
        {
          id: 99,
          originalFilename: 'alex_chen_resume.pdf',
          fileSizeBytes: 124800,
          jobTitle: 'Senior Java Backend Engineer',
          jobDescriptionSummary: 'Looking for a Senior Java Backend Engineer...',
          overallScore: 86,
          analysisStatus: 'COMPLETED',
          aiProvider: 'gemini',
          aiModel: 'gemini-2.5-flash',
          errorCategory: null,
          errorMessage: null,
          createdAt: '2026-10-26T15:00:00Z',
          completedAt: '2026-10-26T15:00:03Z',
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    });

    const createSpy = vi
      .spyOn(api.resumeAnalyses, 'create')
      .mockResolvedValue(mockResumeReport);
    const getByIdSpy = vi
      .spyOn(api.resumeAnalyses, 'getById')
      .mockResolvedValue(mockResumeReport);
    const deleteSpy = vi.spyOn(api.resumeAnalyses, 'deleteById').mockResolvedValue({
      deleted: true,
      id: 99,
      message: 'Resume analysis deleted.',
    });

    render(
      <MemoryRouter initialEntries={['/resume-screener']}>
        <App />
      </MemoryRouter>
    );

    const fileInput = await screen.findByTestId('resume-file-input');

    // 1. Rejects unsupported .txt file
    const invalidFile = new File(['plain text'], 'resume.txt', { type: 'text/plain' });
    fireEvent.change(fileInput, { target: { files: [invalidFile] } });

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(/Unsupported file format/i);
    });

    // 2. Accepts valid .pdf file and validates JD minimum length
    const validPdf = new File(['%PDF-1.4 sample resume'], 'alex_chen_resume.pdf', {
      type: 'application/pdf',
    });
    fireEvent.change(fileInput, { target: { files: [validPdf] } });

    const jdInput = screen.getByLabelText(/Full Job Description/i);
    fireEvent.change(jdInput, {
      target: {
        value:
          'Looking for a Senior Java Backend Engineer experienced with Spring Boot, MySQL, distributed systems, and REST APIs.',
      },
    });

    const analyzeBtn = screen.getByTestId('analyze-resume-btn');
    expect(analyzeBtn).not.toBeDisabled();
    fireEvent.click(analyzeBtn);

    await waitFor(() => {
      expect(createSpy).toHaveBeenCalledTimes(1);
      expect(screen.getByText(/Executive Evaluation Summary/i)).toBeInTheDocument();
      expect(screen.getByText(/27 \/ 30 pts/i)).toBeInTheDocument();
    });

    // 3. Switch to Screening History tab, reopen saved report without calling create again
    fireEvent.click(screen.getByRole('button', { name: /Screening History/i }));

    await waitFor(() => {
      expect(listSpy).toHaveBeenCalled();
      expect(screen.getByTestId('history-item-99')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId('view-report-99'));

    await waitFor(() => {
      expect(getByIdSpy).toHaveBeenCalledWith(99);
      expect(createSpy).toHaveBeenCalledTimes(1); // Not re-invoked!
      expect(screen.getByText(/Executive Evaluation Summary/i)).toBeInTheDocument();
    });

    // 4. Return to history and delete the saved report via confirmation modal
    fireEvent.click(screen.getByRole('button', { name: /Screening History/i }));
    const deleteBtn = await screen.findByTestId('delete-report-99');
    fireEvent.click(deleteBtn);

    const confirmBtn = await screen.findByTestId('confirm-delete-btn');
    fireEvent.click(confirmBtn);

    await waitFor(() => {
      expect(deleteSpy).toHaveBeenCalledWith(99);
    });
  });

  it('applies consistent light-mode and dark-mode styling on the Resume Screener page when toggling theme', async () => {
    storage.setToken('jwt-test-token');
    storage.setUser({ ...mockUser, themePreference: 'light' });
    localStorage.setItem('atis_theme', 'light');
    vi.spyOn(api.users, 'getMe').mockResolvedValue({ ...mockUser, themePreference: 'light' });
    vi.spyOn(api.users, 'updateTheme').mockResolvedValue(mockUser);
    vi.spyOn(api.resumeAnalyses, 'list').mockResolvedValue({
      items: [
        {
          id: 99,
          originalFilename: 'alex_chen_resume.pdf',
          fileSizeBytes: 124800,
          jobTitle: 'Senior Java Backend Engineer',
          jobDescriptionSummary: 'Looking for a Senior Java Backend Engineer...',
          overallScore: 86,
          analysisStatus: 'COMPLETED',
          aiProvider: 'gemini',
          aiModel: 'gemini-2.5-flash',
          errorCategory: null,
          errorMessage: null,
          createdAt: '2026-10-26T15:00:00Z',
          completedAt: '2026-10-26T15:00:03Z',
        },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    });

    render(
      <MemoryRouter initialEntries={['/resume-screener']}>
        <App />
      </MemoryRouter>
    );

    const heading = await screen.findByRole('heading', { name: /AI Resume Screener/i });
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(document.documentElement.classList.contains('light')).toBe(true);
    expect(heading.className).toContain('text-slate-900');

    // Verify upload card, dropzone, inputs, rubric rows, and primary action button classes
    const uploadHeading = screen.getByRole('heading', { name: /Upload Candidate Resume/i });
    const uploadCard = uploadHeading.closest('.rounded-2xl');
    expect(uploadCard?.className).toContain('bg-white');
    expect(uploadCard?.className).toContain('dark:bg-slate-900/90');

    const dropzoneText = screen.getByText(/Click to select a resume file or drag and drop here/i);
    const dropzone = dropzoneText.closest('[role="button"]');
    expect(dropzone?.className).toContain('border-dashed');
    expect(dropzone?.className).toContain('bg-slate-50/80');

    const jobTitleInput = screen.getByLabelText(/Job Title \/ Target Role/i);
    expect(jobTitleInput.className).toContain('bg-white');
    expect(jobTitleInput.className).toContain('text-slate-900');

    const jdTextarea = screen.getByLabelText(/Full Job Description/i);
    expect(jdTextarea.className).toContain('bg-white');
    expect(jdTextarea.className).toContain('text-slate-900');

    const analyzeBtn = screen.getByTestId('analyze-resume-btn');
    expect(analyzeBtn).toBeDisabled();
    expect(analyzeBtn.className).toContain('bg-indigo-600');
    expect(analyzeBtn.className).toContain('disabled:bg-indigo-100');

    // Toggle to dark mode via sidebar theme toggle and back to light mode
    const themeToggleBtn = screen.getByTestId('theme-toggle-btn');
    fireEvent.click(themeToggleBtn);

    await waitFor(() => {
      expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
      expect(document.documentElement.classList.contains('dark')).toBe(true);
    });

    fireEvent.click(themeToggleBtn);

    await waitFor(() => {
      expect(document.documentElement.getAttribute('data-theme')).toBe('light');
      expect(document.documentElement.classList.contains('light')).toBe(true);
    });
  });
});
