import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Clock,
  FileText,
  Filter,
  Play,
  PlusCircle,
  RefreshCw,
  Search,
  Trophy,
} from 'lucide-react';
import { api } from '../services/api';
import type { InterviewSessionSummary } from '../types/api';

export const InterviewHistoryPage: React.FC = () => {
  const [sessions, setSessions] = useState<InterviewSessionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [difficultyFilter, setDifficultyFilter] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState<string>('');

  const loadHistory = async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await api.interviews.list();
      setSessions(list);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Unable to load interview history.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadHistory();
  }, []);

  const filteredSessions = sessions.filter((s) => {
    if (statusFilter !== 'ALL' && s.status !== statusFilter) return false;
    if (difficultyFilter !== 'ALL' && s.configuredDifficulty !== difficultyFilter) return false;
    if (searchQuery.trim() !== '') {
      const q = searchQuery.toLowerCase();
      const matchTopics = s.topics.some((t) => t.toLowerCase().includes(q));
      const matchType = s.interviewType.toLowerCase().includes(q);
      const matchId = String(s.id).includes(q);
      if (!matchTopics && !matchType && !matchId) return false;
    }
    return true;
  });

  if (loading) {
    return (
      <div className="space-y-4">
        <div className="h-20 rounded-xl bg-slate-900 border border-slate-800 animate-pulse" />
        <div className="h-96 rounded-xl bg-slate-900 border border-slate-800 animate-pulse" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-100">Interview History</h1>
          <p className="text-sm text-slate-400 mt-1">
            Review past sessions, resume in-progress technical interviews, or inspect diagnostic reports.
          </p>
        </div>
        <div className="flex items-center gap-2.5">
          <button
            onClick={loadHistory}
            className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-900 px-3 py-2 text-xs font-medium text-slate-300 hover:bg-slate-800 transition-colors"
          >
            <RefreshCw className="h-3.5 w-3.5" /> Refresh
          </button>
          <Link
            to="/interviews/new"
            className="inline-flex items-center gap-2 rounded-lg bg-brand-600 hover:bg-brand-500 px-4 py-2 text-xs font-semibold text-white transition-colors"
          >
            <PlusCircle className="h-4 w-4" /> New Interview
          </Link>
        </div>
      </div>

      {error && (
        <div className="rounded-xl border border-rose-500/30 bg-rose-500/10 p-4 text-sm text-rose-200">
          {error}
        </div>
      )}

      {/* Filter bar */}
      <div className="rounded-xl border border-slate-800 bg-slate-900/60 p-4 flex flex-col md:flex-row items-stretch md:items-center justify-between gap-3">
        <div className="relative flex-1">
          <Search className="h-4 w-4 text-slate-500 absolute left-3.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Filter by topic (e.g. Arrays, Dynamic Programming), type, or session ID..."
            className="w-full rounded-lg border border-slate-700 bg-slate-950 pl-9 pr-4 py-2 text-xs text-slate-200 placeholder-slate-500 focus:border-brand-500 focus:outline-none"
          />
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          <div className="flex items-center gap-1.5 text-xs text-slate-400">
            <Filter className="h-3.5 w-3.5" />
            <span>Status:</span>
          </div>
          <select
            aria-label="Filter by status"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-1.5 text-xs text-slate-200 focus:border-brand-500 focus:outline-none"
          >
            <option value="ALL">All Statuses</option>
            <option value="COMPLETED">Completed</option>
            <option value="IN_PROGRESS">In Progress</option>
            <option value="CREATED">Created</option>
          </select>

          <select
            aria-label="Filter by difficulty"
            value={difficultyFilter}
            onChange={(e) => setDifficultyFilter(e.target.value)}
            className="rounded-lg border border-slate-700 bg-slate-950 px-3 py-1.5 text-xs text-slate-200 focus:border-brand-500 focus:outline-none"
          >
            <option value="ALL">All Difficulties</option>
            <option value="EASY">Easy</option>
            <option value="MEDIUM">Medium</option>
            <option value="HARD">Hard</option>
          </select>
        </div>
      </div>

      {/* Sessions Table / Empty State */}
      {sessions.length === 0 ? (
        <div className="rounded-2xl border border-slate-800 bg-slate-900/50 p-10 text-center">
          <Clock className="h-10 w-10 text-slate-600 mx-auto mb-3" />
          <h2 className="text-base font-semibold text-slate-200">No Interview Sessions Yet</h2>
          <p className="text-sm text-slate-400 max-w-md mx-auto mt-1 mb-5">
            Your completed and active technical interviews will appear here with full competency breakdowns and mistake diagnoses.
          </p>
          <Link
            to="/interviews/new"
            className="inline-flex items-center gap-2 rounded-lg bg-brand-600 hover:bg-brand-500 px-4 py-2.5 text-xs font-semibold text-white transition-colors"
          >
            <Play className="h-3.5 w-3.5" /> Start Your First Interview
          </Link>
        </div>
      ) : filteredSessions.length === 0 ? (
        <div className="rounded-xl border border-slate-800 bg-slate-900/40 p-8 text-center text-sm text-slate-400">
          No interview sessions match your current filter criteria.
        </div>
      ) : (
        <div className="rounded-xl border border-slate-800 bg-slate-900/60 overflow-hidden">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-slate-800 text-[11px] font-semibold uppercase tracking-wider text-slate-400 bg-slate-950/60">
                  <th className="py-3.5 px-4">Session</th>
                  <th className="py-3.5 px-4">Type & Style</th>
                  <th className="py-3.5 px-4">Topics</th>
                  <th className="py-3.5 px-4">Difficulty</th>
                  <th className="py-3.5 px-4">Questions / Duration</th>
                  <th className="py-3.5 px-4">Status</th>
                  <th className="py-3.5 px-4">Overall Score</th>
                  <th className="py-3.5 px-4 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/70 text-xs">
                {filteredSessions.map((session) => (
                  <tr key={session.id} className="hover:bg-slate-800/30 transition-colors">
                    <td className="py-3.5 px-4">
                      <div className="font-semibold text-slate-200">Session #{session.id}</div>
                      <div className="text-[11px] text-slate-500">
                        {new Date(session.createdAt).toLocaleString()}
                      </div>
                    </td>
                    <td className="py-3.5 px-4">
                      <span className="font-medium text-slate-200">{session.interviewType}</span>
                      <div className="text-[11px] text-slate-500">{session.interviewStyle}</div>
                    </td>
                    <td className="py-3.5 px-4">
                      <div className="flex flex-wrap gap-1 max-w-xs">
                        {session.topics.map((topic) => (
                          <span
                            key={topic}
                            className="rounded bg-slate-800 px-2 py-0.5 text-[11px] text-slate-300"
                          >
                            {topic}
                          </span>
                        ))}
                      </div>
                    </td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`inline-flex rounded px-2 py-0.5 text-[11px] font-medium ${
                          session.configuredDifficulty === 'HARD'
                            ? 'bg-rose-500/15 text-rose-300 border border-rose-500/30'
                            : session.configuredDifficulty === 'MEDIUM'
                            ? 'bg-amber-500/15 text-amber-300 border border-amber-500/30'
                            : 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                        }`}
                      >
                        {session.configuredDifficulty}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-slate-300">
                      {session.questionCount} Qs · {Math.round(session.durationSeconds / 60)} min
                    </td>
                    <td className="py-3.5 px-4">
                      <span
                        className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-medium ${
                          session.status === 'COMPLETED'
                            ? 'bg-emerald-500/15 text-emerald-300'
                            : session.status === 'IN_PROGRESS'
                            ? 'bg-brand-500/15 text-brand-300'
                            : 'bg-slate-800 text-slate-300'
                        }`}
                      >
                        {session.status}
                      </span>
                    </td>
                    <td className="py-3.5 px-4">
                      {session.overallScore != null ? (
                        <div className="inline-flex items-center gap-1.5 font-mono font-semibold text-slate-100">
                          <Trophy className="h-3.5 w-3.5 text-amber-400" />
                          {session.overallScore.toFixed(1)} / 100
                        </div>
                      ) : (
                        <span className="text-slate-500">—</span>
                      )}
                    </td>
                    <td className="py-3.5 px-4 text-right">
                      {session.status === 'COMPLETED' ? (
                        <Link
                          to={`/interviews/${session.id}/report`}
                          className="inline-flex items-center gap-1.5 rounded-lg border border-slate-700 bg-slate-950 hover:bg-slate-800 px-3 py-1.5 text-xs font-medium text-brand-400 transition-colors"
                        >
                          <FileText className="h-3.5 w-3.5" /> View Report
                        </Link>
                      ) : (
                        <Link
                          to={`/interviews/${session.id}/workspace`}
                          className="inline-flex items-center gap-1.5 rounded-lg bg-brand-600 hover:bg-brand-500 px-3 py-1.5 text-xs font-semibold text-white transition-colors"
                        >
                          <Play className="h-3.5 w-3.5" /> Resume
                        </Link>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};
