import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  AlertCircle,
  BrainCircuit,
  Bug,
  Check,
  Clock,
  Code2,
  Layers,
  MessageSquare,
  Play,
  Sliders,
} from 'lucide-react';
import { api } from '../services/api';

const AVAILABLE_TOPICS = [
  'Arrays',
  'Strings',
  'Hash Maps',
  'Linked Lists',
  'Stacks',
  'Queues',
  'Trees',
  'Graphs',
  'Binary Search',
  'Dynamic Programming',
];

export const InterviewConfigPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const query = new URLSearchParams(location.search);

  const [interviewType, setInterviewType] = useState<string>(
    query.get('type')?.toUpperCase() || 'DSA'
  );
  const [difficulty, setDifficulty] = useState<string>(
    query.get('difficulty')?.toUpperCase() || 'MEDIUM'
  );
  const [selectedTopics, setSelectedTopics] = useState<string[]>(() => {
    const paramTopic = query.get('topic');
    return paramTopic ? [paramTopic] : ['Arrays', 'Hash Maps', 'Strings', 'Dynamic Programming'];
  });
  const [durationMinutes, setDurationMinutes] = useState<number>(30);
  const [questionCount, setQuestionCount] = useState<number>(2);
  const [interviewStyle, setInterviewStyle] = useState<string>('INTERVIEWER_LED');
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const maxQuestionsForDuration = durationMinutes === 15 ? 2 : durationMinutes === 30 ? 3 : 4;

  useEffect(() => {
    if (questionCount > maxQuestionsForDuration) {
      setQuestionCount(maxQuestionsForDuration);
    }
  }, [durationMinutes, maxQuestionsForDuration, questionCount]);

  const toggleTopic = (topic: string) => {
    setSelectedTopics((prev) =>
      prev.includes(topic) ? prev.filter((t) => t !== topic) : [...prev, topic]
    );
  };

  const handleStartInterview = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (selectedTopics.length === 0) {
      setError('Please select at least one DSA topic for your interview.');
      return;
    }

    setCreating(true);
    try {
      const session = await api.interviews.create({
        interviewType,
        difficulty,
        topics: selectedTopics,
        durationMinutes,
        questionCount,
        interviewStyle,
      });
      await api.interviews.start(session.id);
      navigate(`/interviews/${session.id}/workspace`);
    } catch (err: any) {
      setError(
        err.response?.data?.message || 'Failed to create and start interview session.'
      );
    } finally {
      setCreating(false);
    }
  };

  const interviewTypes = [
    {
      id: 'DSA',
      title: 'DSA Coding',
      icon: Code2,
      desc: 'Standard algorithmic problem-solving with optimal pattern selection and complexity proofs.',
    },
    {
      id: 'DEBUGGING',
      title: 'Debugging & Defect Isolation',
      icon: Bug,
      desc: 'Diagnose and repair realistic boundary, accumulator, and type defects in broken Java code.',
    },
    {
      id: 'CONCEPTUAL',
      title: 'Conceptual & Optimization',
      icon: BrainCircuit,
      desc: 'Deep emphasis on trade-off analysis, invariant justification, and asymptotic optimization.',
    },
    {
      id: 'MIXED',
      title: 'Adaptive Mixed Session',
      icon: Layers,
      desc: 'Blends DSA implementation, debugging, and conceptual follow-ups based on your skill vector.',
    },
  ];

  return (
    <div className="p-6 lg:p-8 max-w-4xl mx-auto space-y-8">
      <div>
        <h1 className="text-2xl font-bold text-white flex items-center gap-2.5">
          <Sliders className="w-6 h-6 text-blue-400" />
          <span>Configure Adaptive Technical Interview</span>
        </h1>
        <p className="text-xs text-slate-400 mt-1">
          Customize your session parameters. The adaptive engine will select questions targeting your weakest competencies within bounded difficulty steps.
        </p>
      </div>

      {error && (
        <div
          role="alert"
          className="p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs flex items-center gap-2.5"
        >
          <AlertCircle className="w-4 h-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      <form onSubmit={handleStartInterview} className="space-y-6">
        {/* 1. Interview Type */}
        <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
          <h2 className="text-sm font-semibold text-white">1. Select Interview Focus</h2>
          <div className="grid sm:grid-cols-2 gap-3">
            {interviewTypes.map((t) => {
              const Icon = t.icon;
              const active = interviewType === t.id;
              return (
                <button
                  key={t.id}
                  type="button"
                  onClick={() => setInterviewType(t.id)}
                  className={`p-4 rounded-xl border text-left transition-all flex items-start gap-3.5 ${
                    active
                      ? 'bg-blue-600/15 border-blue-500 text-white'
                      : 'bg-[#090d16] border-slate-800 text-slate-300 hover:border-slate-700'
                  }`}
                >
                  <div
                    className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 mt-0.5 ${
                      active ? 'bg-blue-600 text-white' : 'bg-slate-800 text-slate-400'
                    }`}
                  >
                    <Icon className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="text-xs font-semibold">{t.title}</div>
                    <p className="text-[11px] text-slate-400 mt-1 leading-relaxed">{t.desc}</p>
                  </div>
                </button>
              );
            })}
          </div>
        </div>

        {/* 2. Difficulty & Interview Style */}
        <div className="grid sm:grid-cols-2 gap-6">
          <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
            <h2 className="text-sm font-semibold text-white">2. Initial Difficulty Baseline</h2>
            <div className="grid grid-cols-3 gap-2.5">
              {(['EASY', 'MEDIUM', 'HARD'] as const).map((diff) => (
                <button
                  key={diff}
                  type="button"
                  onClick={() => setDifficulty(diff)}
                  className={`py-2.5 px-3 rounded-lg border text-xs font-mono font-semibold transition-colors ${
                    difficulty === diff
                      ? diff === 'EASY'
                        ? 'bg-emerald-500/20 border-emerald-500 text-emerald-300'
                        : diff === 'MEDIUM'
                        ? 'bg-amber-500/20 border-amber-500 text-amber-300'
                        : 'bg-rose-500/20 border-rose-500 text-rose-300'
                      : 'bg-[#090d16] border-slate-800 text-slate-400 hover:border-slate-700'
                  }`}
                >
                  {diff}
                </button>
              ))}
            </div>
            <p className="text-[11px] text-slate-400">
              Subsequent questions adjust by at most ±1 difficulty step based on your performance.
            </p>
          </div>

          <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
            <h2 className="text-sm font-semibold text-white">3. Interview Interaction Style</h2>
            <div className="grid grid-cols-2 gap-2.5">
              {[
                {
                  id: 'INTERVIEWER_LED',
                  label: 'Interviewer-Led',
                  sub: 'Active probing & follow-ups',
                },
                {
                  id: 'CODING_FOCUSED',
                  label: 'Coding-Focused',
                  sub: 'Execution & rubric priority',
                },
              ].map((st) => (
                <button
                  key={st.id}
                  type="button"
                  onClick={() => setInterviewStyle(st.id)}
                  className={`p-3 rounded-lg border text-left transition-colors ${
                    interviewStyle === st.id
                      ? 'bg-blue-600/15 border-blue-500 text-white'
                      : 'bg-[#090d16] border-slate-800 text-slate-400 hover:border-slate-700'
                  }`}
                >
                  <div className="text-xs font-semibold flex items-center gap-1.5">
                    <MessageSquare className="w-3.5 h-3.5 text-blue-400" />
                    <span>{st.label}</span>
                  </div>
                  <div className="text-[11px] text-slate-400 mt-1">{st.sub}</div>
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* 4. Topics Multi-Select */}
        <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-sm font-semibold text-white">4. Target DSA Topics</h2>
              <p className="text-xs text-slate-400">
                Select one or more topics to cover during the interview session
              </p>
            </div>
            <button
              type="button"
              onClick={() =>
                setSelectedTopics(
                  selectedTopics.length === AVAILABLE_TOPICS.length ? ['Arrays'] : [...AVAILABLE_TOPICS]
                )
              }
              className="text-xs text-blue-400 hover:text-blue-300 font-medium"
            >
              {selectedTopics.length === AVAILABLE_TOPICS.length ? 'Reset' : 'Select All'}
            </button>
          </div>

          <div className="flex flex-wrap gap-2">
            {AVAILABLE_TOPICS.map((topic) => {
              const checked = selectedTopics.includes(topic);
              return (
                <button
                  key={topic}
                  type="button"
                  onClick={() => toggleTopic(topic)}
                  className={`px-3 py-1.5 rounded-lg border text-xs font-medium flex items-center gap-1.5 transition-colors ${
                    checked
                      ? 'bg-blue-600/20 border-blue-500 text-blue-200'
                      : 'bg-[#090d16] border-slate-800 text-slate-400 hover:border-slate-700'
                  }`}
                >
                  {checked && <Check className="w-3.5 h-3.5 text-blue-400" />}
                  <span>{topic}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* 5. Duration & Question Count */}
        <div className="grid sm:grid-cols-2 gap-6">
          <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
            <h2 className="text-sm font-semibold text-white flex items-center gap-2">
              <Clock className="w-4 h-4 text-teal-400" />
              <span>5. Session Duration</span>
            </h2>
            <div className="grid grid-cols-3 gap-2.5">
              {[15, 30, 45].map((mins) => (
                <button
                  key={mins}
                  type="button"
                  onClick={() => setDurationMinutes(mins)}
                  className={`py-2.5 px-3 rounded-lg border text-xs font-mono font-semibold transition-colors ${
                    durationMinutes === mins
                      ? 'bg-teal-500/20 border-teal-500 text-teal-300'
                      : 'bg-[#090d16] border-slate-800 text-slate-400 hover:border-slate-700'
                  }`}
                >
                  {mins} min
                </button>
              ))}
            </div>
            <p className="text-[11px] text-slate-400">
              Enforced by server-side deadline clock.
            </p>
          </div>

          <div className="p-6 rounded-xl bg-[#0d1321] border border-slate-800/90 space-y-4">
            <h2 className="text-sm font-semibold text-white">
              6. Number of Questions (Constrained by {durationMinutes}m)
            </h2>
            <div className="grid grid-cols-4 gap-2.5">
              {[1, 2, 3, 4].map((cnt) => {
                const disabled = cnt > maxQuestionsForDuration;
                return (
                  <button
                    key={cnt}
                    type="button"
                    disabled={disabled}
                    onClick={() => setQuestionCount(cnt)}
                    className={`py-2.5 px-3 rounded-lg border text-xs font-mono font-semibold transition-colors ${
                      disabled
                        ? 'bg-slate-900/50 border-slate-800/50 text-slate-600 cursor-not-allowed'
                        : questionCount === cnt
                        ? 'bg-blue-600/20 border-blue-500 text-blue-300'
                        : 'bg-[#090d16] border-slate-800 text-slate-400 hover:border-slate-700'
                    }`}
                  >
                    {cnt} {cnt === 1 ? 'Q' : 'Qs'}
                  </button>
                );
              })}
            </div>
            <p className="text-[11px] text-slate-400">
              Max {maxQuestionsForDuration} questions allowed for a {durationMinutes}-minute interview.
            </p>
          </div>
        </div>

        {/* Submit Action */}
        <div className="flex items-center justify-end gap-4 pt-2">
          <button
            type="submit"
            disabled={creating}
            className="px-6 py-3 rounded-xl bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white font-semibold text-sm transition-colors flex items-center gap-2 shadow-lg shadow-blue-950/50"
          >
            <Play className="w-4 h-4" />
            <span>{creating ? 'Initializing Session...' : 'Launch Live Interview Workspace'}</span>
          </button>
        </div>
      </form>
    </div>
  );
};
