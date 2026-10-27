import axios from 'axios';
import type {
  AiConnectionTestResult,
  AiSystemStatus,
  AuthResponse,
  Attempt,
  DashboardData,
  ExplanationEvaluationResult,
  HintResponse,
  InterviewMessage,
  InterviewReport,
  InterviewSessionSummary,
  InterviewWorkspaceState,
  QuestionDetail,
  QuestionSummary,
  Recommendation,
  ResumeAnalysisPage,
  ResumeAnalysisReport,
  SkillMetric,
  UserProfile,
} from '../types/api';

const TOKEN_STORAGE_KEY = 'atis_access_token';
const USER_STORAGE_KEY = 'atis_user_profile';

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  headers: {
    'Content-Type': 'application/json',
  },
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      localStorage.removeItem(USER_STORAGE_KEY);
      if (
        typeof window !== 'undefined' &&
        !window.location.pathname.startsWith('/login') &&
        !window.location.pathname.startsWith('/register') &&
        window.location.pathname !== '/'
      ) {
        window.location.href = '/login?expired=1';
      }
    }
    return Promise.reject(error);
  }
);

export const storage = {
  getToken: () => localStorage.getItem(TOKEN_STORAGE_KEY),
  setToken: (token: string) => localStorage.setItem(TOKEN_STORAGE_KEY, token),
  clearToken: () => {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
  },
  getUser: (): UserProfile | null => {
    const raw = localStorage.getItem(USER_STORAGE_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as UserProfile;
    } catch {
      return null;
    }
  },
  setUser: (user: UserProfile) => {
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
  },
};

export const api = {
  auth: {
    register: async (payload: {
      email: string;
      password: string;
      displayName: string;
      targetRole: string;
      experienceLevel: string;
    }) => {
      const res = await http.post<AuthResponse>('/api/auth/register', payload);
      return res.data;
    },
    login: async (payload: { email: string; password: string }) => {
      const res = await http.post<AuthResponse>('/api/auth/login', payload);
      return res.data;
    },
    logout: async () => {
      await http.post('/api/auth/logout');
    },
  },
  users: {
    getMe: async () => {
      const res = await http.get<UserProfile>('/api/users/me');
      return res.data;
    },
    updateMe: async (payload: {
      displayName: string;
      targetRole: string;
      experienceLevel: string;
      themePreference?: 'dark' | 'light' | 'DARK' | 'LIGHT' | null;
    }) => {
      const res = await http.put<UserProfile>('/api/users/me', payload);
      return res.data;
    },
    updateTheme: async (theme: 'dark' | 'light' | 'DARK' | 'LIGHT') => {
      const res = await http.patch<UserProfile>('/api/users/me/theme', {
        theme: theme.toLowerCase(),
      });
      return res.data;
    },
    changePassword: async (payload: { currentPassword: string; newPassword: string }) => {
      const res = await http.put<{ message: string }>('/api/users/me/password', payload);
      return res.data;
    },
    getSkills: async () => {
      const res = await http.get<SkillMetric[]>('/api/users/me/skills');
      return res.data;
    },
    getProgress: async () => {
      const res = await http.get<DashboardData>('/api/users/me/progress');
      return res.data;
    },
    getRecommendations: async () => {
      const res = await http.get<Recommendation[]>('/api/users/me/recommendations');
      return res.data;
    },
    refreshRecommendations: async () => {
      const res = await http.post<Recommendation[]>('/api/users/me/recommendations/refresh');
      return res.data;
    },
    updateRecommendationStatus: async (id: number, status: string) => {
      const res = await http.patch<Recommendation>(`/api/users/me/recommendations/${id}`, { status });
      return res.data;
    },
  },
  dashboard: {
    get: async () => {
      const res = await http.get<DashboardData>('/api/dashboard');
      return res.data;
    },
    getSystemStatus: async () => {
      const res = await http.get<{
        ai: AiSystemStatus;
        runner: Record<string, unknown>;
      }>('/api/system/status');
      return res.data;
    },
    testAiConnection: async () => {
      const res = await http.post<AiConnectionTestResult>('/api/system/ai/test');
      return res.data;
    },
  },
  system: {
    getStatus: async (): Promise<AiSystemStatus> => {
      const res = await http.get<{
        ai: AiSystemStatus;
        runner: Record<string, unknown>;
      }>('/api/system/status');
      return res.data.ai;
    },
    testAiConnection: async (): Promise<AiConnectionTestResult> => {
      const res = await http.post<AiConnectionTestResult>('/api/system/ai/test');
      return res.data;
    },
  },
  questions: {
    list: async (params?: { topic?: string; difficulty?: string; questionType?: string }) => {
      const res = await http.get<QuestionSummary[]>('/api/questions', { params });
      return res.data;
    },
    getById: async (id: number) => {
      const res = await http.get<QuestionDetail>(`/api/questions/${id}`);
      return res.data;
    },
  },
  interviews: {
    create: async (payload: {
      interviewType: string;
      difficulty: string;
      topics: string[];
      durationMinutes: number;
      questionCount: number;
      interviewStyle: string;
    }) => {
      const res = await http.post<InterviewSessionSummary>('/api/interviews', payload);
      return res.data;
    },
    list: async () => {
      const res = await http.get<InterviewSessionSummary[]>('/api/interviews');
      return res.data;
    },
    getWorkspace: async (id: number) => {
      const res = await http.get<InterviewWorkspaceState>(`/api/interviews/${id}`);
      return res.data;
    },
    start: async (id: number) => {
      const res = await http.post<InterviewWorkspaceState>(`/api/interviews/${id}/start`);
      return res.data;
    },
    nextQuestion: async (id: number) => {
      const res = await http.post<InterviewWorkspaceState>(`/api/interviews/${id}/next-question`);
      return res.data;
    },
    end: async (id: number) => {
      const res = await http.post<InterviewReport>(`/api/interviews/${id}/end`);
      return res.data;
    },
    submitCode: async (id: number, payload: { code: string; language?: string; sampleOnly: boolean }) => {
      const res = await http.post<Attempt>(`/api/interviews/${id}/submissions`, {
        code: payload.code,
        language: payload.language || 'JAVA',
        sampleOnly: payload.sampleOnly,
      });
      return res.data;
    },
    submitExplanation: async (
      id: number,
      payload: {
        algorithmExplanation: string;
        timeComplexity: string;
        spaceComplexity: string;
        tradeOffs: string;
      }
    ) => {
      const res = await http.post<ExplanationEvaluationResult>(`/api/interviews/${id}/explanations`, payload);
      return res.data;
    },
    requestHint: async (id: number) => {
      const res = await http.post<HintResponse>(`/api/interviews/${id}/hints`);
      return res.data;
    },
    sendMessage: async (id: number, payload: { content: string; currentCode: string }) => {
      const res = await http.post<InterviewMessage>(`/api/interviews/${id}/messages`, payload);
      return res.data;
    },
    getReport: async (id: number) => {
      const res = await http.get<InterviewReport>(`/api/interviews/${id}/report`);
      return res.data;
    },
  },
  resumeAnalyses: {
    create: async (payload: { file: File; jobTitle?: string; jobDescription: string }) => {
      const formData = new FormData();
      formData.append('file', payload.file);
      if (payload.jobTitle && payload.jobTitle.trim()) {
        formData.append('jobTitle', payload.jobTitle.trim());
      }
      formData.append('jobDescription', payload.jobDescription);
      const res = await http.post<ResumeAnalysisReport>('/api/resume-analyses', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      return res.data;
    },
    analyze: async (file: File, jobDescription: string, jobTitle?: string) => {
      const formData = new FormData();
      formData.append('file', file);
      if (jobTitle && jobTitle.trim()) {
        formData.append('jobTitle', jobTitle.trim());
      }
      formData.append('jobDescription', jobDescription);
      const res = await http.post<ResumeAnalysisReport>('/api/resume-analyses', formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });
      return res.data;
    },
    list: async (params?: {
      search?: string;
      status?: string;
      minScore?: number;
      maxScore?: number;
      sortBy?: string;
      sort?: string;
      page?: number;
      size?: number;
    }) => {
      const queryParams = params
        ? {
            ...params,
            sortBy: params.sortBy || params.sort,
          }
        : undefined;
      const res = await http.get<ResumeAnalysisPage>('/api/resume-analyses', {
        params: queryParams,
      });
      return res.data;
    },
    getById: async (id: number) => {
      const res = await http.get<ResumeAnalysisReport>(`/api/resume-analyses/${id}`);
      return res.data;
    },
    deleteById: async (id: number) => {
      const res = await http.delete<{ deleted: boolean; id: number; message: string }>(
        `/api/resume-analyses/${id}`
      );
      return res.data;
    },
  },
};
