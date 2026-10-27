import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  Activity,
  AlertCircle,
  ArrowRight,
  Award,
  BarChart2,
  BookOpen,
  CheckCircle2,
  Clock,
  PlayCircle,
  Sparkles,
  TrendingUp,
} from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Line,
  LineChart,
  PolarAngleAxis,
  PolarGrid,
  PolarRadiusAxis,
  Radar,
  RadarChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { api } from '../services/api';
import { useAuth } from '../features/auth/AuthContext';
import { useTheme } from '../features/theme/ThemeContext';
import type { DashboardData } from '../types/api';

export const DashboardPage: React.FC = () => {
  const { user } = useAuth();
  const { chartColors } = useTheme();
  const navigate = useNavigate();
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    api.dashboard
      .get()
      .then((res) => {
        setData(res);
        setError(null);
      })
      .catch((err) => {
        setError(err.response?.data?.message || 'Failed to load dashboard data from MySQL.');
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="p-8 space-y-6">
        <div className="h-8 w-64 bg-slate-800/70 rounded animate-pulse" />
        <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-28 bg-[#0d1321] border border-slate-800 rounded-xl animate-pulse" />
          ))}
        </div>
        <div className="grid lg:grid-cols-2 gap-6">
          <div className="h-80 bg-[#0d1321] border border-slate-800 rounded-xl animate-pulse" />
          <div className="h-80 bg-[#0d1321] border border-slate-800 rounded-xl animate-pulse" />
        </div>
      </div>
    );
  }

  if (error || !data) {
    return (
      <div className="p-8">
        <div className="p-6 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 flex items-center gap-3">
          <AlertCircle className="w-5 h-5 shrink-0" />
          <div className="text-sm">{error || 'Unable to load dashboard.'}</div>
        </div>
      </div>
    );
  }

  const radarData = data.skillProfile.map((s) => ({
    subject: s.label,
    score: s.sufficientEvidence ? s.score : 0,
    fullMark: 100,
  }));

  return (
    <div className="p-6 lg:p-8 max-w-7xl mx-auto space-y-8">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white">
            Welcome back, {user?.displayName || 'Candidate'}
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Target Role: <span className="text-slate-200 font-medium">{user?.targetRole}</span> · Level:{' '}
            <span className="text-slate-200 font-medium">{user?.experienceLevel}</span> · Language:{' '}
            <span className="font-mono text-blue-400">Java 21</span>
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link
            to="/interviews/new"
            className="px-4 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium transition-colors flex items-center gap-2 shadow-lg shadow-blue-950/50"
          >
            <PlayCircle className="w-4 h-4" />
            <span>Start Adaptive Interview</span>
          </Link>
        </div>
      </div>

      {/* Empty State Banner for Brand-New Users */}
      {!data.hasAssessmentData && (
        <div className="p-6 rounded-xl bg-blue-600/10 border border-blue-500/30 flex flex-col md:flex-row items-start md:items-center justify-between gap-6">
          <div className="space-y-2 max-w-2xl">
            <div className="inline-flex items-center gap-1.5 text-xs font-mono text-blue-400">
              <Sparkles className="w-3.5 h-3.5" />
              <span>INITIAL SKILL PROFILE — NO ASSESSMENTS RECORDED YET</span>
            </div>
            <h2 className="text-lg font-semibold text-white">
              Complete your first adaptive interview to calibrate your 5-competency skill vector
            </h2>
            <p className="text-xs text-slate-300 leading-relaxed">
              We never fabricate or hardcode proficiency scores. Start a 15- or 30-minute Java session to collect deterministic test execution telemetry and rubric evaluations across Pattern Recognition, Implementation, Complexity Analysis, Debugging, and Communication.
            </p>
          </div>
          <Link
            to="/interviews/new"
            className="px-5 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold whitespace-nowrap flex items-center gap-2"
          >
            <span>Configure First Session</span>
            <ArrowRight className="w-4 h-4" />
          </Link>
        </div>
      )}

      {/* 4 Summary Cards */}
      <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-5 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-2">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Interviews Completed</span>
            <CheckCircle2 className="w-4 h-4 text-blue-400" />
          </div>
          <div className="text-2xl font-bold text-white font-mono">
            {data.totalInterviewsCompleted}
          </div>
          <p className="text-[11px] text-slate-400">
            {data.activeInterviewsCount > 0
              ? `${data.activeInterviewsCount} active session(s) in progress`
              : 'All persisted in MySQL 8'}
          </p>
        </div>

        <div className="p-5 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-2">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Average Interview Score</span>
            <Award className="w-4 h-4 text-teal-400" />
          </div>
          <div className="text-2xl font-bold text-white font-mono">
            {data.averageInterviewScore !== null ? `${data.averageInterviewScore.toFixed(1)}%` : 'Unassessed'}
          </div>
          <p className="text-[11px] text-slate-400">
            {data.averageInterviewScore !== null
              ? 'Across completed mock interviews'
              : 'Complete an interview to compute'}
          </p>
        </div>

        <div className="p-5 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-2">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Overall Proficiency</span>
            <Activity className="w-4 h-4 text-violet-400" />
          </div>
          <div className="text-2xl font-bold text-white font-mono">
            {data.currentOverallProficiency !== null
              ? `${data.currentOverallProficiency.toFixed(1)} / 100`
              : 'Insufficient Evidence'}
          </div>
          <p className="text-[11px] text-slate-400">
            Mean across assessed competencies
          </p>
        </div>

        <div className="p-5 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-2">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Change Since Previous</span>
            <TrendingUp className="w-4 h-4 text-emerald-400" />
          </div>
          <div
            className={`text-2xl font-bold font-mono ${
              data.improvementSincePrevious === null
                ? 'text-slate-300'
                : data.improvementSincePrevious >= 0
                ? 'text-emerald-400'
                : 'text-rose-400'
            }`}
          >
            {data.improvementSincePrevious !== null
              ? `${data.improvementSincePrevious >= 0 ? '+' : ''}${data.improvementSincePrevious.toFixed(1)} pts`
              : 'N/A'}
          </div>
          <p className="text-[11px] text-slate-400">
            {data.improvementSincePrevious !== null
              ? 'Compared with prior completed session'
              : 'Requires at least 2 completed sessions'}
          </p>
        </div>
      </div>

      {/* Skill Profile Radar & Competency Cards */}
      <div className="grid lg:grid-cols-12 gap-6">
        <div className="lg:col-span-5 p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 flex flex-col justify-between">
          <div>
            <h2 className="text-base font-semibold text-white">5-Competency Skill Radar</h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Live vector S = [Pattern, Implementation, Complexity, Debugging, Communication]
            </p>
          </div>

          <div className="h-64 w-full mt-4">
            <ResponsiveContainer width="100%" height="100%">
              <RadarChart cx="50%" cy="50%" outerRadius="72%" data={radarData}>
                <PolarGrid stroke={chartColors.grid} />
                <PolarAngleAxis dataKey="subject" tick={{ fill: chartColors.axisText, fontSize: 11 }} />
                <PolarRadiusAxis angle={30} domain={[0, 100]} tick={{ fill: chartColors.axisText, fontSize: 10 }} />
                <Radar
                  name="Proficiency"
                  dataKey="score"
                  stroke="#3b82f6"
                  fill="#3b82f6"
                  fillOpacity={0.35}
                />
                <Tooltip
                  contentStyle={{
                    backgroundColor: chartColors.tooltipBg,
                    borderColor: chartColors.tooltipBorder,
                    fontSize: '12px',
                    color: chartColors.tooltipText,
                  }}
                />
              </RadarChart>
            </ResponsiveContainer>
          </div>

          {data.weakestCompetencies.length > 0 && (
            <div className="p-3 rounded-lg bg-[#090d16] border border-slate-800/80 text-xs flex items-center justify-between">
              <span className="text-slate-400">Priority Target Areas:</span>
              <span className="font-medium text-amber-300">
                {data.weakestCompetencies.map((w) => w.label).join(' · ')}
              </span>
            </div>
          )}
        </div>

        <div className="lg:col-span-7 p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold text-white">Competency Vector & Confidence</h2>
              <p className="text-xs text-slate-400 mt-0.5">
                Updated via weighted evidence formula s_new = α·r + (1-α)·s
              </p>
            </div>
            <Link to="/analytics" className="text-xs text-blue-400 hover:text-blue-300 font-medium">
              Full Analytics →
            </Link>
          </div>

          <div className="space-y-3 pt-1">
            {data.skillProfile.map((skill) => (
              <div
                key={skill.competency}
                className="p-3.5 rounded-lg bg-[#090d16] border border-slate-800/80 space-y-2"
              >
                <div className="flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2">
                    <span className="font-medium text-slate-200">{skill.label}</span>
                    <span className="text-[11px] font-mono text-slate-400">
                      ({skill.assessmentsCount} assessment{skill.assessmentsCount === 1 ? '' : 's'})
                    </span>
                  </div>
                  {skill.sufficientEvidence ? (
                    <div className="flex items-center gap-3 font-mono">
                      <span className="text-slate-400 text-[11px]">
                        Confidence: {Math.round(skill.confidence * 100)}%
                      </span>
                      <span className="text-sm font-bold text-blue-400">{skill.score.toFixed(1)}</span>
                    </div>
                  ) : (
                    <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-mono text-[10px]">
                      INSUFFICIENT EVIDENCE
                    </span>
                  )}
                </div>

                <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all ${
                      !skill.sufficientEvidence
                        ? 'bg-slate-700 w-0'
                        : skill.score >= 75
                        ? 'bg-emerald-500'
                        : skill.score >= 55
                        ? 'bg-blue-500'
                        : 'bg-amber-500'
                    }`}
                    style={{ width: `${skill.sufficientEvidence ? Math.max(4, skill.score) : 0}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Progression & Topic Performance Charts */}
      {data.hasAssessmentData && (
        <div className="grid lg:grid-cols-2 gap-6">
          <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
            <div>
              <h2 className="text-base font-semibold text-white">Skill Progression Over Time</h2>
              <p className="text-xs text-slate-400">Historical trajectory across completed assessments</p>
            </div>
            <div className="h-64">
              {data.skillProgression.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={data.skillProgression}>
                    <CartesianGrid strokeDasharray="3 3" stroke={chartColors.grid} />
                    <XAxis dataKey="sessionLabel" stroke={chartColors.axisText} fontSize={11} />
                    <YAxis domain={[0, 100]} stroke={chartColors.axisText} fontSize={11} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: chartColors.tooltipBg,
                        borderColor: chartColors.tooltipBorder,
                        color: chartColors.tooltipText,
                        fontSize: '12px',
                      }}
                    />
                    <Line type="monotone" dataKey="patternRecognition" name="Pattern" stroke="#3b82f6" strokeWidth={2} />
                    <Line type="monotone" dataKey="implementation" name="Implementation" stroke="#14b8a6" strokeWidth={2} />
                    <Line type="monotone" dataKey="complexityAnalysis" name="Complexity" stroke="#8b5cf6" strokeWidth={2} />
                    <Line type="monotone" dataKey="debugging" name="Debugging" stroke="#f59e0b" strokeWidth={2} />
                    <Line type="monotone" dataKey="communication" name="Communication" stroke="#10b981" strokeWidth={2} />
                  </LineChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex items-center justify-center text-xs text-slate-500">
                  Complete questions to populate longitudinal progression points.
                </div>
              )}
            </div>
          </div>

          <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
            <div>
              <h2 className="text-base font-semibold text-white">Performance by DSA Topic</h2>
              <p className="text-xs text-slate-400">Test case accuracy percentage per topic</p>
            </div>
            <div className="h-64">
              {data.topicPerformance.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={data.topicPerformance}>
                    <CartesianGrid strokeDasharray="3 3" stroke={chartColors.grid} />
                    <XAxis dataKey="topic" stroke={chartColors.axisText} fontSize={11} />
                    <YAxis domain={[0, 100]} stroke={chartColors.axisText} fontSize={11} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: chartColors.tooltipBg,
                        borderColor: chartColors.tooltipBorder,
                        color: chartColors.tooltipText,
                        fontSize: '12px',
                      }}
                    />
                    <Bar dataKey="accuracyPercent" name="Test Pass Rate (%)" fill="#3b82f6" radius={[4, 4, 0, 0]} />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-full flex items-center justify-center text-xs text-slate-500">
                  No code submissions recorded yet.
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Recommended Next Practice & Recent Activity */}
      <div className="grid lg:grid-cols-12 gap-6">
        <div className="lg:col-span-6 p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold text-white flex items-center gap-2">
                <BookOpen className="w-4 h-4 text-teal-400" />
                <span>Recommended Next Practice</span>
              </h2>
              <p className="text-xs text-slate-400">Tailored to your weakest competencies</p>
            </div>
            <Link to="/recommendations" className="text-xs text-blue-400 hover:text-blue-300 font-medium">
              View All →
            </Link>
          </div>

          <div className="space-y-3">
            {data.recommendedPractice.slice(0, 3).map((rec) => (
              <div
                key={rec.id}
                className="p-4 rounded-lg bg-[#090d16] border border-slate-800/90 space-y-2 flex flex-col justify-between"
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <span className="text-xs font-semibold text-white">{rec.questionTitle}</span>
                    <div className="flex items-center gap-2 mt-1 text-[11px] font-mono">
                      <span className="text-blue-400">{rec.topic}</span>
                      <span className="text-slate-500">·</span>
                      <span className="text-amber-300">{rec.suggestedDifficulty}</span>
                      <span className="text-slate-500">·</span>
                      <span className="text-violet-300">Target: {rec.targetCompetencyLabel}</span>
                    </div>
                  </div>
                  <button
                    onClick={() =>
                      navigate(
                        `/interviews/new?topic=${encodeURIComponent(rec.topic)}&difficulty=${rec.suggestedDifficulty}&type=${rec.questionType}`
                      )
                    }
                    className="px-2.5 py-1 rounded bg-blue-600/20 hover:bg-blue-600/30 text-blue-300 border border-blue-500/30 text-xs font-medium shrink-0"
                  >
                    Practice
                  </button>
                </div>
                <p className="text-xs text-slate-400 leading-relaxed">{rec.reason}</p>
              </div>
            ))}
          </div>
        </div>

        <div className="lg:col-span-6 p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-base font-semibold text-white flex items-center gap-2">
                <Clock className="w-4 h-4 text-blue-400" />
                <span>Recent Interview Sessions</span>
              </h2>
              <p className="text-xs text-slate-400">Your latest interview sessions and reports</p>
            </div>
            <Link to="/history" className="text-xs text-blue-400 hover:text-blue-300 font-medium">
              Full History →
            </Link>
          </div>

          {data.recentActivity.length === 0 ? (
            <div className="p-8 rounded-lg bg-[#090d16] border border-slate-800/80 text-center space-y-3">
              <BarChart2 className="w-8 h-8 text-slate-600 mx-auto" />
              <p className="text-xs text-slate-400">No interview sessions recorded yet.</p>
              <Link
                to="/interviews/new"
                className="inline-flex items-center gap-1.5 text-xs font-medium text-blue-400 hover:text-blue-300"
              >
                <span>Start your first mock interview</span>
                <ArrowRight className="w-3.5 h-3.5" />
              </Link>
            </div>
          ) : (
            <div className="space-y-2.5">
              {data.recentActivity.slice(0, 5).map((s) => (
                <div
                  key={s.id}
                  className="p-3.5 rounded-lg bg-[#090d16] border border-slate-800/90 flex items-center justify-between gap-4"
                >
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-semibold text-white">
                        {s.interviewType} Interview #{s.id}
                      </span>
                      <span
                        className={`px-1.5 py-0.5 text-[10px] font-mono rounded ${
                          s.status === 'COMPLETED'
                            ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                            : 'bg-amber-500/15 text-amber-300 border border-amber-500/30'
                        }`}
                      >
                        {s.status}
                      </span>
                    </div>
                    <p className="text-[11px] text-slate-400 truncate mt-1">
                      {s.configuredDifficulty} · {s.topics.join(', ')} ·{' '}
                      {new Date(s.createdAt).toLocaleDateString()}
                    </p>
                  </div>

                  <div className="flex items-center gap-3 shrink-0">
                    {s.overallScore !== null && (
                      <span className="text-sm font-mono font-bold text-blue-400">
                        {s.overallScore.toFixed(1)}%
                      </span>
                    )}
                    <Link
                      to={
                        s.status === 'COMPLETED'
                          ? `/interviews/${s.id}/report`
                          : `/interviews/${s.id}/workspace`
                      }
                      className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium transition-colors"
                    >
                      {s.status === 'COMPLETED' ? 'Report' : 'Resume'}
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
