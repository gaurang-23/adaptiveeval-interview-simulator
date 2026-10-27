import React, { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import Editor from '@monaco-editor/react';
import {
  AlertCircle,
  ArrowRight,
  Bot,
  CheckCircle2,
  Clock,
  Code2,
  Cpu,
  FileCode,
  HelpCircle,
  History,
  Lightbulb,
  Play,
  RotateCcw,
  Send,
  Sparkles,
  Square,
  Terminal,
  XCircle,
} from 'lucide-react';
import { api } from '../services/api';
import { ThemeToggle, useTheme } from '../features/theme/ThemeContext';
import type {
  Attempt,
  ExplanationEvaluationResult,
  InterviewMessage,
  InterviewWorkspaceState,
} from '../types/api';

export const InterviewWorkspacePage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const sessionId = Number(id);
  const navigate = useNavigate();
  const { theme } = useTheme();

  const [workspace, setWorkspace] = useState<InterviewWorkspaceState | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Editor state preserved across panel switches & messages
  const [code, setCode] = useState<string>('');
  const [remainingSeconds, setRemainingSeconds] = useState<number>(0);

  // Center bottom panel tabs: 'output' | 'explain' | 'history'
  const [bottomTab, setBottomTab] = useState<'output' | 'explain' | 'history'>('output');
  const [latestAttempt, setLatestAttempt] = useState<Attempt | null>(null);
  const [runningCode, setRunningCode] = useState(false);
  const [submittingCode, setSubmittingCode] = useState(false);

  // Explanation form state
  const [algorithmExplanation, setAlgorithmExplanation] = useState('');
  const [timeComplexity, setTimeComplexity] = useState('O(n)');
  const [spaceComplexity, setSpaceComplexity] = useState('O(n)');
  const [tradeOffs, setTradeOffs] = useState('');
  const [submittingExplanation, setSubmittingExplanation] = useState(false);
  const [explanationResult, setExplanationResult] = useState<ExplanationEvaluationResult | null>(null);

  // Chat & Hint state
  const [chatInput, setChatInput] = useState('');
  const [sendingChat, setSendingChat] = useState(false);
  const [requestingHint, setRequestingHint] = useState(false);
  const [advancingQuestion, setAdvancingQuestion] = useState(false);
  const [endingInterview, setEndingInterview] = useState(false);

  // Mobile responsive tab selector
  const [mobilePanel, setMobilePanel] = useState<'problem' | 'editor' | 'interviewer'>('editor');

  const chatEndRef = useRef<HTMLDivElement | null>(null);

  const getDraftKey = (sId: number, qId: number) => `atis_code_draft_${sId}_${qId}`;

  const loadWorkspace = async () => {
    try {
      let state = await api.interviews.getWorkspace(sessionId);
      if (state.session.status === 'CREATED') {
        state = await api.interviews.start(sessionId);
      }
      if (state.session.status === 'COMPLETED') {
        navigate(`/interviews/${sessionId}/report`, { replace: true });
        return;
      }
      applyWorkspaceState(state);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to load interview workspace.');
    } finally {
      setLoading(false);
    }
  };

  const applyWorkspaceState = (state: InterviewWorkspaceState) => {
    setWorkspace(state);
    setRemainingSeconds(state.remainingSeconds);

    const activeQ = state.activeQuestion?.question;
    if (activeQ) {
      const draftKey = getDraftKey(state.session.id, activeQ.id);
      const savedDraft = localStorage.getItem(draftKey);
      const attempts = state.currentQuestionAttempts || [];
      const lastAttempt = attempts.length > 0 ? attempts[attempts.length - 1] : null;
      setLatestAttempt(lastAttempt);

      if (savedDraft !== null) {
        setCode(savedDraft);
      } else if (lastAttempt) {
        setCode(lastAttempt.submittedCode);
      } else {
        setCode(activeQ.starterCode);
      }
    }
  };

  useEffect(() => {
    if (!Number.isNaN(sessionId)) {
      loadWorkspace();
    }
  }, [sessionId]);

  // Countdown timer synced with server remainingSeconds
  useEffect(() => {
    if (!workspace || workspace.session.status !== 'IN_PROGRESS') return;
    const timer = setInterval(() => {
      setRemainingSeconds((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          handleEndInterview();
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(timer);
  }, [workspace?.session.status]);

  // Auto-scroll chat messages
  useEffect(() => {
    if (typeof chatEndRef.current?.scrollIntoView === 'function') {
      chatEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [workspace?.messages.length]);

  const handleCodeChange = (newVal: string | undefined) => {
    const val = newVal ?? '';
    setCode(val);
    if (workspace?.activeQuestion) {
      localStorage.setItem(
        getDraftKey(workspace.session.id, workspace.activeQuestion.question.id),
        val
      );
    }
  };

  const handleResetCode = () => {
    if (!workspace?.activeQuestion) return;
    const starter = workspace.activeQuestion.question.starterCode;
    setCode(starter);
    localStorage.setItem(
      getDraftKey(workspace.session.id, workspace.activeQuestion.question.id),
      starter
    );
  };

  const handleExecuteCode = async (sampleOnly: boolean) => {
    if (!workspace?.activeQuestion) return;
    if (sampleOnly) setRunningCode(true);
    else setSubmittingCode(true);
    setBottomTab('output');

    try {
      const attempt = await api.interviews.submitCode(sessionId, {
        code,
        language: 'JAVA',
        sampleOnly,
      });
      setLatestAttempt(attempt);
      const updated = await api.interviews.getWorkspace(sessionId);
      setWorkspace(updated);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Code execution request failed.');
    } finally {
      setRunningCode(false);
      setSubmittingCode(false);
    }
  };

  const handleSubmitExplanation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!algorithmExplanation.trim()) return;
    setSubmittingExplanation(true);
    try {
      const evalRes = await api.interviews.submitExplanation(sessionId, {
        algorithmExplanation: algorithmExplanation.trim(),
        timeComplexity,
        spaceComplexity,
        tradeOffs: tradeOffs.trim(),
      });
      setExplanationResult(evalRes);
      const updated = await api.interviews.getWorkspace(sessionId);
      setWorkspace(updated);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to evaluate explanation.');
    } finally {
      setSubmittingExplanation(false);
    }
  };

  const handleRequestHint = async () => {
    setRequestingHint(true);
    try {
      await api.interviews.requestHint(sessionId);
      const updated = await api.interviews.getWorkspace(sessionId);
      setWorkspace(updated);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Unable to request hint.');
    } finally {
      setRequestingHint(false);
    }
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatInput.trim() || sendingChat) return;
    const msgText = chatInput.trim();
    setChatInput('');
    setSendingChat(true);

    try {
      await api.interviews.sendMessage(sessionId, {
        content: msgText,
        currentCode: code,
      });
      const updated = await api.interviews.getWorkspace(sessionId);
      setWorkspace(updated);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to send message.');
    } finally {
      setSendingChat(false);
    }
  };

  const handleNextQuestion = async () => {
    setAdvancingQuestion(true);
    try {
      const nextState = await api.interviews.nextQuestion(sessionId);
      if (nextState.session.status === 'COMPLETED') {
        navigate(`/interviews/${sessionId}/report`);
        return;
      }
      setExplanationResult(null);
      setAlgorithmExplanation('');
      setTradeOffs('');
      applyWorkspaceState(nextState);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to advance to next question.');
    } finally {
      setAdvancingQuestion(false);
    }
  };

  const handleEndInterview = async () => {
    if (endingInterview) return;
    setEndingInterview(true);
    try {
      await api.interviews.end(sessionId);
      navigate(`/interviews/${sessionId}/report`);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to finalize interview.');
      setEndingInterview(false);
    }
  };

  const formatTimer = (sec: number) => {
    const mins = Math.floor(sec / 60);
    const rem = sec % 60;
    return `${String(mins).padStart(2, '0')}:${String(rem).padStart(2, '0')}`;
  };

  if (loading) {
    return (
      <div className="h-screen bg-[#090d16] flex items-center justify-center text-slate-300 text-sm font-mono">
        Loading Isolated Interview Workspace...
      </div>
    );
  }

  if (!workspace || !workspace.activeQuestion) {
    return (
      <div className="p-8">
        <div className="p-6 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300">
          {error || 'No active question found for this session.'}
        </div>
      </div>
    );
  }

  const activeIq = workspace.activeQuestion;
  const q = activeIq.question;
  const hasMoreQuestions = activeIq.sequenceNumber < activeIq.totalQuestions;

  return (
    <div className="h-screen bg-[#090d16] text-slate-100 flex flex-col overflow-hidden">
      {/* Top Toolbar */}
      <header className="h-14 px-4 bg-[#0d1321] border-b border-slate-800 flex items-center justify-between gap-4 shrink-0">
        <div className="flex items-center gap-3 min-w-0">
          <div className="flex items-center gap-2">
            <div className="w-7 h-7 rounded bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400">
              <Code2 className="w-4 h-4" />
            </div>
            <span className="font-semibold text-sm text-white truncate">{q.title}</span>
          </div>

          <span className="px-2 py-0.5 rounded bg-slate-800 border border-slate-700 text-xs font-mono text-blue-300">
            Question {activeIq.sequenceNumber} of {activeIq.totalQuestions}
          </span>

          <span
            className={`hidden md:inline-flex items-center gap-1 px-2 py-0.5 rounded text-[11px] font-mono ${
              workspace.aiMode === 'LIVE_LLM'
                ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                : 'bg-amber-500/15 text-amber-300 border border-amber-500/30'
            }`}
            title={
              workspace.aiMode === 'LIVE_LLM'
                ? 'Connected to live LLM provider'
                : 'No LLM API key configured — using deterministic rubric evaluation'
            }
          >
            <Sparkles className="w-3 h-3" />
            <span>
              {workspace.aiMode === 'LIVE_LLM' ? 'Live AI Evaluator' : 'Fallback Rubric Mode'}
            </span>
          </span>
        </div>

        {/* Center Timer */}
        <div
          className={`px-3 py-1 rounded-lg border font-mono text-sm font-bold flex items-center gap-2 ${
            remainingSeconds < 180
              ? 'bg-rose-500/15 border-rose-500/40 text-rose-400 animate-pulse'
              : 'bg-[#090d16] border-slate-800 text-slate-200'
          }`}
        >
          <Clock className="w-4 h-4 text-blue-400" />
          <span>{formatTimer(remainingSeconds)}</span>
        </div>

        {/* Right Actions */}
        <div className="flex items-center gap-2.5">
          <ThemeToggle compact />
          {hasMoreQuestions && (
            <button
              type="button"
              onClick={handleNextQuestion}
              disabled={advancingQuestion}
              className="px-3 py-1.5 rounded-lg bg-blue-600/20 hover:bg-blue-600/30 border border-blue-500/40 text-blue-300 text-xs font-semibold flex items-center gap-1.5 transition-colors"
            >
              <span>{advancingQuestion ? 'Selecting Next...' : 'Next Adaptive Question'}</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          )}

          <button
            type="button"
            onClick={handleEndInterview}
            disabled={endingInterview}
            className="px-3 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-500 disabled:opacity-50 text-white text-xs font-semibold flex items-center gap-1.5 transition-colors"
          >
            <Square className="w-3 h-3 fill-current" />
            <span>{endingInterview ? 'Generating Report...' : 'End Interview'}</span>
          </button>
        </div>
      </header>

      {error && (
        <div className="px-4 py-2 bg-rose-500/15 border-b border-rose-500/30 text-rose-300 text-xs flex items-center justify-between">
          <div className="flex items-center gap-2">
            <AlertCircle className="w-4 h-4 shrink-0" />
            <span>{error}</span>
          </div>
          <button onClick={() => setError(null)} className="text-rose-200 underline">
            Dismiss
          </button>
        </div>
      )}

      {/* Mobile Tab Switcher */}
      <div className="flex lg:hidden border-b border-slate-800 bg-[#0d1321] text-xs font-medium">
        <button
          onClick={() => setMobilePanel('problem')}
          className={`flex-1 py-2.5 text-center border-b-2 ${
            mobilePanel === 'problem' ? 'border-blue-500 text-blue-400' : 'border-transparent text-slate-400'
          }`}
        >
          Problem & Hints
        </button>
        <button
          onClick={() => setMobilePanel('editor')}
          className={`flex-1 py-2.5 text-center border-b-2 ${
            mobilePanel === 'editor' ? 'border-blue-500 text-blue-400' : 'border-transparent text-slate-400'
          }`}
        >
          Code & Output
        </button>
        <button
          onClick={() => setMobilePanel('interviewer')}
          className={`flex-1 py-2.5 text-center border-b-2 ${
            mobilePanel === 'interviewer' ? 'border-blue-500 text-blue-400' : 'border-transparent text-slate-400'
          }`}
        >
          AI Interviewer
        </button>
      </div>

      {/* 3-Panel Workspace Grid */}
      <div className="flex-1 min-h-0 grid lg:grid-cols-12 divide-x divide-slate-800/80">
        {/* LEFT PANEL: Problem Statement, Constraints, Examples, Progressive Hints */}
        <div
          className={`lg:col-span-3 flex flex-col min-h-0 bg-[#0d1321]/60 overflow-y-auto p-4 space-y-5 ${
            mobilePanel === 'problem' ? 'block' : 'hidden lg:flex'
          }`}
        >
          {/* Badges & Adaptive Reason */}
          <div className="space-y-3">
            <div className="flex flex-wrap items-center gap-2">
              <span
                className={`px-2 py-0.5 rounded text-[11px] font-mono font-semibold ${
                  q.difficulty === 'EASY'
                    ? 'bg-emerald-500/15 text-emerald-300 border border-emerald-500/30'
                    : q.difficulty === 'MEDIUM'
                    ? 'bg-amber-500/15 text-amber-300 border border-amber-500/30'
                    : 'bg-rose-500/15 text-rose-300 border border-rose-500/30'
                }`}
              >
                {q.difficulty}
              </span>
              <span className="px-2 py-0.5 rounded bg-blue-500/15 text-blue-300 border border-blue-500/30 text-[11px] font-mono">
                {q.topic}
              </span>
              <span className="px-2 py-0.5 rounded bg-violet-500/15 text-violet-300 border border-violet-500/30 text-[11px] font-mono">
                {q.questionType}
              </span>
            </div>

            <div className="p-3 rounded-lg bg-blue-950/30 border border-blue-500/25 text-xs space-y-1">
              <div className="font-semibold text-blue-300 flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5" />
                <span>Adaptive Selection Policy</span>
              </div>
              <p className="text-slate-300 text-[11px] leading-relaxed">
                {activeIq.selectionReason}
              </p>
            </div>
          </div>

          {/* Problem Statement */}
          <div className="space-y-2">
            <h2 className="text-sm font-semibold text-white">Problem Statement</h2>
            <div className="text-xs text-slate-300 leading-relaxed whitespace-pre-wrap bg-[#090d16] p-3.5 rounded-lg border border-slate-800/80">
              {q.statement}
            </div>
          </div>

          {/* Examples */}
          <div className="space-y-2.5">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              Examples
            </h3>
            {q.examples.map((ex, idx) => (
              <div
                key={idx}
                className="p-3 rounded-lg bg-[#090d16] border border-slate-800/90 space-y-1.5 text-xs font-mono"
              >
                <div>
                  <span className="text-slate-500">Input: </span>
                  <span className="text-slate-200">{ex.input}</span>
                </div>
                <div>
                  <span className="text-slate-500">Output: </span>
                  <span className="text-emerald-400">{ex.output}</span>
                </div>
                {ex.explanation && (
                  <div className="text-[11px] font-sans text-slate-400 pt-1 border-t border-slate-800/60">
                    {ex.explanation}
                  </div>
                )}
              </div>
            ))}
          </div>

          {/* Constraints */}
          <div className="space-y-2">
            <h3 className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              Constraints
            </h3>
            <pre className="p-3 rounded-lg bg-[#090d16] border border-slate-800/90 text-xs font-mono text-slate-300 whitespace-pre-wrap">
              {q.constraintsText}
            </pre>
          </div>

          {/* Progressive Hints Section */}
          <div className="space-y-2.5 pt-2 border-t border-slate-800">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold text-amber-300 flex items-center gap-1.5">
                <Lightbulb className="w-4 h-4" />
                <span>
                  Progressive Hints ({activeIq.hintsUsed} / {q.totalHintsAvailable})
                </span>
              </span>
              <button
                type="button"
                onClick={handleRequestHint}
                disabled={requestingHint || activeIq.hintsUsed >= q.totalHintsAvailable}
                className="px-2.5 py-1 rounded bg-amber-500/15 hover:bg-amber-500/25 disabled:opacity-40 text-amber-300 border border-amber-500/30 text-[11px] font-medium transition-colors"
              >
                {requestingHint
                  ? 'Unlocking...'
                  : activeIq.hintsUsed >= q.totalHintsAvailable
                  ? 'All Hints Unlocked'
                  : 'Request Next Hint'}
              </button>
            </div>

            {activeIq.unlockedHints.length === 0 ? (
              <p className="text-[11px] text-slate-400">
                Try formulating an initial approach first. Requesting graduated hints is tracked as part of your diagnostic profile.
              </p>
            ) : (
              <div className="space-y-2">
                {activeIq.unlockedHints.map((hint, idx) => (
                  <div
                    key={idx}
                    className="p-3 rounded-lg bg-amber-500/10 border border-amber-500/25 text-xs text-amber-100 space-y-1"
                  >
                    <span className="font-mono text-[10px] text-amber-300 uppercase">
                      Hint Level {idx + 1}
                    </span>
                    <p className="leading-relaxed">{hint}</p>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        {/* CENTER PANEL: Monaco Code Editor + Execution Controls + Output / Explanation / History */}
        <div
          className={`lg:col-span-6 flex flex-col min-h-0 bg-[#090d16] ${
            mobilePanel === 'editor' ? 'flex' : 'hidden lg:flex'
          }`}
        >
          {/* Editor Action Bar */}
          <div className="h-11 px-4 bg-[#0d1321] border-b border-slate-800 flex items-center justify-between gap-2 shrink-0">
            <div className="flex items-center gap-2">
              <FileCode className="w-4 h-4 text-blue-400" />
              <select
                aria-label="Programming Language"
                value="JAVA"
                disabled
                className="bg-[#090d16] border border-slate-700 rounded px-2 py-1 text-xs font-mono text-slate-200"
              >
                <option value="JAVA">Java 21 (Solution.java)</option>
              </select>
              <button
                type="button"
                onClick={handleResetCode}
                title="Reset to starter code"
                className="px-2.5 py-1 rounded hover:bg-slate-800 text-slate-400 hover:text-slate-200 text-xs flex items-center gap-1 transition-colors"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>Reset</span>
              </button>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => handleExecuteCode(true)}
                disabled={runningCode || submittingCode}
                className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-slate-100 border border-slate-700 text-xs font-medium flex items-center gap-1.5 transition-colors"
              >
                <Play className="w-3.5 h-3.5 text-teal-400" />
                <span>{runningCode ? 'Running Samples...' : 'Run Code'}</span>
              </button>

              <button
                type="button"
                onClick={() => handleExecuteCode(false)}
                disabled={runningCode || submittingCode}
                className="px-3.5 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white text-xs font-semibold flex items-center gap-1.5 transition-colors"
              >
                <CheckCircle2 className="w-3.5 h-3.5" />
                <span>{submittingCode ? 'Evaluating Suite...' : 'Submit Solution'}</span>
              </button>
            </div>
          </div>

          {/* Monaco Editor Container */}
          <div className="flex-1 min-h-[240px] relative border-b border-slate-800">
            <Editor
              height="100%"
              defaultLanguage="java"
              language="java"
              theme={theme === 'light' ? 'light' : 'vs-dark'}
              value={code}
              onChange={handleCodeChange}
              options={{
                minimap: { enabled: false },
                fontSize: 13,
                fontFamily: "'JetBrains Mono', 'Fira Code', Consolas, monospace",
                scrollBeyondLastLine: false,
                automaticLayout: true,
                tabSize: 4,
                padding: { top: 12 },
              }}
              loading={
                <textarea
                  aria-label="Java Code Editor"
                  value={code}
                  onChange={(e) => handleCodeChange(e.target.value)}
                  className="w-full h-full bg-[#090d16] text-slate-100 font-mono text-xs p-4 focus:outline-none"
                />
              }
            />
          </div>

          {/* Bottom Console & Explanation Tabs */}
          <div className="h-72 flex flex-col bg-[#0d1321] shrink-0">
            <div className="h-9 px-4 border-b border-slate-800 flex items-center justify-between text-xs">
              <div className="flex items-center gap-4">
                <button
                  type="button"
                  onClick={() => setBottomTab('output')}
                  className={`py-2 font-medium border-b-2 flex items-center gap-1.5 ${
                    bottomTab === 'output'
                      ? 'border-blue-500 text-blue-400'
                      : 'border-transparent text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <Terminal className="w-3.5 h-3.5" />
                  <span>Test Output & Telemetry</span>
                </button>

                <button
                  type="button"
                  onClick={() => setBottomTab('explain')}
                  className={`py-2 font-medium border-b-2 flex items-center gap-1.5 ${
                    bottomTab === 'explain'
                      ? 'border-blue-500 text-blue-400'
                      : 'border-transparent text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>Explain Algorithm & Complexity</span>
                  {activeIq.explanationSubmitted && (
                    <span className="w-2 h-2 rounded-full bg-emerald-400" />
                  )}
                </button>

                <button
                  type="button"
                  onClick={() => setBottomTab('history')}
                  className={`py-2 font-medium border-b-2 flex items-center gap-1.5 ${
                    bottomTab === 'history'
                      ? 'border-blue-500 text-blue-400'
                      : 'border-transparent text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <History className="w-3.5 h-3.5" />
                  <span>Submissions ({workspace.currentQuestionAttempts.length})</span>
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-4">
              {bottomTab === 'output' && (
                <div>
                  {!latestAttempt ? (
                    <div className="text-xs text-slate-400 flex items-center gap-2">
                      <Cpu className="w-4 h-4 text-slate-500" />
                      <span>
                        Click <strong>Run Code</strong> to test against sample cases or{' '}
                        <strong>Submit Solution</strong> to run the full test suite in the isolated Java runner.
                      </span>
                    </div>
                  ) : (
                    <div className="space-y-3 text-xs">
                      <div className="flex flex-wrap items-center justify-between gap-2 p-2.5 rounded-lg bg-[#090d16] border border-slate-800">
                        <div className="flex items-center gap-2.5">
                          {latestAttempt.compilationStatus === 'SUCCESS' &&
                          latestAttempt.testsPassed === latestAttempt.testsTotal ? (
                            <span className="text-emerald-400 font-semibold flex items-center gap-1.5">
                              <CheckCircle2 className="w-4 h-4" />
                              All {latestAttempt.testsPassed}/{latestAttempt.testsTotal} Tests Passed
                            </span>
                          ) : (
                            <span className="text-rose-400 font-semibold flex items-center gap-1.5">
                              <XCircle className="w-4 h-4" />
                              {latestAttempt.compilationStatus !== 'SUCCESS'
                                ? latestAttempt.compilationStatus
                                : `${latestAttempt.testsPassed}/${latestAttempt.testsTotal} Tests Passed (${latestAttempt.failureCategory})`}
                            </span>
                          )}
                        </div>
                        <div className="flex items-center gap-3 font-mono text-[11px] text-slate-400">
                          <span>Runtime: {latestAttempt.runtimeMs} ms</span>
                          <span>Memory: {latestAttempt.memoryKb} KB</span>
                          <span>Sandbox: {latestAttempt.isolationModeUsed}</span>
                        </div>
                      </div>

                      {latestAttempt.compilerOutput && (
                        <pre className="p-3 rounded-lg bg-rose-950/30 border border-rose-500/30 text-rose-200 font-mono text-[11px] whitespace-pre-wrap">
                          {latestAttempt.compilerOutput}
                        </pre>
                      )}

                      {latestAttempt.testResults && latestAttempt.testResults.length > 0 && (
                        <div className="grid sm:grid-cols-2 gap-2.5">
                          {latestAttempt.testResults.map((tr) => (
                            <div
                              key={tr.testIndex}
                              className={`p-2.5 rounded-lg border font-mono text-[11px] space-y-1 ${
                                tr.passed
                                  ? 'bg-emerald-950/15 border-emerald-500/25 text-slate-200'
                                  : 'bg-rose-950/20 border-rose-500/30 text-slate-200'
                              }`}
                            >
                              <div className="flex items-center justify-between font-sans font-semibold">
                                <span>
                                  Test #{tr.testIndex + 1} {tr.sample ? '(Sample)' : '(Hidden)'}
                                </span>
                                <span className={tr.passed ? 'text-emerald-400' : 'text-rose-400'}>
                                  {tr.passed ? 'PASSED' : 'FAILED'} · {tr.runtimeMs}ms
                                </span>
                              </div>
                              <div>
                                <span className="text-slate-500">Input: </span>
                                <span>{tr.inputData}</span>
                              </div>
                              <div>
                                <span className="text-slate-500">Expected: </span>
                                <span>{tr.expectedOutput}</span>
                              </div>
                              <div>
                                <span className="text-slate-500">Actual: </span>
                                <span>{tr.actualOutput}</span>
                              </div>
                              {tr.stdout && (
                                <div className="text-blue-300">
                                  <span className="text-slate-500">Stdout: </span>
                                  {tr.stdout}
                                </div>
                              )}
                              {tr.errorMessage && (
                                <div className="text-rose-300">{tr.errorMessage}</div>
                              )}
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  )}
                </div>
              )}

              {bottomTab === 'explain' && (
                <form onSubmit={handleSubmitExplanation} className="space-y-3 text-xs">
                  <div className="grid sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-[11px] text-slate-300 mb-1">
                        Time Complexity (Big-O)
                      </label>
                      <select
                        value={timeComplexity}
                        onChange={(e) => setTimeComplexity(e.target.value)}
                        className="w-full px-2.5 py-1.5 rounded bg-[#090d16] border border-slate-700 text-xs font-mono text-white"
                      >
                        <option value="O(1)">O(1)</option>
                        <option value="O(log n)">O(log n)</option>
                        <option value="O(n)">O(n)</option>
                        <option value="O(n log n)">O(n log n)</option>
                        <option value="O(n^2)">O(n^2)</option>
                        <option value="O(amount * n)">O(amount * n)</option>
                        <option value="O(m * n)">O(m * n)</option>
                        <option value="O(2^n)">O(2^n)</option>
                      </select>
                    </div>
                    <div>
                      <label className="block text-[11px] text-slate-300 mb-1">
                        Auxiliary Space Complexity (Big-O)
                      </label>
                      <select
                        value={spaceComplexity}
                        onChange={(e) => setSpaceComplexity(e.target.value)}
                        className="w-full px-2.5 py-1.5 rounded bg-[#090d16] border border-slate-700 text-xs font-mono text-white"
                      >
                        <option value="O(1)">O(1)</option>
                        <option value="O(log n)">O(log n)</option>
                        <option value="O(h)">O(h)</option>
                        <option value="O(n)">O(n)</option>
                        <option value="O(min(n, m))">O(min(n, m))</option>
                        <option value="O(amount)">O(amount)</option>
                        <option value="O(m * n)">O(m * n)</option>
                      </select>
                    </div>
                  </div>

                  <div>
                    <label className="block text-[11px] text-slate-300 mb-1">
                      Algorithm Approach & Correctness Invariant
                    </label>
                    <textarea
                      rows={2}
                      required
                      value={algorithmExplanation}
                      onChange={(e) => setAlgorithmExplanation(e.target.value)}
                      placeholder="Explain why your algorithm works, what data structure or pointer invariant it maintains, and how it avoids redundant work..."
                      className="w-full p-2 rounded bg-[#090d16] border border-slate-700 text-xs text-white placeholder-slate-500"
                    />
                  </div>

                  <div className="flex items-end gap-3">
                    <div className="flex-1">
                      <label className="block text-[11px] text-slate-300 mb-1">
                        Trade-offs & Edge Cases Handled
                      </label>
                      <input
                        type="text"
                        value={tradeOffs}
                        onChange={(e) => setTradeOffs(e.target.value)}
                        placeholder="e.g., Trades O(n) HashMap memory for O(1) lookup time; handles duplicates and negative numbers."
                        className="w-full px-2.5 py-1.5 rounded bg-[#090d16] border border-slate-700 text-xs text-white placeholder-slate-500"
                      />
                    </div>
                    <button
                      type="submit"
                      disabled={submittingExplanation}
                      className="px-4 py-1.5 rounded-lg bg-violet-600 hover:bg-violet-500 disabled:opacity-50 text-white font-semibold text-xs shrink-0"
                    >
                      {submittingExplanation ? 'Evaluating...' : 'Evaluate Explanation'}
                    </button>
                  </div>

                  {explanationResult && (
                    <div className="p-3 rounded-lg bg-violet-950/30 border border-violet-500/30 space-y-1.5 text-xs">
                      <div className="flex items-center justify-between">
                        <span className="font-semibold text-violet-300">
                          Rubric Evaluation: {explanationResult.score.toFixed(1)} / 100
                        </span>
                        <span className="font-mono text-[10px] text-slate-400">
                          Source: {explanationResult.sourceType}
                        </span>
                      </div>
                      <p className="text-slate-300 text-[11px]">{explanationResult.evidence}</p>
                      {explanationResult.fallbackReason && (
                        <p className="text-amber-300 text-[11px] pt-1 border-t border-violet-500/20">
                          Fallback Note: {explanationResult.fallbackReason}
                        </p>
                      )}
                    </div>
                  )}
                </form>
              )}

              {bottomTab === 'history' && (
                <div className="space-y-2 text-xs">
                  {workspace.currentQuestionAttempts.length === 0 ? (
                    <p className="text-slate-500">No submissions recorded for this question yet.</p>
                  ) : (
                    workspace.currentQuestionAttempts.map((att, index) => (
                      <div
                        key={att.id}
                        className="p-2.5 rounded-lg bg-[#090d16] border border-slate-800 flex items-center justify-between"
                      >
                        <div>
                          <span className="font-semibold text-slate-200">
                            Attempt #{index + 1} ({att.finalSubmission ? 'Full Suite' : 'Sample Run'})
                          </span>
                          <span className="ml-2 font-mono text-[11px] text-slate-400">
                            {att.compilationStatus} · {att.testsPassed}/{att.testsTotal} passed · {att.runtimeMs}ms
                          </span>
                        </div>
                        <button
                          type="button"
                          onClick={() => {
                            handleCodeChange(att.submittedCode);
                            setLatestAttempt(att);
                            setBottomTab('output');
                          }}
                          className="px-2 py-1 rounded bg-slate-800 hover:bg-slate-700 text-blue-300 text-[11px]"
                        >
                          Restore Code
                        </button>
                      </div>
                    ))
                  )}
                </div>
              )}
            </div>
          </div>
        </div>

        {/* RIGHT PANEL: AI Interviewer Live Chat & Feedback */}
        <div
          className={`lg:col-span-3 flex flex-col min-h-0 bg-[#0d1321]/80 ${
            mobilePanel === 'interviewer' ? 'flex' : 'hidden lg:flex'
          }`}
        >
          <div className="h-11 px-4 border-b border-slate-800 flex items-center justify-between shrink-0">
            <div className="flex items-center gap-2">
              <Bot className="w-4 h-4 text-violet-400" />
              <span className="text-xs font-semibold text-white">AI Technical Interviewer</span>
            </div>
            <span className="text-[10px] font-mono text-slate-400">
              {workspace.aiMode === 'LIVE_LLM' ? 'Live LLM' : 'Fallback Mode'}
            </span>
          </div>

          {/* Messages Feed */}
          <div className="flex-1 overflow-y-auto p-4 space-y-3">
            {workspace.messages.map((m: InterviewMessage) => {
              const isInterviewer = m.role === 'INTERVIEWER';
              return (
                <div
                  key={m.id}
                  className={`p-3 rounded-xl text-xs leading-relaxed space-y-1.5 ${
                    isInterviewer
                      ? 'bg-[#090d16] border border-slate-800 text-slate-200'
                      : 'bg-blue-600/15 border border-blue-500/30 text-blue-100 ml-4'
                  }`}
                >
                  <div className="flex items-center justify-between text-[10px] font-mono text-slate-400">
                    <span className={isInterviewer ? 'text-violet-400 font-semibold' : 'text-blue-400 font-semibold'}>
                      {isInterviewer ? 'INTERVIEWER' : 'YOU'}
                    </span>
                    <span>{m.messageType}</span>
                  </div>
                  <div className="whitespace-pre-wrap">{m.content}</div>
                </div>
              );
            })}
            <div ref={chatEndRef} />
          </div>

          {/* Chat Input & Quick Actions */}
          <form onSubmit={handleSendMessage} className="p-3 border-t border-slate-800 space-y-2 bg-[#0d1321]">
            <div className="flex items-center gap-1.5">
              <button
                type="button"
                onClick={() => setBottomTab('explain')}
                className="px-2 py-1 rounded bg-violet-500/15 hover:bg-violet-500/25 text-violet-300 border border-violet-500/30 text-[11px] flex items-center gap-1"
              >
                <Sparkles className="w-3 h-3" />
                <span>Submit Formal Complexity</span>
              </button>
              <button
                type="button"
                onClick={handleRequestHint}
                disabled={requestingHint || activeIq.hintsUsed >= q.totalHintsAvailable}
                className="px-2 py-1 rounded bg-amber-500/15 hover:bg-amber-500/25 disabled:opacity-40 text-amber-300 border border-amber-500/30 text-[11px] flex items-center gap-1"
              >
                <HelpCircle className="w-3 h-3" />
                <span>Hint ({activeIq.hintsUsed}/{q.totalHintsAvailable})</span>
              </button>
            </div>

            <div className="flex items-center gap-2">
              <input
                type="text"
                value={chatInput}
                onChange={(e) => setChatInput(e.target.value)}
                placeholder="Discuss approach, invariants, or ask a question..."
                className="flex-1 px-3 py-2 rounded-lg bg-[#090d16] border border-slate-700/80 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
              />
              <button
                type="submit"
                disabled={sendingChat || !chatInput.trim()}
                className="p-2 rounded-lg bg-blue-600 hover:bg-blue-500 disabled:opacity-50 text-white transition-colors"
              >
                <Send className="w-4 h-4" />
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  );
};
