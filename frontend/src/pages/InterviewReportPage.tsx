import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  Activity,
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  Award,
  BookOpen,
  CheckCircle2,
  Code2,
  Cpu,
  FileSearch,
  Lightbulb,
  PlayCircle,
  Sparkles,
  TrendingUp,
} from 'lucide-react';
import { api } from '../services/api';
import type { InterviewReport } from '../types/api';

export const InterviewReportPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const sessionId = Number(id);
  const navigate = useNavigate();

  const [report, setReport] = useState<InterviewReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (Number.isNaN(sessionId)) return;
    setLoading(true);
    api.interviews
      .getReport(sessionId)
      .then((res) => {
        setReport(res);
        setError(null);
      })
      .catch((err) => {
        setError(err.response?.data?.message || 'Failed to load interview report.');
      })
      .finally(() => setLoading(false));
  }, [sessionId]);

  if (loading) {
    return (
      <div className="p-8 space-y-6 max-w-6xl mx-auto">
        <div className="h-10 w-80 bg-slate-800 rounded animate-pulse" />
        <div className="h-44 bg-[#0d1321] border border-slate-800 rounded-xl animate-pulse" />
        <div className="h-96 bg-[#0d1321] border border-slate-800 rounded-xl animate-pulse" />
      </div>
    );
  }

  if (error || !report) {
    return (
      <div className="p-8 max-w-4xl mx-auto">
        <div className="p-6 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 flex items-center gap-3">
          <AlertCircle className="w-5 h-5 shrink-0" />
          <div className="text-sm">{error || 'Report unavailable.'}</div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-6 lg:p-8 max-w-7xl mx-auto space-y-8">
      {/* Top Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div className="space-y-1">
          <Link
            to="/dashboard"
            className="inline-flex items-center gap-1.5 text-xs text-slate-400 hover:text-white"
          >
            <ArrowLeft className="w-3.5 h-3.5" />
            <span>Back to Dashboard</span>
          </Link>
          <h1 className="text-2xl font-bold text-white flex items-center gap-3">
            <span>Interview Evaluation Report #{report.sessionId}</span>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-mono bg-emerald-500/15 text-emerald-300 border border-emerald-500/30">
              {report.status}
            </span>
          </h1>
          <p className="text-xs text-slate-400">
            Type: <span className="text-slate-200">{report.interviewType}</span> · Configured Difficulty:{' '}
            <span className="text-slate-200">{report.configuredDifficulty}</span> · Style:{' '}
            <span className="text-slate-200">{report.interviewStyle}</span> · Completed:{' '}
            {report.completedAt ? new Date(report.completedAt).toLocaleString() : 'N/A'}
          </p>
        </div>

        <div className="flex items-center gap-3">
          <Link
            to="/interviews/new"
            className="px-4 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-semibold flex items-center gap-2"
          >
            <PlayCircle className="w-4 h-4" />
            <span>Start Follow-Up Session</span>
          </Link>
        </div>
      </div>

      {/* Overall Score, Previous Comparison & Strongest/Weakest Summary */}
      <div className="grid sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="p-5 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-2">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Overall Session Score</span>
            <Award className="w-4 h-4 text-blue-400" />
          </div>
          <div className="text-3xl font-bold font-mono text-white">
            {report.overallScore.toFixed(1)}%
          </div>
          <div className="text-[11px] font-mono text-slate-400">
            Evaluation Mode: {report.aiModeUsed === 'LIVE_LLM' ? 'Live LLM + Deterministic' : 'Deterministic Rubric'}
          </div>
        </div>

        <div className="p-5 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-2">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Comparison vs. Prior Session</span>
            <TrendingUp className="w-4 h-4 text-emerald-400" />
          </div>
          {report.previousComparison ? (
            <>
              <div
                className={`text-2xl font-bold font-mono ${
                  report.previousComparison.scoreDelta >= 0 ? 'text-emerald-400' : 'text-rose-400'
                }`}
              >
                {report.previousComparison.scoreDelta >= 0 ? '+' : ''}
                {report.previousComparison.scoreDelta.toFixed(1)} pts
              </div>
              <p className="text-[11px] text-slate-400">
                Prior Session #{report.previousComparison.previousSessionId}:{' '}
                {report.previousComparison.previousOverallScore.toFixed(1)}%
              </p>
            </>
          ) : (
            <>
              <div className="text-xl font-bold font-mono text-slate-300">Baseline Session</div>
              <p className="text-[11px] text-slate-400">
                No earlier completed session to compare against
              </p>
            </>
          )}
        </div>

        <div className="p-5 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-2">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Strongest Competencies</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-sm font-semibold text-emerald-300">
            {report.strongestCompetencies.length > 0
              ? report.strongestCompetencies.join(', ')
              : 'Requires higher score threshold'}
          </div>
          <p className="text-[11px] text-slate-400">Demonstrated ≥ 65/100 proficiency</p>
        </div>

        <div className="p-5 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-2">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span>Primary Growth Targets</span>
            <Activity className="w-4 h-4 text-amber-400" />
          </div>
          <div className="text-sm font-semibold text-amber-300">
            {report.weakestCompetencies.length > 0
              ? report.weakestCompetencies.join(', ')
              : 'N/A'}
          </div>
          <p className="text-[11px] text-slate-400">Targeted in recommended exercises below</p>
        </div>
      </div>

      {/* 5 Competency Scores & Confidence Table */}
      <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-white">
            Five-Competency Profile Update & Evidence Source
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            Distinguishes deterministic test/structural signals from AI/rubric explanation evaluations
          </p>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-5 gap-3">
          {report.competencyScores.map((c) => (
            <div
              key={c.competency}
              className="p-4 rounded-lg bg-[#090d16] border border-slate-800/90 flex flex-col justify-between space-y-3"
            >
              <div className="space-y-1">
                <span className="text-xs font-semibold text-white block">{c.label}</span>
                <span className="inline-block px-1.5 py-0.5 rounded bg-slate-800 text-[10px] font-mono text-slate-300">
                  {c.sourceType}
                </span>
              </div>

              {c.sufficientEvidence ? (
                <div className="space-y-1">
                  <div className="flex items-baseline justify-between">
                    <span className="text-2xl font-bold font-mono text-blue-400">
                      {c.score.toFixed(1)}
                    </span>
                    {c.delta !== null && (
                      <span
                        className={`text-xs font-mono font-semibold ${
                          c.delta >= 0 ? 'text-emerald-400' : 'text-rose-400'
                        }`}
                      >
                        {c.delta >= 0 ? '+' : ''}
                        {c.delta.toFixed(1)}
                      </span>
                    )}
                  </div>
                  <div className="text-[11px] font-mono text-slate-400">
                    Confidence: {Math.round(c.confidence * 100)}%
                  </div>
                </div>
              ) : (
                <div className="text-xs font-mono text-slate-500">
                  Insufficient Evidence
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      {/* Questions Attempted & Topic Results */}
      <div className="grid lg:grid-cols-12 gap-6">
        <div className="lg:col-span-8 p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
          <h2 className="text-base font-semibold text-white flex items-center gap-2">
            <Code2 className="w-4 h-4 text-blue-400" />
            <span>Questions Attempted & Execution Telemetry</span>
          </h2>

          <div className="space-y-3">
            {report.questionReports.map((qr) => (
              <div
                key={qr.questionId}
                className="p-4 rounded-lg bg-[#090d16] border border-slate-800 space-y-2.5"
              >
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <span className="text-xs font-mono text-blue-400 mr-2">
                      Q{qr.sequenceNumber}.
                    </span>
                    <span className="text-sm font-semibold text-white">{qr.title}</span>
                    <span className="ml-2 px-2 py-0.5 rounded bg-slate-800 text-[11px] font-mono text-slate-300">
                      {qr.topic} · {qr.difficulty}
                    </span>
                  </div>
                  <div className="text-xs font-mono">
                    <span
                      className={
                        qr.testsPassed === qr.testsTotal && qr.testsTotal > 0
                          ? 'text-emerald-400 font-semibold'
                          : 'text-amber-300 font-semibold'
                      }
                    >
                      {qr.testsPassed}/{qr.testsTotal} Tests Passed
                    </span>
                  </div>
                </div>

                <p className="text-[11px] text-slate-400 italic">
                  Selection Reason: {qr.selectionReason}
                </p>

                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-2 border-t border-slate-800/80 text-[11px] font-mono text-slate-300">
                  <div>Compile: {qr.finalCompilationStatus}</div>
                  <div>Best Runtime: {qr.bestRuntimeMs} ms</div>
                  <div>Hints Used: {qr.hintsUsed}</div>
                  <div>
                    Target Big-O: {qr.expectedTimeComplexity} / {qr.expectedSpaceComplexity}
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="lg:col-span-4 p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
          <h2 className="text-base font-semibold text-white flex items-center gap-2">
            <Cpu className="w-4 h-4 text-teal-400" />
            <span>Topic-Wise Breakdown</span>
          </h2>

          <div className="space-y-3">
            {report.topicResults.map((t) => (
              <div
                key={t.topic}
                className="p-3.5 rounded-lg bg-[#090d16] border border-slate-800 space-y-2"
              >
                <div className="flex items-center justify-between text-xs">
                  <span className="font-semibold text-white">{t.topic}</span>
                  <span className="font-mono text-blue-400 font-bold">
                    {t.averageScore.toFixed(1)}%
                  </span>
                </div>
                <div className="text-[11px] text-slate-400 font-mono">
                  Questions: {t.questionsAttempted} · Tests Passed: {t.totalTestsPassed}/{t.totalTestsRun}
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Mistake Diagnosis Engine Evidence Cards (Observed Facts vs Inferred Hypothesis) */}
      <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-white flex items-center gap-2">
            <FileSearch className="w-4 h-4 text-violet-400" />
            <span>Diagnostic Evidence & Root-Cause Hypotheses</span>
          </h2>
          <p className="text-xs text-slate-400 mt-0.5">
            Explicitly separates observed deterministic telemetry facts from inferred diagnostic causes and interventions
          </p>
        </div>

        <div className="grid md:grid-cols-2 gap-4">
          {report.diagnosedMistakes.map((dm, idx) => (
            <div
              key={`${dm.questionId}-${dm.competency}-${idx}`}
              className="p-4 rounded-xl bg-[#090d16] border border-slate-800/90 space-y-3 flex flex-col justify-between"
            >
              <div className="space-y-2.5">
                <div className="flex items-center justify-between gap-2">
                  <div>
                    <span className="text-xs font-semibold text-white">
                      {dm.competencyLabel}
                    </span>
                    <span className="text-[11px] text-slate-400 ml-2">({dm.questionTitle})</span>
                  </div>
                  <span className="px-2 py-0.5 rounded bg-slate-800 font-mono text-[10px] text-blue-300">
                    {dm.diagnosisCode}
                  </span>
                </div>

                {/* Observed Facts */}
                <div className="p-2.5 rounded-lg bg-[#0d1321] border border-slate-800/80 space-y-1">
                  <span className="text-[10px] font-mono uppercase tracking-wider text-teal-400 block">
                    Observed Facts (Deterministic Telemetry)
                  </span>
                  <ul className="list-disc list-inside text-[11px] text-slate-300 space-y-0.5">
                    {dm.observedFacts.map((fact, fIdx) => (
                      <li key={fIdx}>{fact}</li>
                    ))}
                  </ul>
                </div>

                {/* Inferred Cause */}
                <div className="text-xs text-slate-300 space-y-0.5">
                  <span className="text-[10px] font-mono uppercase tracking-wider text-violet-400 block">
                    Inferred Diagnostic Hypothesis ({dm.sourceType})
                  </span>
                  <p>{dm.inferredHypothesis}</p>
                </div>
              </div>

              {/* Recommended Intervention */}
              <div className="pt-2 border-t border-slate-800/80 flex items-start gap-2 text-xs text-amber-200/90">
                <Lightbulb className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                <span>{dm.recommendedIntervention}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Personalized Practice Recommendations */}
      <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="text-base font-semibold text-white flex items-center gap-2">
              <BookOpen className="w-4 h-4 text-emerald-400" />
              <span>Recommended Follow-Up Practice Exercises</span>
            </h2>
            <p className="text-xs text-slate-400">
              Generated from your updated skill profile and diagnostic evidence
            </p>
          </div>
          <Link to="/recommendations" className="text-xs text-blue-400 hover:text-blue-300 font-medium">
            Open Practice Queue →
          </Link>
        </div>

        <div className="grid md:grid-cols-2 gap-4">
          {report.recommendedPractice.slice(0, 4).map((rec) => (
            <div
              key={rec.id}
              className="p-4 rounded-lg bg-[#090d16] border border-slate-800/90 space-y-2 flex flex-col justify-between"
            >
              <div className="space-y-1.5">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-semibold text-white">{rec.questionTitle}</span>
                  <span className="px-2 py-0.5 rounded bg-blue-500/15 text-blue-300 text-[10px] font-mono">
                    {rec.topic} · {rec.suggestedDifficulty}
                  </span>
                </div>
                <p className="text-xs text-slate-300">{rec.reason}</p>
                <p className="text-[11px] text-teal-300/90">
                  Objective: {rec.learningObjective}
                </p>
              </div>
              <div className="pt-2 flex justify-end">
                <button
                  onClick={() =>
                    navigate(
                      `/interviews/new?topic=${encodeURIComponent(rec.topic)}&difficulty=${rec.suggestedDifficulty}&type=${rec.questionType}`
                    )
                  }
                  className="px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-xs font-medium flex items-center gap-1.5"
                >
                  <span>Practice Exercise</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};
