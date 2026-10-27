import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity,
  AlertCircle,
  BarChart3,
  CheckCircle2,
  Cpu,
  PlayCircle,
  TrendingUp,
} from 'lucide-react';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
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
import { useTheme } from '../features/theme/ThemeContext';
import type { DashboardData } from '../types/api';

export const SkillAnalyticsPage: React.FC = () => {
  const { chartColors } = useTheme();
  const [data, setData] = useState<DashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setLoading(true);
    api.users
      .getProgress()
      .then((res) => {
        setData(res);
        setError(null);
      })
      .catch((err) => {
        setError(err.response?.data?.message || 'Failed to load skill analytics.');
      })
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="p-8 space-y-6 max-w-7xl mx-auto">
        <div className="h-8 w-64 bg-slate-800 rounded animate-pulse" />
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
          <AlertCircle className="w-5 h-5" />
          <span>{error || 'Analytics unavailable.'}</span>
        </div>
      </div>
    );
  }

  const radarData = data.skillProfile.map((s) => ({
    subject: s.label,
    score: s.sufficientEvidence ? s.score : 0,
    confidence: Math.round(s.confidence * 100),
  }));

  return (
    <div className="p-6 lg:p-8 max-w-7xl mx-auto space-y-8">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-white flex items-center gap-2.5">
            <BarChart3 className="w-6 h-6 text-blue-400" />
            <span>Multi-Dimensional Skill Analytics</span>
          </h1>
          <p className="text-xs text-slate-400 mt-1">
            Longitudinal analysis of your 5 core technical competencies, DSA topic accuracy, and execution efficiency.
          </p>
        </div>

        <Link
          to="/interviews/new"
          className="px-4 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold flex items-center gap-2 self-start"
        >
          <PlayCircle className="w-4 h-4" />
          <span>New Adaptive Assessment</span>
        </Link>
      </div>

      {!data.hasAssessmentData ? (
        <div className="p-10 rounded-xl bg-[#0d1321] border border-slate-800 text-center space-y-4">
          <Activity className="w-10 h-10 text-blue-400 mx-auto" />
          <h2 className="text-lg font-semibold text-white">No Assessment Telemetry Recorded Yet</h2>
          <p className="text-xs text-slate-400 max-w-lg mx-auto leading-relaxed">
            Your analytics charts populate from real persisted interview attempts and rubric assessments in MySQL. Complete your first interview session to unlock longitudinal skill tracking.
          </p>
          <Link
            to="/interviews/new"
            className="inline-flex items-center gap-2 px-5 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold"
          >
            <PlayCircle className="w-4 h-4" />
            <span>Start First Interview</span>
          </Link>
        </div>
      ) : (
        <>
          {/* Competency Matrix Table */}
          <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
            <h2 className="text-base font-semibold text-white">
              Competency Vector S = [Pattern, Implementation, Complexity, Debugging, Communication]
            </h2>

            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="border-b border-slate-800 text-slate-400 font-mono text-[11px]">
                    <th className="py-3 pr-4">COMPETENCY</th>
                    <th className="py-3 px-4">SCORE (0–100)</th>
                    <th className="py-3 px-4">CONFIDENCE</th>
                    <th className="py-3 px-4">ASSESSMENTS</th>
                    <th className="py-3 pl-4">STATUS</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800/70">
                  {data.skillProfile.map((s) => (
                    <tr key={s.competency}>
                      <td className="py-3.5 pr-4 font-semibold text-white">{s.label}</td>
                      <td className="py-3.5 px-4 font-mono text-blue-400 font-bold">
                        {s.sufficientEvidence ? s.score.toFixed(1) : '—'}
                      </td>
                      <td className="py-3.5 px-4 font-mono text-slate-300">
                        {Math.round(s.confidence * 100)}%
                      </td>
                      <td className="py-3.5 px-4 font-mono text-slate-300">{s.assessmentsCount}</td>
                      <td className="py-3.5 pl-4">
                        {!s.sufficientEvidence ? (
                          <span className="px-2 py-0.5 rounded bg-slate-800 text-slate-400 font-mono text-[10px]">
                            INSUFFICIENT EVIDENCE
                          </span>
                        ) : s.score >= 75 ? (
                          <span className="px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 font-mono text-[10px]">
                            STRONG PROFICIENCY
                          </span>
                        ) : s.score >= 55 ? (
                          <span className="px-2 py-0.5 rounded bg-blue-500/15 text-blue-300 border border-blue-500/30 font-mono text-[10px]">
                            DEVELOPING
                          </span>
                        ) : (
                          <span className="px-2 py-0.5 rounded bg-amber-500/15 text-amber-300 border border-amber-500/30 font-mono text-[10px]">
                            NEEDS REINFORCEMENT
                          </span>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Radar & Longitudinal Competency Trajectory */}
          <div className="grid lg:grid-cols-2 gap-6">
            <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
              <div>
                <h2 className="text-base font-semibold text-white">Score vs. Confidence Radar</h2>
                <p className="text-xs text-slate-400">Overlay of proficiency score and evidence confidence</p>
              </div>
              <div className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <RadarChart cx="50%" cy="50%" outerRadius="72%" data={radarData}>
                    <PolarGrid stroke={chartColors.grid} />
                    <PolarAngleAxis dataKey="subject" tick={{ fill: chartColors.axisText, fontSize: 11 }} />
                    <PolarRadiusAxis angle={30} domain={[0, 100]} tick={{ fill: chartColors.axisText, fontSize: 10 }} />
                    <Radar name="Score" dataKey="score" stroke="#3b82f6" fill="#3b82f6" fillOpacity={0.35} />
                    <Radar name="Confidence %" dataKey="confidence" stroke="#14b8a6" fill="#14b8a6" fillOpacity={0.15} />
                    <Legend wrapperStyle={{ fontSize: '12px' }} />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: chartColors.tooltipBg,
                        borderColor: chartColors.tooltipBorder,
                        color: chartColors.tooltipText,
                        fontSize: '12px',
                      }}
                    />
                  </RadarChart>
                </ResponsiveContainer>
              </div>
            </div>

            <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
              <div>
                <h2 className="text-base font-semibold text-white flex items-center gap-2">
                  <TrendingUp className="w-4 h-4 text-emerald-400" />
                  <span>Correctness & Compilation Efficiency Trends</span>
                </h2>
                <p className="text-xs text-slate-400">
                  Session-over-session test pass rate, compilation success rate, and overall score
                </p>
              </div>
              <div className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={data.correctnessAndEfficiencyTrends}>
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
                    <Legend wrapperStyle={{ fontSize: '12px' }} />
                    <Line type="monotone" dataKey="overallScore" name="Overall Score" stroke="#3b82f6" strokeWidth={2} />
                    <Line type="monotone" dataKey="testPassRatePercent" name="Test Pass %" stroke="#10b981" strokeWidth={2} />
                    <Line type="monotone" dataKey="compilationSuccessPercent" name="Compile Success %" stroke="#8b5cf6" strokeWidth={2} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </div>
          </div>

          {/* Topic Breakdown Table & Bar Chart */}
          <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
            <div>
              <h2 className="text-base font-semibold text-white flex items-center gap-2">
                <Cpu className="w-4 h-4 text-teal-400" />
                <span>DSA Topic Mastery & Execution Telemetry</span>
              </h2>
              <p className="text-xs text-slate-400">
                Aggregated pass rate and runtime performance across attempted DSA topics
              </p>
            </div>

            {data.topicPerformance.length === 0 ? (
              <p className="text-xs text-slate-500">No topic submissions recorded yet.</p>
            ) : (
              <div className="grid lg:grid-cols-12 gap-6 items-center">
                <div className="lg:col-span-6 h-64">
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
                      <Bar dataKey="accuracyPercent" name="Accuracy (%)" fill="#14b8a6" radius={[4, 4, 0, 0]} />
                    </BarChart>
                  </ResponsiveContainer>
                </div>

                <div className="lg:col-span-6 space-y-2.5">
                  {data.topicPerformance.map((tp) => (
                    <div
                      key={tp.topic}
                      className="p-3 rounded-lg bg-[#090d16] border border-slate-800 flex items-center justify-between text-xs"
                    >
                      <div>
                        <span className="font-semibold text-white">{tp.topic}</span>
                        <span className="text-slate-400 ml-2 font-mono text-[11px]">
                          ({tp.questionsAttempted} Qs · {tp.attemptsCount} runs)
                        </span>
                      </div>
                      <div className="flex items-center gap-4 font-mono">
                        <span className="text-slate-400 text-[11px]">Avg {tp.avgRuntimeMs}ms</span>
                        <span className="text-teal-400 font-bold">{tp.accuracyPercent.toFixed(1)}%</span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
};
