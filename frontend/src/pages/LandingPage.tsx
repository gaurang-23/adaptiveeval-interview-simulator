import React from 'react';
import { Link } from 'react-router-dom';
import {
  Activity,
  ArrowRight,
  BrainCircuit,
  Bug,
  CheckCircle2,
  Code2,
  Cpu,
  Gauge,
  MessageSquareCode,
  Shield,
  Terminal,
} from 'lucide-react';
import { useAuth } from '../features/auth/AuthContext';
import { ThemeToggle } from '../features/theme/ThemeContext';

export const LandingPage: React.FC = () => {
  const { user } = useAuth();

  const competencies = [
    {
      title: 'Pattern Recognition',
      icon: BrainCircuit,
      accent: 'text-blue-400 bg-blue-500/10 border-blue-500/30',
      desc: 'Distinguishes whether you identified the optimal sliding window, hash lookup, or DP recurrence versus defaulting to brute force.',
    },
    {
      title: 'Implementation Precision',
      icon: Code2,
      accent: 'text-teal-400 bg-teal-500/10 border-teal-500/30',
      desc: 'Evaluates Java syntax accuracy, collection idioms, index bounds, and hidden edge-case handling.',
    },
    {
      title: 'Complexity Analysis',
      icon: Gauge,
      accent: 'text-violet-400 bg-violet-500/10 border-violet-500/30',
      desc: 'Probes and verifies your asymptotic Time and Auxiliary Space complexity claims against structural code analysis.',
    },
    {
      title: 'Systematic Debugging',
      icon: Bug,
      accent: 'text-amber-400 bg-amber-500/10 border-amber-500/30',
      desc: 'Tracks how you isolate failing test cases, interpret compiler diagnostics, and test hypotheses across revisions.',
    },
    {
      title: 'Technical Communication',
      icon: MessageSquareCode,
      accent: 'text-emerald-400 bg-emerald-500/10 border-emerald-500/30',
      desc: 'Assesses how clearly you articulate loop invariants, correctness proofs, and design trade-offs to an interviewer.',
    },
  ];

  return (
    <div className="min-h-screen bg-[#090d16] text-slate-100 flex flex-col">
      {/* Header */}
      <header className="border-b border-slate-800/80 bg-[#0d1321]/80 backdrop-blur sticky top-0 z-20">
        <div className="max-w-7xl mx-auto px-6 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-lg bg-blue-600/20 border border-blue-500/40 flex items-center justify-center text-blue-400">
              <Terminal className="w-5 h-5" />
            </div>
            <div>
              <span className="font-semibold text-base tracking-tight text-white">
                Adaptive Technical Interview Simulator
              </span>
              <span className="hidden sm:inline-block ml-2.5 px-2 py-0.5 text-[11px] font-mono rounded bg-slate-800 text-slate-300 border border-slate-700">
                Java · Spring Boot · MySQL 8
              </span>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <ThemeToggle compact />
            {user ? (
              <Link
                to="/dashboard"
                className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium transition-colors flex items-center gap-2"
              >
                <span>Go to Dashboard</span>
                <ArrowRight className="w-4 h-4" />
              </Link>
            ) : (
              <>
                <Link
                  to="/login"
                  className="px-4 py-2 rounded-lg text-slate-300 hover:text-white hover:bg-slate-800/60 text-sm font-medium transition-colors"
                >
                  Sign In
                </Link>
                <Link
                  to="/register"
                  className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium transition-colors flex items-center gap-1.5"
                >
                  <span>Start Practicing</span>
                  <ArrowRight className="w-4 h-4" />
                </Link>
              </>
            )}
          </div>
        </div>
      </header>

      {/* Hero Section */}
      <main className="flex-1">
        <section className="max-w-7xl mx-auto px-6 py-16 lg:py-24 grid lg:grid-cols-12 gap-12 items-center">
          <div className="lg:col-span-7 space-y-6">
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-blue-500/10 border border-blue-500/30 text-blue-300 text-xs font-mono">
              <Activity className="w-3.5 h-3.5" />
              <span>Evidence-Based 5-Competency Diagnostic Engine</span>
            </div>

            <h1 className="text-4xl sm:text-5xl font-bold tracking-tight text-white leading-[1.15]">
              Go beyond pass/fail test cases.{' '}
              <span className="text-blue-400">Diagnose why you struggle</span> and adapt in real time.
            </h1>

            <p className="text-slate-300 text-base sm:text-lg leading-relaxed max-w-2xl">
              Traditional coding platforms only tell you whether your output matched. Our simulator separates{' '}
              <strong className="text-white font-medium">algorithmic pattern recognition</strong> from{' '}
              <strong className="text-white font-medium">implementation bugs</strong>,{' '}
              <strong className="text-white font-medium">complexity misconceptions</strong>,{' '}
              <strong className="text-white font-medium">debugging process</strong>, and{' '}
              <strong className="text-white font-medium">technical communication</strong>.
            </p>

            <div className="flex flex-wrap items-center gap-4 pt-2">
              <Link
                to={user ? '/interviews/new' : '/register'}
                className="px-6 py-3 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-medium text-sm transition-colors flex items-center gap-2 shadow-lg shadow-blue-950/50"
              >
                <span>{user ? 'Configure New Interview' : 'Create Free Account'}</span>
                <ArrowRight className="w-4 h-4" />
              </Link>
              <Link
                to={user ? '/dashboard' : '/login'}
                className="px-6 py-3 rounded-lg bg-[#111827] hover:bg-slate-800 text-slate-200 border border-slate-700 font-medium text-sm transition-colors"
              >
                {user ? 'View Skill Profile' : 'Sign In to Workspace'}
              </Link>
            </div>

            <div className="grid grid-cols-3 gap-4 pt-6 border-t border-slate-800/80 text-xs text-slate-400">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-teal-400 shrink-0" />
                <span>Isolated Java 21 Code Runner</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-blue-400 shrink-0" />
                <span>Bounded Adaptive Selection</span>
              </div>
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-4 h-4 text-violet-400 shrink-0" />
                <span>Live AI + Deterministic Rubric</span>
              </div>
            </div>
          </div>

          {/* Interactive Architecture / Diagnostic Preview Panel */}
          <div className="lg:col-span-5 bg-[#0d1321] border border-slate-800 rounded-xl p-5 shadow-2xl space-y-4">
            <div className="flex items-center justify-between border-b border-slate-800 pb-3">
              <div className="flex items-center gap-2">
                <span className="w-2.5 h-2.5 rounded-full bg-rose-500/80" />
                <span className="w-2.5 h-2.5 rounded-full bg-amber-500/80" />
                <span className="w-2.5 h-2.5 rounded-full bg-emerald-500/80" />
                <span className="ml-2 text-xs font-mono text-slate-400">
                  Live Diagnostic Telemetry · Solution.java
                </span>
              </div>
              <span className="px-2 py-0.5 text-[11px] font-mono rounded bg-teal-500/15 text-teal-300 border border-teal-500/30">
                5/5 Tests Passed
              </span>
            </div>

            <pre className="text-xs font-mono bg-[#090d16] p-3.5 rounded-lg border border-slate-800/90 text-slate-300 overflow-x-auto">
              <code>{`public class Solution {
  public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
      int comp = target - nums[i];
      if (seen.containsKey(comp)) {
        return new int[]{seen.get(comp), i};
      }
      seen.put(nums[i], i);
    }
    return new int[]{};
  }
}`}</code>
            </pre>

            <div className="space-y-2.5">
              <div className="p-3 rounded-lg bg-[#090d16] border border-slate-800/90 flex items-center justify-between text-xs">
                <div>
                  <span className="text-slate-400 block">Observed Fact</span>
                  <span className="text-slate-200 font-medium">
                    Single-pass HashMap · 5/5 tests in 11ms · 0 hints
                  </span>
                </div>
                <span className="font-mono text-emerald-400">DETERMINISTIC</span>
              </div>

              <div className="p-3 rounded-lg bg-[#090d16] border border-slate-800/90 space-y-1.5 text-xs">
                <div className="flex items-center justify-between">
                  <span className="text-slate-300 font-medium">Skill Vector Update (S_new)</span>
                  <span className="font-mono text-blue-400">s_new = α·r + (1-α)·s</span>
                </div>
                <div className="grid grid-cols-2 gap-2 pt-1 text-[11px] font-mono">
                  <div className="text-slate-400">
                    Pattern Rec: <span className="text-emerald-400">78.4 (+8.2)</span>
                  </div>
                  <div className="text-slate-400">
                    Implementation: <span className="text-emerald-400">84.1 (+9.5)</span>
                  </div>
                  <div className="text-slate-400">
                    Complexity: <span className="text-blue-400">81.0 (+6.4)</span>
                  </div>
                  <div className="text-slate-400">
                    Communication: <span className="text-violet-400">76.5 (+5.1)</span>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* Five Competencies Grid */}
        <section className="border-t border-slate-800/80 bg-[#0d1321]/50 py-16">
          <div className="max-w-7xl mx-auto px-6 space-y-10">
            <div className="max-w-2xl space-y-2">
              <h2 className="text-2xl font-bold text-white">
                Five Orthogonal Engineering Competencies
              </h2>
              <p className="text-sm text-slate-400">
                Every code execution, revision, progressive hint, and complexity explanation updates your multi-dimensional skill profile with explicit confidence bounds.
              </p>
            </div>

            <div className="grid sm:grid-cols-2 lg:grid-cols-5 gap-4">
              {competencies.map((c) => {
                const Icon = c.icon;
                return (
                  <div
                    key={c.title}
                    className="p-5 rounded-xl bg-[#0d1321] border border-slate-800/90 flex flex-col justify-between space-y-4"
                  >
                    <div className="space-y-3">
                      <div
                        className={`w-9 h-9 rounded-lg border flex items-center justify-center ${c.accent}`}
                      >
                        <Icon className="w-4 h-4" />
                      </div>
                      <h3 className="font-semibold text-sm text-white">{c.title}</h3>
                      <p className="text-xs text-slate-400 leading-relaxed">{c.desc}</p>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800/80 py-6 px-6 text-center text-xs text-slate-500">
        Adaptive Technical Interview Simulator · Built with React, TypeScript, Spring Boot, MySQL 8 & Isolated Java Execution Sandbox
      </footer>
    </div>
  );
};
