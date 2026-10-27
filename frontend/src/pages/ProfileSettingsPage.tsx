import React, { useEffect, useState } from 'react';
import {
  AlertCircle,
  CheckCircle2,
  Cpu,
  KeyRound,
  Loader2,
  Moon,
  RefreshCw,
  Save,
  Server,
  ShieldCheck,
  Sparkles,
  Sun,
  User,
} from 'lucide-react';
import { api } from '../services/api';
import { useAuth } from '../features/auth/AuthContext';
import { useTheme } from '../features/theme/ThemeContext';
import type { AiConnectionTestResult, AiSystemStatus } from '../types/api';

export const ProfileSettingsPage: React.FC = () => {
  const { user, refreshProfile } = useAuth();
  const { theme, setTheme } = useTheme();

  const [displayName, setDisplayName] = useState(user?.displayName || '');
  const [targetRole, setTargetRole] = useState(
    user?.targetRole || 'Backend / Full-Stack Software Engineer'
  );
  const [experienceLevel, setExperienceLevel] = useState(user?.experienceLevel || 'MID_LEVEL');
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileMessage, setProfileMessage] = useState<string | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [savingPassword, setSavingPassword] = useState(false);
  const [passwordMessage, setPasswordMessage] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  const [aiStatus, setAiStatus] = useState<AiSystemStatus | null>(null);
  const [runnerStatus, setRunnerStatus] = useState<Record<string, unknown> | null>(null);

  const [testingAi, setTestingAi] = useState(false);
  const [aiTestResult, setAiTestResult] = useState<AiConnectionTestResult | null>(null);
  const [aiTestError, setAiTestError] = useState<string | null>(null);

  useEffect(() => {
    if (user) {
      setDisplayName(user.displayName);
      setTargetRole(user.targetRole || 'Backend / Full-Stack Software Engineer');
      setExperienceLevel(user.experienceLevel || 'MID_LEVEL');
    }
  }, [user]);

  useEffect(() => {
    api.dashboard
      .getSystemStatus()
      .then((res) => {
        setAiStatus(res.ai);
        setRunnerStatus(res.runner);
      })
      .catch(() => {});
  }, []);

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setSavingProfile(true);
    setProfileMessage(null);
    setProfileError(null);
    try {
      await api.users.updateMe({
        displayName: displayName.trim(),
        targetRole: targetRole.trim(),
        experienceLevel,
        themePreference: theme,
      });
      await refreshProfile();
      setProfileMessage('Profile preferences updated and persisted to MySQL.');
    } catch (err: any) {
      setProfileError(err.response?.data?.message || err?.message || 'Failed to update profile.');
    } finally {
      setSavingProfile(false);
    }
  };

  const handleThemeSelect = (nextTheme: 'dark' | 'light') => {
    setTheme(nextTheme);
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordMessage(null);
    setPasswordError(null);

    if (newPassword.length < 8) {
      setPasswordError('New password must be at least 8 characters long.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setPasswordError('New password and confirmation do not match.');
      return;
    }

    setSavingPassword(true);
    try {
      const res = await api.users.changePassword({ currentPassword, newPassword });
      setPasswordMessage(res.message || 'Password updated successfully.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: any) {
      setPasswordError(err.response?.data?.message || err?.message || 'Failed to update password.');
    } finally {
      setSavingPassword(false);
    }
  };

  const handleTestAiConnection = async () => {
    setTestingAi(true);
    setAiTestError(null);
    try {
      const result = await api.dashboard.testAiConnection();
      setAiTestResult(result);
      setAiStatus((prev) => ({
        ...(prev || {
          aiMode: result.aiMode,
          liveLlmConfigured: result.liveLlmConfigured,
          configured: result.configured,
          provider: result.provider,
          model: result.model,
          connectionStatus: result.connectionStatus,
        }),
        aiMode: result.aiMode,
        liveLlmConfigured: result.liveLlmConfigured,
        configured: result.configured,
        provider: result.provider,
        model: result.model,
        connectionStatus: result.connectionStatus,
        lastTestedAt: result.lastTestedAt,
        latencyMs: result.latencyMs,
        errorCategory: result.errorCategory,
        message: result.message,
        troubleshootingRecommendation: result.troubleshootingRecommendation,
      }));
    } catch (err: any) {
      setAiTestError(
        err.response?.data?.message || err?.message || 'Failed to execute AI connection test.'
      );
    } finally {
      setTestingAi(false);
    }
  };

  const connectionStatus =
    aiStatus?.connectionStatus ||
    (aiStatus?.liveLlmConfigured ? 'CONFIGURED_UNVERIFIED' : 'NOT_CONFIGURED');

  const getStatusBadgeClasses = (status: string) => {
    if (status === 'CONNECTED') {
      return 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30';
    }
    if (status === 'CONFIGURED_UNVERIFIED') {
      return 'bg-cyan-500/15 text-cyan-400 border border-cyan-500/30';
    }
    if (status === 'NOT_CONFIGURED') {
      return 'bg-amber-500/15 text-amber-400 border border-amber-500/30';
    }
    return 'bg-rose-500/15 text-rose-400 border border-rose-500/30';
  };

  const isTestSuccess = aiTestResult?.connectionStatus === 'CONNECTED';

  return (
    <div className="max-w-4xl mx-auto p-6 space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-100">User Profile &amp; Platform Settings</h1>
        <p className="text-sm text-slate-400 mt-1">
          Manage your candidate profile, theme appearance, security credentials, and verify live AI
          &amp; code runner telemetry.
        </p>
      </div>

      {/* Appearance & Theme Settings */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-6 space-y-4">
        <div className="flex items-center justify-between border-b border-slate-800 pb-4">
          <div className="flex items-center gap-2.5">
            {theme === 'dark' ? (
              <Moon className="h-5 w-5 text-indigo-400" />
            ) : (
              <Sun className="h-5 w-5 text-amber-400" />
            )}
            <div>
              <h2 className="text-base font-semibold text-slate-100">Appearance &amp; Theme</h2>
              <p className="text-xs text-slate-400">
                Choose your preferred interface theme. Automatically synced across sessions and Code
                Editor.
              </p>
            </div>
          </div>
          <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-800 text-slate-200 capitalize">
            Active: {theme} mode
          </span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <button
            type="button"
            data-testid="select-dark-theme-btn"
            onClick={() => handleThemeSelect('dark')}
            className={`p-4 rounded-xl border text-left transition-all flex items-center justify-between ${
              theme === 'dark'
                ? 'border-indigo-500 bg-indigo-500/10 ring-1 ring-indigo-500/40'
                : 'border-slate-800 bg-slate-950/60 hover:border-slate-700'
            }`}
          >
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-lg bg-slate-900 border border-slate-700 flex items-center justify-center text-indigo-400">
                <Moon className="w-4 h-4" />
              </div>
              <div>
                <p className="text-sm font-semibold text-slate-100">Dark Mode</p>
                <p className="text-xs text-slate-400">
                  High-contrast slate workspace with vs-dark editor
                </p>
              </div>
            </div>
            {theme === 'dark' && <CheckCircle2 className="w-5 h-5 text-indigo-400 shrink-0" />}
          </button>

          <button
            type="button"
            data-testid="select-light-theme-btn"
            onClick={() => handleThemeSelect('light')}
            className={`p-4 rounded-xl border text-left transition-all flex items-center justify-between ${
              theme === 'light'
                ? 'border-indigo-500 bg-indigo-500/10 ring-1 ring-indigo-500/40'
                : 'border-slate-800 bg-slate-950/60 hover:border-slate-700'
            }`}
          >
            <div className="flex items-center gap-3">
              <div className="w-9 h-9 rounded-lg bg-slate-900 border border-slate-700 flex items-center justify-center text-amber-400">
                <Sun className="w-4 h-4" />
              </div>
              <div>
                <p className="text-sm font-semibold text-slate-100">Light Mode</p>
                <p className="text-xs text-slate-400">
                  Daylight-optimized surfaces with light code editor
                </p>
              </div>
            </div>
            {theme === 'light' && <CheckCircle2 className="w-5 h-5 text-indigo-400 shrink-0" />}
          </button>
        </div>
      </div>

      {/* Live Infrastructure Telemetry & AI Connection Test */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-6 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 border-b border-slate-800 pb-4">
          <div className="flex items-center gap-2.5">
            <Server className="h-5 w-5 text-violet-400" />
            <div>
              <h2 className="text-base font-semibold text-slate-100">
                Runtime &amp; AI Provider Diagnostics
              </h2>
              <p className="text-xs text-slate-400">
                Verify live LLM connectivity, inspect diagnostic status, and check isolated Java
                runner health.
              </p>
            </div>
          </div>

          <button
            type="button"
            data-testid="test-ai-connection-btn"
            disabled={testingAi}
            onClick={handleTestAiConnection}
            className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 px-4 py-2 text-xs font-semibold text-white transition-colors self-start sm:self-center shadow-sm"
          >
            {testingAi ? (
              <>
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
                Testing AI Connection...
              </>
            ) : (
              <>
                <Sparkles className="h-3.5 w-3.5" />
                Test AI Connection
              </>
            )}
          </button>
        </div>

        {/* AI Connection Test Result Banner */}
        {aiTestResult && (
          <div
            data-testid="ai-connection-test-result"
            className={`rounded-xl p-4 border space-y-2 text-xs ${
              isTestSuccess
                ? 'bg-emerald-500/10 border-emerald-500/30 text-slate-200'
                : 'bg-rose-500/10 border-rose-500/30 text-slate-200'
            }`}
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                {isTestSuccess ? (
                  <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                ) : (
                  <AlertCircle className="w-4 h-4 text-rose-400 shrink-0" />
                )}
                <span className="font-bold text-sm text-white">
                  {isTestSuccess
                    ? 'Live AI Connection Verified'
                    : `AI Connection Test Failed (${aiTestResult.connectionStatus})`}
                </span>
              </div>
              <div className="flex items-center gap-2 font-mono text-[11px]">
                <span className="px-2 py-0.5 rounded bg-slate-900 border border-slate-700 text-slate-300">
                  {aiTestResult.provider} / {aiTestResult.model}
                </span>
                {aiTestResult.latencyMs !== null && aiTestResult.latencyMs > 0 && (
                  <span className="text-slate-400">{aiTestResult.latencyMs} ms</span>
                )}
              </div>
            </div>

            {aiTestResult.message && <p className="text-slate-300">{aiTestResult.message}</p>}

            {!isTestSuccess && aiTestResult.troubleshootingRecommendation && (
              <div className="p-3 rounded-lg bg-slate-950/70 border border-slate-800 space-y-1">
                <p className="font-semibold text-amber-300">Troubleshooting &amp; Next Step:</p>
                <p className="text-slate-300 leading-relaxed">
                  {aiTestResult.troubleshootingRecommendation}
                </p>
              </div>
            )}

            {!isTestSuccess && (
              <div className="pt-1">
                <button
                  type="button"
                  onClick={handleTestAiConnection}
                  disabled={testingAi}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-200"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                  Retry Connection Test
                </button>
              </div>
            )}
          </div>
        )}

        {aiTestError && (
          <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 p-3 text-xs text-rose-200 flex items-center justify-between gap-2">
            <span>{aiTestError}</span>
            <button
              type="button"
              onClick={handleTestAiConnection}
              className="px-2.5 py-1 rounded bg-slate-800 text-slate-200 font-semibold"
            >
              Retry
            </button>
          </div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 text-xs">
          <div className="rounded-lg border border-slate-800 bg-slate-950/70 p-4 space-y-2.5">
            <div className="flex items-center justify-between gap-2">
              <span className="font-semibold text-slate-300 flex items-center gap-1.5">
                <Cpu className="h-4 w-4 text-indigo-400" /> AI Provider &amp; Evaluator
              </span>
              <span
                data-testid="settings-ai-connection-status"
                className={`rounded-full px-2.5 py-0.5 text-[10px] font-mono font-semibold ${getStatusBadgeClasses(
                  connectionStatus
                )}`}
              >
                {connectionStatus}
              </span>
            </div>
            <p className="text-slate-400">
              Provider:{' '}
              <strong className="text-slate-200">{aiStatus?.provider || 'gemini'}</strong> · Model:{' '}
              <strong className="text-slate-200">{aiStatus?.model || 'gemini-2.5-flash'}</strong>
            </p>
            <p className="text-slate-400">
              Evaluation Mode:{' '}
              <strong className="text-slate-200 font-mono">
                {aiStatus?.aiMode || 'FALLBACK_RUBRIC'}
              </strong>{' '}
              · Credentials Present:{' '}
              <strong className="text-slate-200">
                {aiStatus?.configured || aiStatus?.liveLlmConfigured ? 'Yes' : 'No'}
              </strong>
            </p>
            {aiStatus?.lastTestedAt && (
              <p className="text-[11px] text-slate-400">
                Last Verified: {new Date(aiStatus.lastTestedAt).toLocaleString()}
              </p>
            )}
            <p className="text-[11px] text-slate-400 leading-relaxed pt-1 border-t border-slate-800/80">
              {aiStatus?.troubleshootingRecommendation ||
                (aiStatus?.configured || aiStatus?.liveLlmConfigured
                  ? 'API key is configured. Click "Test AI Connection" above to verify live model responses.'
                  : 'No external AI_API_KEY configured. Operating in deterministic rubric fallback mode with explicit labeling.')}
            </p>
          </div>

          <div className="rounded-lg border border-slate-800 bg-slate-950/70 p-4 space-y-2.5">
            <div className="flex items-center justify-between">
              <span className="font-semibold text-slate-300 flex items-center gap-1.5">
                <ShieldCheck className="h-4 w-4 text-teal-400" /> Isolated Code Execution Service
              </span>
              <span className="rounded-full bg-emerald-500/15 text-emerald-400 border border-emerald-500/30 px-2.5 py-0.5 text-[10px] font-semibold">
                {String(runnerStatus?.status || 'UP')}
              </span>
            </div>
            <p className="text-slate-400">
              Isolation Mode:{' '}
              <strong className="text-slate-200">
                {String(runnerStatus?.isolationMode || 'RESTRICTED_LOCAL_SANDBOX')}
              </strong>
            </p>
            <p className="text-[11px] text-slate-400 leading-relaxed pt-1 border-t border-slate-800/80">
              Enforces static AST/API security validation, per-test timeouts, heap memory limits,
              and output truncation. Deterministic compilation and test execution remain
              authoritative.
            </p>
          </div>
        </div>
      </div>

      {/* Profile Settings Form */}
      <form
        onSubmit={handleSaveProfile}
        className="rounded-xl border border-slate-800 bg-slate-900/60 p-6 space-y-5"
      >
        <div className="flex items-center gap-2.5 border-b border-slate-800 pb-4">
          <User className="h-5 w-5 text-indigo-400" />
          <div>
            <h2 className="text-base font-semibold text-slate-100">Candidate Profile</h2>
            <p className="text-xs text-slate-400">
              Used to calibrate interview difficulty expectations and communication rubric depth.
            </p>
          </div>
        </div>

        {profileMessage && (
          <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs text-emerald-300 flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
            {profileMessage}
          </div>
        )}
        {profileError && (
          <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 p-3 text-xs text-rose-300">
            {profileError}
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1.5">
              Account Email (Read-only)
            </label>
            <input
              type="email"
              value={user?.email || ''}
              disabled
              className="w-full rounded-lg border border-slate-800 bg-slate-950/60 px-3.5 py-2 text-xs text-slate-500 cursor-not-allowed"
            />
          </div>

          <div>
            <label htmlFor="displayName" className="block text-xs font-medium text-slate-300 mb-1.5">
              Display Name
            </label>
            <input
              id="displayName"
              type="text"
              required
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3.5 py-2 text-xs text-slate-100 focus:border-indigo-500 focus:outline-none"
            />
          </div>

          <div>
            <label htmlFor="targetRole" className="block text-xs font-medium text-slate-300 mb-1.5">
              Target Engineering Role
            </label>
            <input
              id="targetRole"
              type="text"
              required
              value={targetRole}
              onChange={(e) => setTargetRole(e.target.value)}
              className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3.5 py-2 text-xs text-slate-100 focus:border-indigo-500 focus:outline-none"
            />
          </div>

          <div>
            <label
              htmlFor="experienceLevel"
              className="block text-xs font-medium text-slate-300 mb-1.5"
            >
              Experience Level
            </label>
            <select
              id="experienceLevel"
              value={experienceLevel}
              onChange={(e) => setExperienceLevel(e.target.value)}
              className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3.5 py-2 text-xs text-slate-100 focus:border-indigo-500 focus:outline-none"
            >
              <option value="INTERN_NEW_GRAD">Intern / New Grad</option>
              <option value="JUNIOR">Junior Engineer (1–2 yrs)</option>
              <option value="MID_LEVEL">Mid-Level Engineer (3–5 yrs)</option>
              <option value="SENIOR">Senior Engineer (5+ yrs)</option>
              <option value="STAFF_PRINCIPAL">Staff / Principal Engineer</option>
            </select>
          </div>
        </div>

        <div className="flex items-center justify-between pt-2">
          <div className="text-xs text-slate-400">
            Primary Coding Language: <strong className="text-slate-200">Java (JDK 21)</strong>
          </div>
          <button
            type="submit"
            disabled={savingProfile}
            className="inline-flex items-center gap-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 px-4 py-2 text-xs font-semibold text-white transition-colors"
          >
            <Save className="h-3.5 w-3.5" />
            {savingProfile ? 'Saving...' : 'Save Profile Changes'}
          </button>
        </div>
      </form>

      {/* Password Security Form */}
      <form
        onSubmit={handleChangePassword}
        className="rounded-xl border border-slate-800 bg-slate-900/60 p-6 space-y-5"
      >
        <div className="flex items-center gap-2.5 border-b border-slate-800 pb-4">
          <KeyRound className="h-5 w-5 text-teal-400" />
          <div>
            <h2 className="text-base font-semibold text-slate-100">Change Password</h2>
            <p className="text-xs text-slate-400">
              Passwords are hashed with BCrypt before persistence in MySQL.
            </p>
          </div>
        </div>

        {passwordMessage && (
          <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-xs text-emerald-300 flex items-center gap-2">
            <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
            {passwordMessage}
          </div>
        )}
        {passwordError && (
          <div className="rounded-lg border border-rose-500/30 bg-rose-500/10 p-3 text-xs text-rose-300">
            {passwordError}
          </div>
        )}

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label
              htmlFor="currentPassword"
              className="block text-xs font-medium text-slate-300 mb-1.5"
            >
              Current Password
            </label>
            <input
              id="currentPassword"
              type="password"
              required
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3.5 py-2 text-xs text-slate-100 focus:border-indigo-500 focus:outline-none"
            />
          </div>

          <div>
            <label htmlFor="newPassword" className="block text-xs font-medium text-slate-300 mb-1.5">
              New Password (min 8 chars)
            </label>
            <input
              id="newPassword"
              type="password"
              required
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3.5 py-2 text-xs text-slate-100 focus:border-indigo-500 focus:outline-none"
            />
          </div>

          <div>
            <label
              htmlFor="confirmPassword"
              className="block text-xs font-medium text-slate-300 mb-1.5"
            >
              Confirm New Password
            </label>
            <input
              id="confirmPassword"
              type="password"
              required
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="w-full rounded-lg border border-slate-700 bg-slate-950 px-3.5 py-2 text-xs text-slate-100 focus:border-indigo-500 focus:outline-none"
            />
          </div>
        </div>

        <div className="flex justify-end">
          <button
            type="submit"
            disabled={savingPassword}
            className="inline-flex items-center gap-2 rounded-lg border border-slate-700 bg-slate-950 hover:bg-slate-800 disabled:opacity-50 px-4 py-2 text-xs font-semibold text-slate-200 transition-colors"
          >
            <ShieldCheck className="h-3.5 w-3.5 text-teal-400" />
            {savingPassword ? 'Updating...' : 'Update Password'}
          </button>
        </div>
      </form>
    </div>
  );
};
