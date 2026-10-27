import React, { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import {
  BarChart3,
  BookOpen,
  Code2,
  Cpu,
  FileSearch,
  History,
  LayoutDashboard,
  LogOut,
  PlayCircle,
  Settings,
  Sparkles,
} from 'lucide-react';
import { useAuth } from '../features/auth/AuthContext';
import { ThemeToggle } from '../features/theme/ThemeContext';
import { api } from '../services/api';
import type { AiConnectionStatus } from '../types/api';

function getAiBadgeAppearance(connectionStatus?: AiConnectionStatus | string, aiMode?: string) {
  switch (connectionStatus) {
    case 'CONNECTED':
      return {
        label: 'CONNECTED',
        badgeClass: 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30',
        description: null,
      };
    case 'CONFIGURED_UNVERIFIED':
      return {
        label: 'UNVERIFIED',
        badgeClass: 'bg-cyan-500/15 text-cyan-400 border border-cyan-500/30',
        description:
          'API key configured (not yet verified). Run a connection test in Profile & Settings.',
      };
    case 'AUTH_ERROR':
      return {
        label: 'AUTH ERROR',
        badgeClass: 'bg-rose-500/15 text-rose-400 border border-rose-500/30',
        description: 'Provider rejected the configured AI_API_KEY. Using fallback rubric.',
      };
    case 'QUOTA_OR_BILLING_ERROR':
      return {
        label: 'QUOTA LIMIT',
        badgeClass: 'bg-rose-500/15 text-rose-400 border border-rose-500/30',
        description: 'Provider reported quota or billing limits. Using fallback rubric.',
      };
    case 'MODEL_OR_ENDPOINT_ERROR':
      return {
        label: 'MODEL/URL ERR',
        badgeClass: 'bg-rose-500/15 text-rose-400 border border-rose-500/30',
        description: 'Invalid AI_MODEL or AI_BASE_URL for provider. Using fallback rubric.',
      };
    case 'RATE_LIMITED':
      return {
        label: 'RATE LIMITED',
        badgeClass: 'bg-amber-500/15 text-amber-400 border border-amber-500/30',
        description: 'Provider rate limit reached temporarily. Using fallback rubric.',
      };
    case 'TIMEOUT':
      return {
        label: 'TIMEOUT',
        badgeClass: 'bg-amber-500/15 text-amber-400 border border-amber-500/30',
        description: 'Provider request timed out. Using fallback rubric.',
      };
    case 'PROVIDER_ERROR':
      return {
        label: 'PROVIDER ERR',
        badgeClass: 'bg-rose-500/15 text-rose-400 border border-rose-500/30',
        description: 'Upstream AI provider error. Using fallback rubric.',
      };
    case 'NOT_CONFIGURED':
    default:
      if (aiMode === 'LIVE_LLM') {
        return {
          label: 'LIVE LLM',
          badgeClass: 'bg-emerald-500/15 text-emerald-400 border border-emerald-500/30',
          description: null,
        };
      }
      return {
        label: 'FALLBACK MODE',
        badgeClass: 'bg-amber-500/15 text-amber-400 border border-amber-500/30',
        description: 'No LLM API key configured — using deterministic rubric evaluation.',
      };
  }
}

export const AppLayout: React.FC = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sysStatus, setSysStatus] = useState<{
    aiMode: string;
    connectionStatus?: AiConnectionStatus | string;
    provider?: string;
    model?: string;
    runnerStatus: string;
    isolationMode: string;
  } | null>(null);

  useEffect(() => {
    api.dashboard
      .getSystemStatus()
      .then((res) => {
        setSysStatus({
          aiMode: res.ai?.aiMode || 'FALLBACK_RUBRIC',
          connectionStatus: res.ai?.connectionStatus,
          provider: res.ai?.provider,
          model: res.ai?.model,
          runnerStatus: String(res.runner?.status || 'DOWN'),
          isolationMode: String(res.runner?.effectiveIsolationMode || 'RESTRICTED_LOCAL_SANDBOX'),
        });
      })
      .catch(() => {
        setSysStatus({
          aiMode: 'FALLBACK_RUBRIC',
          connectionStatus: 'NOT_CONFIGURED',
          runnerStatus: 'DOWN',
          isolationMode: 'UNAVAILABLE',
        });
      });
  }, []);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const navItems = [
    { to: '/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/interviews/new', label: 'Start Interview', icon: PlayCircle },
    { to: '/resume-screener', label: 'Resume Screener', icon: FileSearch },
    { to: '/analytics', label: 'Skill Analytics', icon: BarChart3 },
    { to: '/history', label: 'Interview History', icon: History },
    { to: '/recommendations', label: 'Practice Queue', icon: BookOpen },
    { to: '/settings', label: 'Profile & Settings', icon: Settings },
  ];

  const aiBadge = getAiBadgeAppearance(sysStatus?.connectionStatus, sysStatus?.aiMode);

  return (
    <div className="min-h-screen bg-surface-page text-content-primary flex flex-col lg:flex-row">
      {/* Sidebar */}
      <aside className="w-full lg:w-64 bg-surface-sidebar border-b lg:border-b-0 lg:border-r border-edge-subtle flex flex-col justify-between shrink-0">
        <div>
          <div className="h-16 px-4 flex items-center justify-between border-b border-slate-800/80 gap-2">
            <Link to="/dashboard" className="flex items-center gap-2.5 group min-w-0">
              <div className="w-8 h-8 rounded-lg bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400 shrink-0">
                <Code2 className="w-4 h-4" />
              </div>
              <div className="min-w-0">
                <span className="font-semibold text-sm tracking-tight text-slate-100 block leading-none truncate">
                  AdaptiveEval
                </span>
                <span className="text-[11px] font-mono text-slate-400">Java Interview AI</span>
              </div>
            </Link>
            <ThemeToggle compact />
          </div>

          <nav className="p-3 space-y-1 flex lg:flex-col overflow-x-auto">
            {navItems.map((item) => {
              const Icon = item.icon;
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    `flex items-center gap-2.5 px-3 py-2.5 rounded-lg text-sm font-medium transition-colors whitespace-nowrap ${
                      isActive
                        ? 'bg-blue-600/15 text-blue-400 border border-blue-500/30'
                        : 'text-slate-400 hover:text-slate-200 hover:bg-slate-800/50 border border-transparent'
                    }`
                  }
                >
                  <Icon className="w-4 h-4 shrink-0" />
                  <span>{item.label}</span>
                </NavLink>
              );
            })}
          </nav>
        </div>

        {/* System Status & User Footer */}
        <div className="p-4 border-t border-slate-800/80 space-y-3 hidden lg:block">
          {sysStatus && (
            <div className="p-3 rounded-lg bg-[#090d16] border border-slate-800/90 space-y-2 text-xs">
              <div className="flex items-center justify-between">
                <span className="text-slate-400 flex items-center gap-1.5">
                  <Sparkles className="w-3.5 h-3.5 text-violet-400" />
                  AI Evaluator
                </span>
                <span
                  data-testid="sidebar-ai-status-badge"
                  className={`px-1.5 py-0.5 rounded font-mono text-[10px] ${aiBadge.badgeClass}`}
                >
                  {aiBadge.label}
                </span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-slate-400 flex items-center gap-1.5">
                  <Cpu className="w-3.5 h-3.5 text-teal-400" />
                  Java Runner
                </span>
                <span
                  className={`px-1.5 py-0.5 rounded font-mono text-[10px] ${
                    sysStatus.runnerStatus === 'UP'
                      ? 'bg-teal-500/15 text-teal-300 border border-teal-500/30'
                      : 'bg-rose-500/15 text-rose-300 border border-rose-500/30'
                  }`}
                >
                  {sysStatus.runnerStatus === 'UP' ? 'ISOLATED UP' : 'OFFLINE'}
                </span>
              </div>
              {aiBadge.description && (
                <p className="text-[11px] text-slate-400 leading-tight pt-1 border-t border-slate-800/80">
                  {aiBadge.description}
                </p>
              )}
            </div>
          )}

          {user && (
            <div className="flex items-center justify-between pt-1">
              <div className="min-w-0">
                <p className="text-xs font-medium text-slate-200 truncate">{user.displayName}</p>
                <p className="text-[11px] text-slate-400 truncate">{user.targetRole}</p>
              </div>
              <button
                onClick={handleLogout}
                title="Sign out"
                className="p-2 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-rose-500/10 transition-colors"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          )}
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 min-w-0 overflow-y-auto">
        <Outlet />
      </main>
    </div>
  );
};
