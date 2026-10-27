import React from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './features/auth/AuthContext';
import { ThemeProvider } from './features/theme/ThemeContext';
import { AppLayout } from './layouts/AppLayout';
import { LandingPage } from './pages/LandingPage';
import { AuthPage } from './pages/AuthPage';
import { DashboardPage } from './pages/DashboardPage';
import { InterviewConfigPage } from './pages/InterviewConfigPage';
import { InterviewWorkspacePage } from './pages/InterviewWorkspacePage';
import { InterviewReportPage } from './pages/InterviewReportPage';
import { SkillAnalyticsPage } from './pages/SkillAnalyticsPage';
import { InterviewHistoryPage } from './pages/InterviewHistoryPage';
import { RecommendationsPage } from './pages/RecommendationsPage';
import { ProfileSettingsPage } from './pages/ProfileSettingsPage';
import { ResumeScreenerPage } from './pages/ResumeScreenerPage';

const RequireAuth: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { user, loading } = useAuth();
  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex items-center justify-center text-slate-300 text-sm">
        Loading session...
      </div>
    );
  }
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <>{children}</>;
};

export const AppRoutes: React.FC = () => {
  return (
    <Routes>
      {/* 1. Landing page */}
      <Route path="/" element={<LandingPage />} />

      {/* 2. Registration and login */}
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/register" element={<AuthPage mode="register" />} />

      {/* 5. Fullscreen Live Interview Workspace */}
      <Route
        path="/interviews/:id/workspace"
        element={
          <RequireAuth>
            <InterviewWorkspacePage />
          </RequireAuth>
        }
      />

      {/* Authenticated Shell with Navigation */}
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        {/* 3. Main dashboard */}
        <Route path="/dashboard" element={<DashboardPage />} />

        {/* 4. Interview configuration */}
        <Route path="/interviews/new" element={<InterviewConfigPage />} />

        {/* 6. Interview feedback and report */}
        <Route path="/interviews/:id/report" element={<InterviewReportPage />} />

        {/* 7. Skill analytics */}
        <Route path="/analytics" element={<SkillAnalyticsPage />} />

        {/* 8. Interview history */}
        <Route path="/history" element={<InterviewHistoryPage />} />

        {/* 9. Personalized practice recommendations */}
        <Route path="/recommendations" element={<RecommendationsPage />} />

        {/* 10. AI Resume Screener & Screening History */}
        <Route path="/resume-screener" element={<ResumeScreenerPage />} />
        <Route path="/resume-screener/history" element={<ResumeScreenerPage />} />
        <Route path="/resume-screener/:id" element={<ResumeScreenerPage />} />

        {/* 11. User profile and settings */}
        <Route path="/settings" element={<ProfileSettingsPage />} />
      </Route>

      {/* Fallback */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
};

export const App: React.FC = () => {
  return (
    <ThemeProvider>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </ThemeProvider>
  );
};

export default App;
