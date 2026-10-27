import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  BookOpen,
  CheckCircle2,
  Compass,
  Play,
  RefreshCw,
  Sparkles,
  Target,
} from 'lucide-react';
import { api } from '../services/api';
import type { Recommendation, SkillMetric } from '../types/api';

export const RecommendationsPage: React.FC = () => {
  const navigate = useNavigate();
  const [recommendations, setRecommendations] = useState<Recommendation[]>([]);
  const [skills, setSkills] = useState<SkillMetric[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [startingId, setStartingId] = useState<number | null>(null);
  const [competencyFilter, setCompetencyFilter] = useState<string>('ALL');
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [recs, skillList] = await Promise.all([
        api.users.getRecommendations(),
        api.users.getSkills(),
      ]);
      setRecommendations(recs);
      setSkills(skillList);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load practice recommendations.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleRefresh = async () => {
    setRefreshing(true);
    setError(null);
    try {
      const updated = await api.users.refreshRecommendations();
      setRecommendations(updated);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Unable to regenerate recommendations.');
    } finally {
      setRefreshing(false);
    }
  };

  const handleStatusChange = async (id: number, status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED') => {
    try {
      const updated = await api.users.updateRecommendationStatus(id, status);
      setRecommendations((prev) => prev.map((r) => (r.id === id ? updated : r)));
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to update recommendation status.');
    }
  };

  const handlePracticeNow = async (rec: Recommendation) => {
    setStartingId(rec.id);
    setError(null);
    try {
      await api.users.updateRecommendationStatus(rec.id, 'IN_PROGRESS');
      const session = await api.interviews.create({
        interviewType: rec.questionType === 'DEBUGGING' ? 'DEBUGGING' : 'DSA',
        difficulty: rec.suggestedDifficulty || 'MEDIUM',
        topics: [rec.topic],
        durationMinutes: 15,
        questionCount: 1,
        interviewStyle: 'INTERVIEWER_LED',
      });
      await api.interviews.start(session.id);
      navigate(`/interviews/${session.id}/workspace`);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Could not launch targeted practice interview.');
      setStartingId(null);
    }
  };

  const filtered = recommendations.filter((rec) => {
    if (competencyFilter !== 'ALL' && rec.targetCompetency !== competencyFilter) return false;
    if (statusFilter !== 'ALL' && rec.status !== statusFilter) return false;
    return true;
  });

  if (loading) {
    return (
      <div className="space-y-4">
        <div className="h-24 rounded-xl bg-slate-900 border border-slate-800 animate-pulse" />
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {[1, 2, 3, 4].map((n) => (
            <div key={n} className="h-52 rounded-xl bg-slate-900 border border-slate-800 animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">Personalized Practice Recommendations</h1>
          <p className="text-sm text-slate-400 mt-1">
            Targeted exercises generated from your weakest competencies, diagnosed misconceptions, and topic gaps.
          </p>
        </div>
        <button
          onClick={handleRefresh}
          disabled={refreshing}
          className="inline-flex items-center gap-2 rounded-lg bg-brand-600 hover:bg-brand-500 disabled:opacity-50 px-4 py-2 text-xs font-semibold text-white transition-colors"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${refreshing ? 'animate-spin' : ''}`} />
          {refreshing ? 'Recomputing Queue...' : 'Regenerate Recommendations'}
        </button>
      </div>

      {error && (
        <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 p-4 text-sm text-rose-200">
          {error}
        </div>
      )}

      {/* Competency snapshot bar */}
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
        {skills.map((skill) => (
          <button
            key={skill.competency}
            onClick={() =>
              setCompetencyFilter((prev) => (prev === skill.competency ? 'ALL' : skill.competency))
            }
            className={`rounded-xl border p-3 text-left transition-all ${
              competencyFilter === skill.competency
                ? 'border-brand-500 bg-brand-500/10'
                : 'border-slate-800 bg-slate-900/60 hover:border-slate-700'
            }`}
          >
            <div className="text-[11px] font-medium text-slate-400 truncate">{skill.label}</div>
            <div className="mt-1 flex items-baseline justify-between">
              <span className="text-base font-bold font-mono text-slate-100">
                {skill.assessmentsCount > 0 ? `${skill.score.toFixed(1)}` : '—'}
              </span>
              <span className="text-[10px] text-slate-500">
                {skill.assessmentsCount} eval{skill.assessmentsCount === 1 ? '' : 's'}
              </span>
            </div>
          </button>
        ))}
      </div>

      {/* Filters */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-800 bg-slate-900/50 p-3.5">
        <div className="flex items-center gap-2 text-xs text-slate-300">
          <Target className="h-4 w-4 text-brand-400" />
          <span>
            Showing <strong>{filtered.length}</strong> of {recommendations.length} recommended practice items
          </span>
          {competencyFilter !== 'ALL' && (
            <button
              onClick={() => setCompetencyFilter('ALL')}
              className="text-brand-400 hover:underline ml-2"
            >
              Clear competency filter
            </button>
          )}
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-slate-400">Status:</span>
          <select
            aria-label="Filter recommendations by status"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-1.5 text-xs text-slate-200"
          >
            <option value="ALL">All</option>
            <option value="PENDING">Pending</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="COMPLETED">Completed</option>
          </select>
        </div>
      </div>

      {/* Recommendation cards */}
      {filtered.length === 0 ? (
        <div className="rounded-2xl border border-slate-800 bg-slate-900/40 p-10 text-center">
          <Compass className="h-10 w-10 text-slate-600 mx-auto mb-3" />
          <h2 className="text-base font-semibold text-slate-200">No Matching Recommendations</h2>
          <p className="text-sm text-slate-400 max-w-md mx-auto mt-1">
            Click &ldquo;Regenerate Recommendations&rdquo; above or complete an interview session to populate personalized practice items.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {filtered.map((rec) => (
            <div
              key={rec.id}
              className="rounded-xl border border-slate-800 bg-slate-900/60 p-5 flex flex-col justify-between gap-4 hover:border-slate-700/80 transition-colors"
            >
              <div className="space-y-3">
                <div className="flex items-start justify-between gap-3">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="inline-flex items-center gap-1 rounded-md bg-brand-500/15 border border-brand-500/30 px-2 py-0.5 text-[11px] font-semibold text-brand-300">
                        <Sparkles className="h-3 w-3" /> {rec.targetCompetencyLabel}
                      </span>
                      <span className="rounded bg-slate-800 px-2 py-0.5 text-[11px] text-slate-300">
                        {rec.topic}
                      </span>
                      <span
                        className={`rounded px-2 py-0.5 text-[11px] font-medium ${
                          rec.suggestedDifficulty === 'HARD'
                            ? 'bg-rose-500/15 text-rose-300'
                            : rec.suggestedDifficulty === 'MEDIUM'
                            ? 'bg-amber-500/15 text-amber-300'
                            : 'bg-emerald-500/15 text-emerald-300'
                        }`}
                      >
                        {rec.suggestedDifficulty}
                      </span>
                    </div>
                    <h3 className="text-base font-bold text-slate-100 mt-2">{rec.questionTitle}</h3>
                  </div>

                  <select
                    aria-label={`Status for ${rec.questionTitle}`}
                    value={rec.status}
                    onChange={(e) =>
                      handleStatusChange(
                        rec.id,
                        e.target.value as 'PENDING' | 'IN_PROGRESS' | 'COMPLETED'
                      )
                    }
                    className={`rounded-lg border px-2.5 py-1 text-[11px] font-medium ${
                      rec.status === 'COMPLETED'
                        ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-300'
                        : rec.status === 'IN_PROGRESS'
                        ? 'border-brand-500/40 bg-brand-500/10 text-brand-300'
                        : 'border-slate-700 bg-slate-950 text-slate-300'
                    }`}
                  >
                    <option value="PENDING">Pending</option>
                    <option value="IN_PROGRESS">In Progress</option>
                    <option value="COMPLETED">Completed</option>
                  </select>
                </div>

                <div className="rounded-lg border border-slate-800/80 bg-slate-950/60 p-3 space-y-1.5">
                  <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-400">
                    Why This Was Selected
                  </div>
                  <p className="text-xs text-slate-300 leading-relaxed">{rec.reason}</p>
                </div>

                <div className="flex items-start gap-2 text-xs text-slate-400">
                  <BookOpen className="h-4 w-4 text-teal-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-semibold text-slate-300">Learning Objective: </span>
                    {rec.learningObjective}
                  </div>
                </div>
              </div>

              <div className="pt-3 border-t border-slate-800/80 flex items-center justify-between gap-3">
                <span className="text-[11px] text-slate-500">
                  Type: <strong className="text-slate-300">{rec.questionType}</strong>
                </span>
                <div className="flex items-center gap-2">
                  {rec.status !== 'COMPLETED' && (
                    <button
                      onClick={() => handleStatusChange(rec.id, 'COMPLETED')}
                      className="inline-flex items-center gap-1 rounded-lg border border-slate-700 bg-slate-950 hover:bg-slate-800 px-3 py-1.5 text-xs text-slate-300 transition-colors"
                    >
                      <CheckCircle2 className="h-3.5 w-3.5 text-emerald-400" /> Mark Done
                    </button>
                  )}
                  <button
                    onClick={() => handlePracticeNow(rec)}
                    disabled={startingId === rec.id}
                    className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 hover:bg-brand-500 disabled:opacity-50 px-3.5 py-1.5 text-xs font-semibold text-white transition-colors"
                  >
                    <Play className="h-3.5 w-3.5" />
                    {startingId === rec.id ? 'Launching...' : 'Practice Now'}
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
