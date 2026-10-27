export type AiConnectionStatus =
  | 'NOT_CONFIGURED'
  | 'CONFIGURED_UNVERIFIED'
  | 'CONNECTED'
  | 'AUTH_ERROR'
  | 'QUOTA_OR_BILLING_ERROR'
  | 'MODEL_OR_ENDPOINT_ERROR'
  | 'RATE_LIMITED'
  | 'TIMEOUT'
  | 'PROVIDER_ERROR';

export interface AiSystemStatus {
  aiMode: string;
  configured?: boolean;
  liveLlmConfigured: boolean;
  connectionStatus?: AiConnectionStatus | string;
  provider: string;
  configuredProvider?: string;
  model: string;
  configuredModel?: string;
  baseUrl?: string;
  lastTestedAt?: string | null;
  errorCategory?: string | null;
  message?: string | null;
  troubleshootingRecommendation?: string | null;
  latencyMs?: number | null;
}

export interface AiConnectionTestResult {
  provider: string;
  model: string;
  baseUrl: string;
  configured: boolean;
  liveLlmConfigured: boolean;
  aiMode: string;
  connectionStatus: AiConnectionStatus | string;
  errorCategory: string | null;
  message: string | null;
  troubleshootingRecommendation: string | null;
  lastTestedAt: string | null;
  latencyMs: number | null;
}

export interface UserProfile {
  id: number;
  email: string;
  displayName: string;
  targetRole: string;
  experienceLevel: string;
  preferredLanguage: string;
  themePreference?: 'dark' | 'light' | null;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInMs: number;
  user: UserProfile;
}

export interface SkillMetric {
  competency: 'PATTERN_RECOGNITION' | 'IMPLEMENTATION' | 'COMPLEXITY_ANALYSIS' | 'DEBUGGING' | 'COMMUNICATION';
  label: string;
  score: number;
  confidence: number;
  assessmentsCount: number;
  sufficientEvidence: boolean;
  updatedAt: string;
}

export interface SkillProgressionPoint {
  sessionId: number;
  sessionLabel: string;
  timestamp: string;
  patternRecognition: number | null;
  implementation: number | null;
  complexityAnalysis: number | null;
  debugging: number | null;
  communication: number | null;
}

export interface TopicPerformance {
  topic: string;
  attemptsCount: number;
  questionsAttempted: number;
  testsPassed: number;
  testsTotal: number;
  accuracyPercent: number;
  avgRuntimeMs: number;
}

export interface SessionTrendPoint {
  sessionId: number;
  sessionLabel: string;
  completedAt: string;
  overallScore: number;
  testPassRatePercent: number;
  compilationSuccessPercent: number;
  avgRuntimeMs: number;
}

export interface Recommendation {
  id: number;
  questionId: number;
  questionTitle: string;
  questionSlug: string;
  topic: string;
  suggestedDifficulty: string;
  questionType: string;
  targetCompetency: string;
  targetCompetencyLabel: string;
  reason: string;
  learningObjective: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED';
  createdAt: string;
}

export interface InterviewSessionSummary {
  id: number;
  interviewType: string;
  configuredDifficulty: string;
  interviewStyle: string;
  topics: string[];
  questionCount: number;
  durationSeconds: number;
  startedAt: string | null;
  deadlineAt: string | null;
  completedAt: string | null;
  status: 'CREATED' | 'IN_PROGRESS' | 'COMPLETED';
  overallScore: number | null;
  createdAt: string;
}

export interface DashboardData {
  hasAssessmentData: boolean;
  totalInterviewsCompleted: number;
  activeInterviewsCount: number;
  averageInterviewScore: number | null;
  currentOverallProficiency: number | null;
  improvementSincePrevious: number | null;
  aiMode: 'LIVE_LLM' | 'FALLBACK_RUBRIC';
  liveLlmConfigured: boolean;
  runnerHealth: Record<string, unknown>;
  skillProfile: SkillMetric[];
  weakestCompetencies: SkillMetric[];
  strongestCompetencies: SkillMetric[];
  skillProgression: SkillProgressionPoint[];
  topicPerformance: TopicPerformance[];
  correctnessAndEfficiencyTrends: SessionTrendPoint[];
  recentActivity: InterviewSessionSummary[];
  recommendedPractice: Recommendation[];
}

export interface QuestionSummary {
  id: number;
  title: string;
  slug: string;
  topic: string;
  difficulty: string;
  questionType: string;
  expectedConcepts: string[];
}

export interface QuestionExample {
  input: string;
  output: string;
  explanation: string;
}

export interface SampleTestCase {
  id: number;
  inputData: string;
  expectedOutput: string;
  orderIndex: number;
}

export interface QuestionDetail {
  id: number;
  title: string;
  slug: string;
  topic: string;
  difficulty: string;
  questionType: string;
  entryMethodName: string;
  statement: string;
  constraintsText: string;
  examples: QuestionExample[];
  starterCode: string;
  totalHintsAvailable: number;
  sampleTestCases: SampleTestCase[];
}

export interface ActiveInterviewQuestion {
  interviewQuestionId: number;
  sequenceNumber: number;
  totalQuestions: number;
  selectionReason: string;
  targetCompetency: string;
  status: string;
  hintsUsed: number;
  unlockedHints: string[];
  explanationSubmitted: boolean;
  startedAt: string;
  question: QuestionDetail;
}

export interface TestCaseResult {
  testCaseId: number;
  testIndex: number;
  sample: boolean;
  passed: boolean;
  inputData: string;
  expectedOutput: string;
  actualOutput: string;
  stdout: string;
  errorMessage: string;
  runtimeMs: number;
}

export interface Attempt {
  id: number;
  questionId: number;
  submittedCode: string;
  language: string;
  finalSubmission: boolean;
  compilationStatus: string;
  compilerOutput: string;
  testsPassed: number;
  testsTotal: number;
  runtimeMs: number;
  memoryKb: number;
  failureCategory: string;
  isolationModeUsed: string;
  testResults: TestCaseResult[];
  submittedAt: string;
}

export interface InterviewMessage {
  id: number;
  questionId: number | null;
  role: 'INTERVIEWER' | 'CANDIDATE';
  content: string;
  messageType: string;
  sourceType: string;
  fallbackReason?: string | null;
  createdAt: string;
}

export interface InterviewWorkspaceState {
  session: InterviewSessionSummary;
  remainingSeconds: number;
  serverNow: string;
  aiMode: 'LIVE_LLM' | 'FALLBACK_RUBRIC';
  liveLlmConfigured: boolean;
  activeQuestion: ActiveInterviewQuestion | null;
  currentQuestionAttempts: Attempt[];
  messages: InterviewMessage[];
}

export interface HintResponse {
  hintLevel: number;
  totalHintsAvailable: number;
  hintText: string;
  unlockedHints: string[];
  requestedAt: string;
}

export interface ExplanationEvaluationResult {
  competency: string;
  score: number;
  patternRecognitionScore: number;
  complexityAnalysisScore: number;
  communicationScore: number;
  confidence: number;
  evidence: string;
  identifiedMisconceptions: string[];
  recommendedFollowUp: string;
  timeComplexityCorrect: boolean;
  spaceComplexityCorrect: boolean;
  expectedTimeComplexity: string;
  expectedSpaceComplexity: string;
  sourceType: 'LIVE_LLM' | 'FALLBACK_RUBRIC';
  fallbackReason?: string | null;
}

export interface InterviewReport {
  sessionId: number;
  interviewType: string;
  configuredDifficulty: string;
  interviewStyle: string;
  startedAt: string;
  completedAt: string;
  status: string;
  overallScore: number;
  aiModeUsed: string;
  previousComparison: {
    previousSessionId: number;
    previousOverallScore: number;
    scoreDelta: number;
    previousCompletedAt: string;
  } | null;
  competencyScores: {
    competency: string;
    label: string;
    score: number;
    previousScore: number | null;
    delta: number | null;
    confidence: number;
    sufficientEvidence: boolean;
    sourceType: string;
  }[];
  strongestCompetencies: string[];
  weakestCompetencies: string[];
  topicResults: {
    topic: string;
    questionsAttempted: number;
    totalTestsPassed: number;
    totalTestsRun: number;
    averageScore: number;
  }[];
  questionReports: {
    questionId: number;
    sequenceNumber: number;
    title: string;
    topic: string;
    difficulty: string;
    selectionReason: string;
    targetCompetency: string;
    attemptsCount: number;
    finalCompilationStatus: string;
    testsPassed: number;
    testsTotal: number;
    bestRuntimeMs: number;
    expectedTimeComplexity: string;
    expectedSpaceComplexity: string;
    hintsUsed: number;
    explanationSubmitted: boolean;
  }[];
  diagnosedMistakes: {
    questionId: number;
    questionTitle: string;
    competency: string;
    competencyLabel: string;
    diagnosisCode: string;
    score: number;
    confidence: number;
    observedFacts: string[];
    inferredHypothesis: string;
    recommendedIntervention: string;
    sourceType: string;
  }[];
  recommendedPractice: Recommendation[];
}

export interface ResumeDimensionScore {
  dimensionKey: string;
  label: string;
  score: number;
  maxScore: number;
  rationale: string;
}

export interface ResumeAnalysisResult {
  overallScore: number;
  summary: string;
  disclaimer: string;
  dimensionScores: ResumeDimensionScore[];
  strengths: {
    title: string;
    resumeEvidence: string;
    relatedRequirement: string;
  }[];
  weaknesses: {
    gap: string;
    detail: string;
    relatedRequirement: string;
    actionableStep: string;
  }[];
  matchedRequirements: {
    requirement: string;
    resumeEvidence: string;
  }[];
  partiallyMatchedRequirements: {
    requirement: string;
    resumeEvidence: string;
    missingDetail: string;
  }[];
  missingRequirements: {
    requirement: string;
    statusNote: string;
  }[];
  technicalSkills: {
    skill: string;
    alignmentStatus: string;
    resumeEvidence: string;
  }[];
  experienceAssessment: {
    summary: string;
    relevantResponsibilities: string[];
    seniorityAndScopeNote: string;
  };
  projectAssessment: {
    summary: string;
    keyProjectsAndOutcomes: string[];
    measurableImpactNote: string;
  };
  improvementRecommendations: {
    priority: string;
    targetSection: string;
    recommendation: string;
    rationale: string;
  }[];
  suggestedResumeEdits: {
    section: string;
    originalContext: string;
    suggestedTruthfulWording: string;
    alignmentReason: string;
  }[];
  interviewPreparationTopics: {
    topic: string;
    reason: string;
    sampleQuestion: string;
  }[];
}

export interface ResumeAnalysisReport {
  id: number;
  originalFilename: string;
  fileContentType: string;
  fileSizeBytes: number;
  jobTitle: string | null;
  jobDescription: string;
  overallScore: number | null;
  analysisStatus: 'COMPLETED' | 'PROCESSING' | 'FAILED' | string;
  aiProvider: string | null;
  aiModel: string | null;
  errorCategory: string | null;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
  dimensionScores: ResumeDimensionScore[];
  result: ResumeAnalysisResult | null;
}

export interface ResumeAnalysisHistoryItem {
  id: number;
  originalFilename: string;
  fileSizeBytes: number;
  jobTitle: string | null;
  jobDescriptionSummary: string;
  overallScore: number | null;
  analysisStatus: 'COMPLETED' | 'PROCESSING' | 'FAILED' | string;
  aiProvider: string | null;
  aiModel: string | null;
  errorCategory: string | null;
  errorMessage: string | null;
  createdAt: string;
  completedAt: string | null;
}

export interface ResumeAnalysisPage {
  items: ResumeAnalysisHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
