import React, { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  AlertCircle,
  AlertTriangle,
  ArrowLeft,
  Award,
  Briefcase,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  Clock,
  Download,
  ExternalLink,
  FileCheck,
  FileSearch,
  FileText,
  Filter,
  HelpCircle,
  History,
  Info,
  Loader2,
  PlusCircle,
  RefreshCw,
  Search,
  ShieldAlert,
  Sparkles,
  Target,
  Trash2,
  Upload,
  X,
  XCircle,
} from 'lucide-react';
import { api } from '../services/api';
import type {
  AiSystemStatus,
  ResumeAnalysisHistoryItem,
  ResumeAnalysisReport,
  ResumeDimensionScore,
} from '../types/api';

const MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
const MIN_JD_CHARS = 30;
const MAX_JD_CHARS = 25000;

function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—';
  try {
    return new Date(iso).toLocaleString(undefined, {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return iso;
  }
}

function getScoreBadge(score: number | null) {
  if (score === null || score === undefined) {
    return {
      label: 'N/A',
      color: 'bg-slate-100 text-slate-700 border-slate-300 dark:bg-slate-800/70 dark:text-slate-300 dark:border-slate-700',
      ring: 'text-slate-600 dark:text-slate-400',
    };
  }
  if (score >= 85) {
    return {
      label: 'Strong Match',
      color: 'bg-emerald-50 text-emerald-700 border-emerald-200 dark:bg-emerald-500/15 dark:text-emerald-400 dark:border-emerald-500/30',
      ring: 'text-emerald-600 dark:text-emerald-400',
    };
  }
  if (score >= 70) {
    return {
      label: 'Good Match',
      color: 'bg-cyan-50 text-cyan-700 border-cyan-200 dark:bg-cyan-500/15 dark:text-cyan-400 dark:border-cyan-500/30',
      ring: 'text-cyan-600 dark:text-cyan-400',
    };
  }
  if (score >= 55) {
    return {
      label: 'Moderate Match',
      color: 'bg-amber-50 text-amber-700 border-amber-200 dark:bg-amber-500/15 dark:text-amber-400 dark:border-amber-500/30',
      ring: 'text-amber-600 dark:text-amber-400',
    };
  }
  if (score >= 40) {
    return {
      label: 'Partial Match',
      color: 'bg-orange-50 text-orange-700 border-orange-200 dark:bg-orange-500/15 dark:text-orange-400 dark:border-orange-500/30',
      ring: 'text-orange-600 dark:text-orange-400',
    };
  }
  return {
    label: 'Weak Match',
    color: 'bg-rose-50 text-rose-700 border-rose-200 dark:bg-rose-500/15 dark:text-rose-400 dark:border-rose-500/30',
    ring: 'text-rose-600 dark:text-rose-400',
  };
}

export const ResumeScreenerPage: React.FC = () => {
  const { id: routeAnalysisId } = useParams<{ id?: string }>();
  const location = useLocation();
  const navigate = useNavigate();

  const isHistoryRoute = location.pathname.endsWith('/history');

  const [activeTab, setActiveTab] = useState<'new' | 'report' | 'history'>(
    routeAnalysisId ? 'report' : isHistoryRoute ? 'history' : 'new'
  );

  // System AI status
  const [systemStatus, setSystemStatus] = useState<AiSystemStatus | null>(null);

  // Stage A form state
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const [jobTitle, setJobTitle] = useState('');
  const [jobDescription, setJobDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<{
    message: string;
    category?: string;
    guidance?: string;
  } | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  // Stage B active report state
  const [activeReport, setActiveReport] = useState<ResumeAnalysisReport | null>(null);
  const [loadingReport, setLoadingReport] = useState(false);
  const [reportError, setReportError] = useState<string | null>(null);

  // History state
  const [historyItems, setHistoryItems] = useState<ResumeAnalysisHistoryItem[]>([]);
  const [historyTotalElements, setHistoryTotalElements] = useState(0);
  const [historyTotalPages, setHistoryTotalPages] = useState(0);
  const [historyPage, setHistoryPage] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'COMPLETED' | 'PROCESSING' | 'FAILED'>(
    'ALL'
  );
  const [scoreFilter, setScoreFilter] = useState<'ALL' | 'HIGH' | 'MEDIUM' | 'LOW'>('ALL');
  const [sortOrder, setSortOrder] = useState<'NEWEST' | 'OLDEST' | 'HIGHEST_SCORE' | 'LOWEST_SCORE'>(
    'NEWEST'
  );

  // Delete modal state
  const [deleteTarget, setDeleteTarget] = useState<ResumeAnalysisHistoryItem | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    api.dashboard
      .getSystemStatus()
      .then((res) => setSystemStatus(res.ai))
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (routeAnalysisId) {
      const numericId = Number(routeAnalysisId);
      if (!Number.isNaN(numericId)) {
        setActiveTab('report');
        loadSavedReport(numericId);
      }
    } else if (isHistoryRoute) {
      setActiveTab('history');
    }
  }, [routeAnalysisId, isHistoryRoute]);

  const fetchHistory = async (pageToLoad = historyPage) => {
    setHistoryLoading(true);
    setHistoryError(null);
    try {
      let minScore: number | undefined;
      let maxScore: number | undefined;
      if (scoreFilter === 'HIGH') {
        minScore = 80;
      } else if (scoreFilter === 'MEDIUM') {
        minScore = 60;
        maxScore = 79;
      } else if (scoreFilter === 'LOW') {
        minScore = 0;
        maxScore = 59;
      }

      const pageData = await api.resumeAnalyses.list({
        search: searchQuery.trim() || undefined,
        status: statusFilter === 'ALL' ? undefined : statusFilter,
        minScore,
        maxScore,
        sortBy: sortOrder,
        page: pageToLoad,
        size: 10,
      });
      setHistoryItems(pageData.items);
      setHistoryTotalElements(pageData.totalElements);
      setHistoryTotalPages(pageData.totalPages);
      setHistoryPage(pageData.page);
    } catch (err: any) {
      setHistoryError(
        err.response?.data?.message || err?.message || 'Failed to load screening history.'
      );
    } finally {
      setHistoryLoading(false);
    }
  };

  useEffect(() => {
    fetchHistory(0);
  }, [searchQuery, statusFilter, scoreFilter, sortOrder]);

  const loadSavedReport = async (id: number) => {
    setLoadingReport(true);
    setReportError(null);
    try {
      const report = await api.resumeAnalyses.getById(id);
      setActiveReport(report);
      setActiveTab('report');
    } catch (err: any) {
      setReportError(
        err.response?.data?.message ||
          err?.message ||
          'Could not load the requested resume analysis report.'
      );
    } finally {
      setLoadingReport(false);
    }
  };

  const validateAndSelectFile = (file: File | null) => {
    setFileError(null);
    setSubmitError(null);
    if (!file) {
      setSelectedFile(null);
      return;
    }
    const lower = file.name.toLowerCase();
    if (!lower.endsWith('.pdf') && !lower.endsWith('.docx')) {
      setFileError('Unsupported file format. Please upload a PDF (.pdf) or Word (.docx) resume.');
      setSelectedFile(null);
      return;
    }
    if (file.size === 0) {
      setFileError('The selected file is empty. Please choose a valid PDF or DOCX resume.');
      setSelectedFile(null);
      return;
    }
    if (file.size > MAX_FILE_SIZE_BYTES) {
      setFileError(
        `File exceeds maximum size of 5 MB (selected file is ${formatBytes(file.size)}).`
      );
      setSelectedFile(null);
      return;
    }
    setSelectedFile(file);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0] || null;
    validateAndSelectFile(file);
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    const file = e.dataTransfer.files?.[0] || null;
    validateAndSelectFile(file);
  };

  const trimmedJd = jobDescription.trim();
  const isJdValid = trimmedJd.length >= MIN_JD_CHARS && trimmedJd.length <= MAX_JD_CHARS;
  const canSubmit = Boolean(selectedFile && !fileError && isJdValid && !submitting);

  const handleAnalyzeSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile || !isJdValid) return;

    setSubmitting(true);
    setSubmitError(null);
    try {
      const report = await api.resumeAnalyses.create({
        file: selectedFile,
        jobTitle: jobTitle.trim() || undefined,
        jobDescription: trimmedJd,
      });
      setActiveReport(report);
      setActiveTab('report');
      fetchHistory(0);
      navigate(`/resume-screener/${report.id}`, { replace: true });
    } catch (err: any) {
      const data = err.response?.data;
      setSubmitError({
        message: data?.message || err?.message || 'Resume analysis failed.',
        category: data?.errorCategory || err?.errorCategory,
        guidance: data?.troubleshootingRecommendation || data?.guidance,
      });
      fetchHistory(0);
    } finally {
      setSubmitting(false);
    }
  };

  const handleStartNewAnalysis = () => {
    setActiveReport(null);
    setReportError(null);
    setSubmitError(null);
    setActiveTab('new');
    if (location.pathname !== '/resume-screener') {
      navigate('/resume-screener');
    }
  };

  const handleOpenHistoryTab = () => {
    setActiveTab('history');
    fetchHistory(historyPage);
    if (!location.pathname.endsWith('/history')) {
      navigate('/resume-screener/history');
    }
  };

  const handleConfirmDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      await api.resumeAnalyses.deleteById(deleteTarget.id);
      if (activeReport && activeReport.id === deleteTarget.id) {
        setActiveReport(null);
        if (activeTab === 'report') {
          setActiveTab('history');
          navigate('/resume-screener/history');
        }
      }
      setDeleteTarget(null);
      await fetchHistory(0);
    } catch (err: any) {
      setHistoryError(
        err.response?.data?.message || err?.message || 'Failed to delete resume analysis.'
      );
      setDeleteTarget(null);
    } finally {
      setDeleting(false);
    }
  };

  const handleExportReportJson = () => {
    if (!activeReport) return;
    const blob = new Blob([JSON.stringify(activeReport, null, 2)], {
      type: 'application/json',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `resume-analysis-${activeReport.id}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const renderCategoryBars = (dimensions: ResumeDimensionScore[]) => {
    if (!dimensions || dimensions.length === 0) return null;
    const barColors = [
      'bg-indigo-500',
      'bg-cyan-500',
      'bg-emerald-500',
      'bg-amber-500',
      'bg-purple-500',
    ];

    return (
      <div className="space-y-4">
        {dimensions.map((dim, idx) => {
          const score = dim.score ?? 0;
          const maxScore = dim.maxScore || 10;
          const pct = Math.min(100, Math.max(0, Math.round((score / maxScore) * 100)));
          return (
            <div
              key={dim.dimensionKey || idx}
              className="p-4 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/90 dark:border-slate-800/80 space-y-2"
            >
              <div className="flex items-center justify-between text-sm">
                <span className="font-semibold text-slate-800 dark:text-slate-200">{dim.label}</span>
                <span className="font-mono font-bold text-slate-900 dark:text-slate-100">
                  {score} / {maxScore} pts ({pct}%)
                </span>
              </div>
              <div className="w-full h-2.5 bg-slate-200 dark:bg-slate-800 rounded-full overflow-hidden">
                <div
                  className={`h-full rounded-full transition-all duration-500 ${
                    barColors[idx % barColors.length]
                  }`}
                  style={{ width: `${pct}%` }}
                />
              </div>
              {dim.rationale && (
                <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed">{dim.rationale}</p>
              )}
            </div>
          );
        })}
      </div>
    );
  };

  const isAiConfigured = Boolean(systemStatus?.configured || systemStatus?.liveLlmConfigured);

  return (
    <div className="p-6 lg:p-8 max-w-7xl mx-auto space-y-6">
      {/* Page Header */}
      <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <div className="flex items-center gap-2.5">
            <div className="w-10 h-10 rounded-xl bg-indigo-50 dark:bg-indigo-500/10 border border-indigo-200 dark:border-indigo-500/30 flex items-center justify-center text-indigo-600 dark:text-indigo-400">
              <FileSearch className="w-5 h-5" />
            </div>
            <div>
              <h1 className="text-2xl font-bold text-slate-900 dark:text-white tracking-tight">
                AI Resume Screener
              </h1>
              <p className="text-sm text-slate-600 dark:text-slate-400">
                Evidence-based 100-point rubric evaluation against a target job description
              </p>
            </div>
          </div>
        </div>

        {/* Navigation Tabs */}
        <div className="flex items-center gap-2 bg-white dark:bg-slate-900/90 p-1.5 rounded-xl border border-slate-200 dark:border-slate-800 shadow-sm self-start">
          <button
            type="button"
            onClick={handleStartNewAnalysis}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-lg text-xs font-semibold transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 ${
              activeTab === 'new'
                ? 'bg-indigo-600 hover:bg-indigo-500 text-white shadow-sm'
                : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100 dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-800/60'
            }`}
          >
            <PlusCircle className="w-3.5 h-3.5" />
            New Screening
          </button>

          {activeReport && (
            <button
              type="button"
              onClick={() => setActiveTab('report')}
              className={`flex items-center gap-2 px-3.5 py-2 rounded-lg text-xs font-semibold transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 ${
                activeTab === 'report'
                  ? 'bg-indigo-600 hover:bg-indigo-500 text-white shadow-sm'
                  : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100 dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-800/60'
              }`}
            >
              <FileCheck className="w-3.5 h-3.5" />
              Active Report
            </button>
          )}

          <button
            type="button"
            onClick={handleOpenHistoryTab}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-lg text-xs font-semibold transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 ${
              activeTab === 'history'
                ? 'bg-indigo-600 hover:bg-indigo-500 text-white shadow-sm'
                : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100 dark:text-slate-400 dark:hover:text-slate-200 dark:hover:bg-slate-800/60'
            }`}
          >
            <History className="w-3.5 h-3.5" />
            Screening History
            <span
              className={`px-1.5 py-0.5 rounded-full text-[10px] ${
                activeTab === 'history'
                  ? 'bg-white/20 text-white'
                  : 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300'
              }`}
            >
              {historyTotalElements}
            </span>
          </button>
        </div>
      </div>

      {/* AI Connection Notice if not configured */}
      {systemStatus && !isAiConfigured && (
        <div className="p-4 rounded-xl bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/30 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-400 shrink-0 mt-0.5" />
            <div className="text-xs text-slate-700 dark:text-slate-300 space-y-1">
              <p className="font-semibold text-amber-800 dark:text-amber-300">
                Live AI Provider Required for New Resume Screenings
              </p>
              <p className="text-slate-600 dark:text-slate-400">
                AI Resume Screener never fabricates scores when the LLM is unconfigured. Configure{' '}
                <code className="text-amber-800 dark:text-amber-300 font-mono">AI_API_KEY</code> in your environment or
                test your provider in Profile &amp; Settings. You can still browse and reopen all
                previously saved reports in Screening History.
              </p>
            </div>
          </div>
          <Link
            to="/settings"
            className="px-3.5 py-2 rounded-lg bg-amber-100 hover:bg-amber-200 text-amber-900 border border-amber-300 dark:bg-amber-500/20 dark:hover:bg-amber-500/30 dark:text-amber-300 dark:border-amber-500/30 text-xs font-semibold whitespace-nowrap self-start sm:self-center transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-amber-500"
          >
            Configure AI Settings
          </Link>
        </div>
      )}

      {/* TAB 1: STAGE A — UPLOAD & JOB DESCRIPTION INPUT */}
      {activeTab === 'new' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <form onSubmit={handleAnalyzeSubmit} className="lg:col-span-2 space-y-6">
            {/* Step 1: Resume Upload Card */}
            <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <span className="w-6 h-6 rounded-full bg-indigo-100 text-indigo-700 dark:bg-indigo-500/20 dark:text-indigo-400 text-xs font-bold flex items-center justify-center">
                    1
                  </span>
                  <h2 className="text-base font-semibold text-slate-900 dark:text-white">Upload Candidate Resume</h2>
                </div>
                <span className="text-xs text-slate-600 dark:text-slate-400">PDF or DOCX • Max 5 MB</span>
              </div>

              <input
                ref={fileInputRef}
                type="file"
                id="resume-file-input"
                data-testid="resume-file-input"
                accept=".pdf,.docx,application/pdf,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                onChange={handleFileChange}
                className="hidden"
              />

              {!selectedFile ? (
                <div
                  role="button"
                  tabIndex={0}
                  onDragOver={(e) => e.preventDefault()}
                  onDrop={handleDrop}
                  onClick={() => fileInputRef.current?.click()}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      fileInputRef.current?.click();
                    }
                  }}
                  className="border-2 border-dashed border-slate-300 dark:border-slate-700 hover:border-indigo-500 dark:hover:border-indigo-500/60 rounded-xl p-8 text-center cursor-pointer transition-colors bg-slate-50/80 hover:bg-indigo-50/40 dark:bg-slate-950/40 dark:hover:bg-slate-950/70 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                >
                  <Upload className="w-9 h-9 text-indigo-600 dark:text-indigo-400 mx-auto mb-3" />
                  <p className="text-sm font-medium text-slate-800 dark:text-slate-200">
                    Click to select a resume file or drag and drop here
                  </p>
                  <p className="text-xs text-slate-600 dark:text-slate-400 mt-1">
                    Supports text-based <span className="text-slate-800 dark:text-slate-300 font-medium">.pdf</span> and{' '}
                    <span className="text-slate-800 dark:text-slate-300 font-medium">.docx</span> documents up to 5 MB
                  </p>
                </div>
              ) : (
                <div className="p-4 rounded-xl bg-slate-50 dark:bg-slate-950/80 border border-indigo-200 dark:border-indigo-500/30 flex items-center justify-between gap-4">
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-10 h-10 rounded-lg bg-indigo-50 border border-indigo-200 text-indigo-600 dark:bg-indigo-500/15 dark:border-indigo-500/30 dark:text-indigo-400 flex items-center justify-center shrink-0">
                      <FileText className="w-5 h-5" />
                    </div>
                    <div className="min-w-0">
                      <p className="text-sm font-semibold text-slate-900 dark:text-white truncate">
                        {selectedFile.name}
                      </p>
                      <p className="text-xs text-slate-600 dark:text-slate-400">
                        {formatBytes(selectedFile.size)} • Ready for text extraction
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-2 shrink-0">
                    <button
                      type="button"
                      onClick={() => fileInputRef.current?.click()}
                      className="px-3 py-1.5 rounded-lg bg-white hover:bg-slate-100 text-slate-700 border border-slate-300 dark:bg-slate-800 dark:hover:bg-slate-700 dark:text-slate-200 dark:border-slate-700 text-xs font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                    >
                      Replace
                    </button>
                    <button
                      type="button"
                      aria-label="Remove selected file"
                      onClick={() => {
                        setSelectedFile(null);
                        if (fileInputRef.current) fileInputRef.current.value = '';
                      }}
                      className="p-1.5 rounded-lg bg-rose-50 hover:bg-rose-100 text-rose-600 border border-rose-200 dark:bg-rose-500/10 dark:hover:bg-rose-500/20 dark:text-rose-400 dark:border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                    >
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              )}

              {fileError && (
                <div
                  role="alert"
                  className="p-3 rounded-lg bg-rose-50 dark:bg-rose-500/10 border border-rose-200 dark:border-rose-500/30 flex items-center gap-2.5 text-xs text-rose-700 dark:text-rose-300"
                >
                  <AlertCircle className="w-4 h-4 text-rose-600 dark:text-rose-400 shrink-0" />
                  <span>{fileError}</span>
                </div>
              )}
            </div>

            {/* Step 2: Job Description Input Card */}
            <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2.5">
                  <span className="w-6 h-6 rounded-full bg-indigo-100 text-indigo-700 dark:bg-indigo-500/20 dark:text-indigo-400 text-xs font-bold flex items-center justify-center">
                    2
                  </span>
                  <h2 className="text-base font-semibold text-slate-900 dark:text-white">Target Job Description</h2>
                </div>
                <span
                  className={`text-xs font-mono ${
                    trimmedJd.length === 0
                      ? 'text-slate-600 dark:text-slate-400'
                      : isJdValid
                      ? 'text-emerald-700 dark:text-emerald-400'
                      : 'text-amber-700 dark:text-amber-400'
                  }`}
                >
                  {trimmedJd.length} / {MIN_JD_CHARS}+ chars required
                </span>
              </div>

              <div>
                <label
                  htmlFor="job-title-input"
                  className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1.5"
                >
                  Job Title / Target Role{' '}
                  <span className="text-slate-500 dark:text-slate-400 font-normal">(Optional)</span>
                </label>
                <input
                  id="job-title-input"
                  type="text"
                  value={jobTitle}
                  onChange={(e) => setJobTitle(e.target.value)}
                  placeholder="e.g., Senior Backend Engineer — Distributed Systems"
                  maxLength={200}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-white dark:bg-slate-950 border border-slate-300 dark:border-slate-800 text-sm text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:border-indigo-600 dark:focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
                />
              </div>

              <div>
                <label
                  htmlFor="job-description-input"
                  className="block text-xs font-semibold text-slate-700 dark:text-slate-300 mb-1.5"
                >
                  Full Job Description <span className="text-rose-600 dark:text-rose-400">*</span>
                </label>
                <textarea
                  id="job-description-input"
                  rows={8}
                  value={jobDescription}
                  onChange={(e) => setJobDescription(e.target.value)}
                  placeholder="Paste the complete job description including required technical skills, experience expectations, system design responsibilities, and role qualifications..."
                  className="w-full px-3.5 py-3 rounded-xl bg-white dark:bg-slate-950 border border-slate-300 dark:border-slate-800 text-sm text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:border-indigo-600 dark:focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20 leading-relaxed"
                />
                {trimmedJd.length > 0 && trimmedJd.length < MIN_JD_CHARS && (
                  <p className="text-xs text-amber-700 dark:text-amber-400 mt-1.5">
                    Please provide at least {MIN_JD_CHARS} characters so the AI rubric can evaluate
                    specific role requirements ({MIN_JD_CHARS - trimmedJd.length} more characters
                    needed).
                  </p>
                )}
              </div>
            </div>

            {/* Submission Error Alert */}
            {submitError && (
              <div
                role="alert"
                className="p-4 rounded-xl bg-rose-50 dark:bg-rose-500/10 border border-rose-200 dark:border-rose-500/30 space-y-2"
              >
                <div className="flex items-start gap-2.5">
                  <XCircle className="w-5 h-5 text-rose-600 dark:text-rose-400 shrink-0 mt-0.5" />
                  <div className="space-y-1">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold text-rose-800 dark:text-rose-300">
                        {submitError.message}
                      </span>
                      {submitError.category && (
                        <span className="px-2 py-0.5 rounded text-[10px] font-mono bg-rose-100 text-rose-800 dark:bg-rose-500/20 dark:text-rose-300">
                          {submitError.category}
                        </span>
                      )}
                    </div>
                    {submitError.guidance && (
                      <p className="text-xs text-slate-700 dark:text-slate-300">{submitError.guidance}</p>
                    )}
                  </div>
                </div>
              </div>
            )}

            {/* Submit Controls */}
            <div className="p-5 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm flex flex-col sm:flex-row sm:items-center justify-between gap-4">
              <div className="flex items-start gap-2.5 text-xs text-slate-600 dark:text-slate-400">
                <Info className="w-4 h-4 text-indigo-600 dark:text-indigo-400 shrink-0 mt-0.5" />
                <span>
                  Advisory tool only: AI resume screening provides structured decision support and
                  does not automatically determine employment outcomes.
                </span>
              </div>

              <button
                type="submit"
                disabled={!canSubmit}
                data-testid="analyze-resume-btn"
                className="px-6 py-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 active:bg-indigo-700 disabled:bg-indigo-100 disabled:text-indigo-400 disabled:border disabled:border-indigo-200 dark:disabled:bg-slate-800 dark:disabled:text-slate-500 dark:disabled:border-slate-700 disabled:cursor-not-allowed text-white text-sm font-semibold transition-all flex items-center justify-center gap-2 shrink-0 shadow-lg shadow-indigo-600/20 disabled:shadow-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
              >
                {submitting ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" />
                    Analyzing Resume with AI...
                  </>
                ) : (
                  <>
                    <Sparkles className="w-4 h-4" />
                    Analyze Resume
                  </>
                )}
              </button>
            </div>
          </form>

          {/* Right Column: Rubric Guide & Recent Screenings Quick Access */}
          <div className="space-y-6">
            <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
              <div className="flex items-center gap-2 text-indigo-600 dark:text-indigo-400">
                <Award className="w-5 h-5" />
                <h3 className="text-sm font-bold text-slate-900 dark:text-white uppercase tracking-wider">
                  100-Point Scoring Rubric
                </h3>
              </div>
              <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed">
                Every resume is evaluated strictly against the provided job description across five
                weighted dimensions:
              </p>
              <div className="space-y-2.5 text-xs">
                {[
                  { name: 'Required Technical Skills', pts: '30 pts' },
                  { name: 'Relevant Experience', pts: '25 pts' },
                  { name: 'Projects & Accomplishments', pts: '20 pts' },
                  { name: 'Role Qualifications', pts: '15 pts' },
                  { name: 'Communication & Impact', pts: '10 pts' },
                ].map((item) => (
                  <div
                    key={item.name}
                    className="flex items-center justify-between p-2.5 rounded-lg bg-slate-50 dark:bg-slate-950/60 border border-slate-200/90 dark:border-slate-800/80"
                  >
                    <span className="text-slate-700 dark:text-slate-300 font-medium">{item.name}</span>
                    <span className="font-mono font-bold text-indigo-600 dark:text-indigo-400">{item.pts}</span>
                  </div>
                ))}
              </div>
              <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/70 border border-slate-200/90 dark:border-slate-800/80 text-[11px] text-slate-600 dark:text-slate-400 space-y-1">
                <p className="font-semibold text-slate-800 dark:text-slate-300">Fairness &amp; Evidence Guardrails</p>
                <p>
                  • Only skills and achievements supported by evidence in the resume receive credit.
                </p>
                <p>
                  • Protected personal attributes (age, gender, nationality, marital status, photo)
                  are never considered.
                </p>
                <p>• Embedded instructions inside resumes are treated as untrusted text.</p>
              </div>
            </div>

            {/* Recent History Preview Card */}
            <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <History className="w-4 h-4 text-indigo-600 dark:text-indigo-400" />
                  <h3 className="text-sm font-bold text-slate-900 dark:text-white">Recent Screenings</h3>
                </div>
                <button
                  type="button"
                  onClick={handleOpenHistoryTab}
                  className="text-xs text-indigo-600 hover:text-indigo-500 dark:text-indigo-400 dark:hover:text-indigo-300 font-medium rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                >
                  View All ({historyTotalElements})
                </button>
              </div>

              {historyItems.length === 0 ? (
                <p className="text-xs text-slate-600 dark:text-slate-400 py-4 text-center">
                  No past resume screenings yet. Your completed analyses will be saved here
                  automatically.
                </p>
              ) : (
                <div className="space-y-2.5">
                  {historyItems.slice(0, 4).map((item) => {
                    const badge = getScoreBadge(item.overallScore);
                    return (
                      <div
                        key={item.id}
                        className="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/90 dark:border-slate-800/80 flex items-center justify-between gap-3"
                      >
                        <div className="min-w-0">
                          <p className="text-xs font-semibold text-slate-900 dark:text-slate-200 truncate">
                            {item.originalFilename}
                          </p>
                          <p className="text-[11px] text-slate-600 dark:text-slate-400 truncate">
                            {item.jobTitle || item.jobDescriptionSummary}
                          </p>
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          {item.analysisStatus === 'COMPLETED' && item.overallScore !== null ? (
                            <span
                              className={`px-2 py-0.5 rounded text-xs font-bold border ${badge.color}`}
                            >
                              {item.overallScore}/100
                            </span>
                          ) : (
                            <span className="px-2 py-0.5 rounded text-[10px] font-semibold bg-rose-50 text-rose-700 border border-rose-200 dark:bg-rose-500/15 dark:text-rose-400 dark:border-rose-500/30">
                              {item.analysisStatus}
                            </span>
                          )}
                          <button
                            type="button"
                            onClick={() => navigate(`/resume-screener/${item.id}`)}
                            className="p-1.5 rounded-lg bg-white hover:bg-slate-100 text-slate-700 border border-slate-200 dark:bg-slate-800 dark:hover:bg-slate-700 dark:text-slate-200 dark:border-transparent text-xs transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                            title="Open saved report"
                          >
                            <ExternalLink className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* TAB 2: STAGE B — STRUCTURED ANALYSIS REPORT */}
      {activeTab === 'report' && (
        <div className="space-y-6">
          {loadingReport ? (
            <div className="p-12 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm text-center space-y-3">
              <Loader2 className="w-8 h-8 text-indigo-600 dark:text-indigo-400 animate-spin mx-auto" />
              <p className="text-sm text-slate-700 dark:text-slate-300 font-medium">
                Loading saved resume analysis report...
              </p>
            </div>
          ) : reportError ? (
            <div className="p-8 rounded-2xl bg-white dark:bg-slate-900/90 border border-rose-200 dark:border-rose-500/30 shadow-sm text-center space-y-4">
              <AlertCircle className="w-10 h-10 text-rose-600 dark:text-rose-400 mx-auto" />
              <div className="space-y-1">
                <h2 className="text-lg font-bold text-slate-900 dark:text-white">Report Unavailable</h2>
                <p className="text-sm text-slate-600 dark:text-slate-400">{reportError}</p>
              </div>
              <div className="flex items-center justify-center gap-3">
                <button
                  type="button"
                  onClick={handleOpenHistoryTab}
                  className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-300 dark:bg-slate-800 dark:hover:bg-slate-700 dark:text-slate-200 dark:border-transparent text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                >
                  Back to Screening History
                </button>
                <button
                  type="button"
                  onClick={handleStartNewAnalysis}
                  className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-xs font-semibold text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
                >
                  Start New Analysis
                </button>
              </div>
            </div>
          ) : activeReport ? (
            <>
              {/* Top Action Bar */}
              <div className="flex flex-wrap items-center justify-between gap-3 bg-white dark:bg-slate-900/90 p-4 rounded-xl border border-slate-200 dark:border-slate-800 shadow-sm">
                <div className="flex items-center gap-3">
                  <button
                    type="button"
                    onClick={handleOpenHistoryTab}
                    className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-300 dark:bg-slate-800 dark:hover:bg-slate-700 dark:text-slate-200 dark:border-transparent text-xs font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                  >
                    <ArrowLeft className="w-3.5 h-3.5" />
                    History
                  </button>
                  <div>
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-bold text-slate-900 dark:text-white">
                        {activeReport.originalFilename}
                      </span>
                      <span className="text-xs text-slate-600 dark:text-slate-400">
                        ({formatBytes(activeReport.fileSizeBytes)})
                      </span>
                    </div>
                    <p className="text-xs text-slate-600 dark:text-slate-400">
                      {activeReport.jobTitle || 'Target Job Description'} • Screened{' '}
                      {formatDate(activeReport.createdAt)} • Provider:{' '}
                      {activeReport.aiProvider || 'AI'} ({activeReport.aiModel || 'model'})
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    type="button"
                    onClick={handleExportReportJson}
                    className="flex items-center gap-1.5 px-3 py-2 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-300 dark:bg-slate-800 dark:hover:bg-slate-700 dark:text-slate-200 dark:border-transparent text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                  >
                    <Download className="w-3.5 h-3.5" />
                    Export JSON
                  </button>
                  <button
                    type="button"
                    onClick={handleStartNewAnalysis}
                    className="flex items-center gap-1.5 px-3.5 py-2 rounded-lg bg-indigo-600 hover:bg-indigo-500 text-xs font-semibold text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
                  >
                    <PlusCircle className="w-3.5 h-3.5" />
                    Analyze Another Resume
                  </button>
                </div>
              </div>

              {/* If FAILED record is viewed */}
              {activeReport.analysisStatus === 'FAILED' || !activeReport.result ? (
                <div className="p-8 rounded-2xl bg-white dark:bg-slate-900/90 border border-rose-200 dark:border-rose-500/30 shadow-sm space-y-4">
                  <div className="flex items-start gap-3">
                    <XCircle className="w-6 h-6 text-rose-600 dark:text-rose-400 shrink-0 mt-0.5" />
                    <div className="space-y-1">
                      <h2 className="text-base font-bold text-slate-900 dark:text-white">
                        This Screening Attempt Failed ({activeReport.errorCategory || 'ERROR'})
                      </h2>
                      <p className="text-sm text-rose-700 dark:text-rose-300">
                        {activeReport.errorMessage ||
                          'The AI provider did not return a valid structured evaluation.'}
                      </p>
                      <p className="text-xs text-slate-600 dark:text-slate-400">
                        No fabricated score was generated. Once your AI provider is verified in
                        Profile &amp; Settings, you can re-upload the resume for a live evaluation.
                      </p>
                    </div>
                  </div>
                </div>
              ) : (
                /* Full Report Content */
                <div className="space-y-6">
                  {/* Overall Match Summary Banner */}
                  {(() => {
                    const r = activeReport.result!;
                    const badge = getScoreBadge(r.overallScore);
                    return (
                      <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm grid grid-cols-1 lg:grid-cols-4 gap-6 items-center">
                        <div className="flex flex-col items-center justify-center p-5 rounded-2xl bg-slate-50 dark:bg-slate-950/80 border border-slate-200/90 dark:border-slate-800/80 text-center">
                          <span className="text-xs uppercase tracking-wider text-slate-600 dark:text-slate-400 font-semibold">
                            Overall Match Score
                          </span>
                          <div className={`text-5xl font-extrabold my-2 ${badge.ring}`}>
                            {r.overallScore}
                            <span className="text-lg text-slate-500 dark:text-slate-400 font-normal">/100</span>
                          </div>
                          <span
                            className={`px-3 py-1 rounded-full text-xs font-bold border ${badge.color}`}
                          >
                            {badge.label}
                          </span>
                        </div>

                        <div className="lg:col-span-3 space-y-4">
                          <div>
                            <h2 className="text-lg font-bold text-slate-900 dark:text-white mb-1">
                              Executive Evaluation Summary
                            </h2>
                            <p className="text-sm text-slate-700 dark:text-slate-300 leading-relaxed">{r.summary}</p>
                          </div>

                          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                            <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/90 dark:border-slate-800/80 space-y-1">
                              <span className="font-semibold text-indigo-600 dark:text-indigo-400 block">
                                Experience Alignment
                              </span>
                              <p className="text-slate-700 dark:text-slate-300">
                                {r.experienceAssessment?.summary || '—'}
                              </p>
                              {r.experienceAssessment?.seniorityAndScopeNote && (
                                <p className="text-slate-600 dark:text-slate-400 text-[11px]">
                                  {r.experienceAssessment.seniorityAndScopeNote}
                                </p>
                              )}
                            </div>
                            <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/90 dark:border-slate-800/80 space-y-1">
                              <span className="font-semibold text-cyan-700 dark:text-cyan-400 block">
                                Project &amp; Impact Depth
                              </span>
                              <p className="text-slate-700 dark:text-slate-300">
                                {r.projectAssessment?.summary || '—'}
                              </p>
                              {r.projectAssessment?.measurableImpactNote && (
                                <p className="text-slate-600 dark:text-slate-400 text-[11px]">
                                  {r.projectAssessment.measurableImpactNote}
                                </p>
                              )}
                            </div>
                          </div>

                          <div className="p-3 rounded-xl bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/20 flex items-center gap-2.5 text-xs text-amber-800 dark:text-amber-300">
                            <ShieldAlert className="w-4 h-4 shrink-0" />
                            <span>{r.disclaimer}</span>
                          </div>
                        </div>
                      </div>
                    );
                  })()}

                  {/* Rubric Breakdown + Technical Skills */}
                  <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
                      <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                        <Award className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
                        Rubric Category Breakdown
                      </h3>
                      {renderCategoryBars(
                        activeReport.dimensionScores?.length
                          ? activeReport.dimensionScores
                          : activeReport.result.dimensionScores || []
                      )}
                    </div>

                    <div className="space-y-6">
                      {/* Technical Skills Matrix */}
                      <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
                        <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                          <Target className="w-5 h-5 text-cyan-700 dark:text-cyan-400" />
                          Technical Skills Alignment
                        </h3>
                        <div className="space-y-2.5">
                          {activeReport.result.technicalSkills?.length ? (
                            activeReport.result.technicalSkills.map((ts, idx) => (
                              <div
                                key={idx}
                                className="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/90 dark:border-slate-800/80 text-xs flex items-start justify-between gap-3"
                              >
                                <div>
                                  <span className="font-bold text-slate-900 dark:text-white">{ts.skill}</span>
                                  <p className="text-slate-600 dark:text-slate-400 mt-0.5">{ts.resumeEvidence}</p>
                                </div>
                                <span
                                  className={`px-2 py-0.5 rounded text-[10px] font-bold shrink-0 ${
                                    ts.alignmentStatus === 'MATCHED'
                                      ? 'bg-emerald-100 text-emerald-800 dark:bg-emerald-500/20 dark:text-emerald-300'
                                      : ts.alignmentStatus === 'PARTIAL'
                                      ? 'bg-amber-100 text-amber-800 dark:bg-amber-500/20 dark:text-amber-300'
                                      : 'bg-rose-100 text-rose-800 dark:bg-rose-500/20 dark:text-rose-300'
                                  }`}
                                >
                                  {ts.alignmentStatus}
                                </span>
                              </div>
                            ))
                          ) : (
                            <p className="text-xs text-slate-600 dark:text-slate-400">
                              No technical skills matrix entries returned.
                            </p>
                          )}
                        </div>
                      </div>

                      {/* Requirement Match Matrix */}
                      <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
                        <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                          <Briefcase className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
                          Job Requirement Match Matrix
                        </h3>
                        <div className="space-y-3 text-xs">
                          <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/90 dark:border-slate-800/80">
                            <p className="font-semibold text-emerald-700 dark:text-emerald-400 mb-1.5">
                              Matched Requirements (
                              {activeReport.result.matchedRequirements?.length || 0})
                            </p>
                            <ul className="space-y-1.5 text-slate-700 dark:text-slate-300">
                              {activeReport.result.matchedRequirements?.map((req, idx) => (
                                <li key={idx}>
                                  <span className="font-semibold text-slate-900 dark:text-white">
                                    • {req.requirement}:
                                  </span>{' '}
                                  <span className="text-slate-600 dark:text-slate-400">{req.resumeEvidence}</span>
                                </li>
                              ))}
                            </ul>
                          </div>

                          <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/90 dark:border-slate-800/80">
                            <p className="font-semibold text-amber-700 dark:text-amber-400 mb-1.5">
                              Partially Matched Requirements (
                              {activeReport.result.partiallyMatchedRequirements?.length || 0})
                            </p>
                            <ul className="space-y-1.5 text-slate-700 dark:text-slate-300">
                              {activeReport.result.partiallyMatchedRequirements?.map((req, idx) => (
                                <li key={idx}>
                                  <span className="font-semibold text-slate-900 dark:text-white">
                                    • {req.requirement}:
                                  </span>{' '}
                                  <span className="text-slate-600 dark:text-slate-400">
                                    {req.resumeEvidence} (Missing: {req.missingDetail})
                                  </span>
                                </li>
                              ))}
                            </ul>
                          </div>

                          <div className="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/60 border border-slate-200/90 dark:border-slate-800/80">
                            <p className="font-semibold text-rose-700 dark:text-rose-400 mb-1.5">
                              Missing Requirements (
                              {activeReport.result.missingRequirements?.length || 0})
                            </p>
                            <ul className="space-y-1.5 text-slate-700 dark:text-slate-300">
                              {activeReport.result.missingRequirements?.map((req, idx) => (
                                <li key={idx}>
                                  <span className="font-semibold text-slate-900 dark:text-white">
                                    • {req.requirement}:
                                  </span>{' '}
                                  <span className="text-slate-600 dark:text-slate-400">{req.statusNote}</span>
                                </li>
                              ))}
                            </ul>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  {/* Strengths & Evidence vs Gaps & Risks */}
                  <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
                      <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                        <CheckCircle2 className="w-5 h-5 text-emerald-600 dark:text-emerald-400" />
                        Strengths &amp; Supporting Resume Evidence
                      </h3>
                      <div className="space-y-2.5">
                        {activeReport.result.strengths?.map((s, idx) => (
                          <div
                            key={idx}
                            className="p-3.5 rounded-xl bg-emerald-50 dark:bg-emerald-500/10 border border-emerald-200 dark:border-emerald-500/20 text-xs space-y-1"
                          >
                            <p className="font-bold text-emerald-800 dark:text-emerald-300">{s.title}</p>
                            <p className="text-slate-700 dark:text-slate-200 italic">“{s.resumeEvidence}”</p>
                            {s.relatedRequirement && (
                              <p className="text-[11px] text-slate-600 dark:text-slate-400">
                                Matches: {s.relatedRequirement}
                              </p>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>

                    <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
                      <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                        <AlertTriangle className="w-5 h-5 text-amber-600 dark:text-amber-400" />
                        Gaps, Weaknesses &amp; Hiring Risks
                      </h3>
                      <div className="space-y-2.5">
                        {activeReport.result.weaknesses?.map((w, idx) => (
                          <div
                            key={idx}
                            className="p-3.5 rounded-xl bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/20 text-xs space-y-1"
                          >
                            <p className="font-bold text-amber-800 dark:text-amber-300">{w.gap}</p>
                            <p className="text-slate-700 dark:text-slate-200">{w.detail}</p>
                            {w.actionableStep && (
                              <p className="text-[11px] text-indigo-700 dark:text-indigo-300">
                                Next step: {w.actionableStep}
                              </p>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>

                  {/* Actionable Recommendations, Suggested Edits & Interview Prep */}
                  <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    <div className="lg:col-span-2 p-6 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
                      <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                        <Sparkles className="w-5 h-5 text-indigo-600 dark:text-indigo-400" />
                        Actionable Resume Improvements
                      </h3>
                      <div className="space-y-3">
                        {activeReport.result.improvementRecommendations?.map((rec, idx) => (
                          <div
                            key={idx}
                            className="p-4 rounded-xl bg-slate-50 dark:bg-slate-950/70 border border-slate-200/90 dark:border-slate-800 space-y-1.5"
                          >
                            <div className="flex items-center justify-between">
                              <span className="text-xs font-bold text-slate-900 dark:text-white">
                                {rec.targetSection}
                              </span>
                              <span
                                className={`px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
                                  rec.priority?.toUpperCase() === 'HIGH'
                                    ? 'bg-rose-100 text-rose-800 dark:bg-rose-500/20 dark:text-rose-300'
                                    : rec.priority?.toUpperCase() === 'MEDIUM'
                                    ? 'bg-amber-100 text-amber-800 dark:bg-amber-500/20 dark:text-amber-300'
                                    : 'bg-cyan-100 text-cyan-800 dark:bg-cyan-500/20 dark:text-cyan-300'
                                }`}
                              >
                                {rec.priority || 'MEDIUM'} Priority
                              </span>
                            </div>
                            <p className="text-xs text-slate-700 dark:text-slate-200">{rec.recommendation}</p>
                            {rec.rationale && (
                              <p className="text-[11px] text-indigo-600 dark:text-indigo-400">
                                Rationale: {rec.rationale}
                              </p>
                            )}
                          </div>
                        ))}
                      </div>

                      {activeReport.result.suggestedResumeEdits?.length > 0 && (
                        <div className="pt-2 space-y-2">
                          <p className="text-xs font-semibold text-slate-700 dark:text-slate-300 uppercase tracking-wider">
                            Truthful Wording &amp; Structural Suggestions
                          </p>
                          <div className="space-y-2">
                            {activeReport.result.suggestedResumeEdits.map((edit, idx) => (
                              <div
                                key={idx}
                                className="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/70 border border-slate-200/90 dark:border-slate-800 text-xs space-y-1"
                              >
                                <span className="font-semibold text-indigo-600 dark:text-indigo-400">
                                  {edit.section}
                                </span>
                                <p className="text-slate-700 dark:text-slate-200">{edit.suggestedTruthfulWording}</p>
                                {edit.alignmentReason && (
                                  <p className="text-[11px] text-slate-600 dark:text-slate-400">
                                    {edit.alignmentReason}
                                  </p>
                                )}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>

                    <div className="p-6 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm space-y-4">
                      <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
                        <HelpCircle className="w-5 h-5 text-cyan-700 dark:text-cyan-400" />
                        Interview Focus Topics
                      </h3>
                      <p className="text-xs text-slate-600 dark:text-slate-400">
                        Recommended technical topics to probe or prepare for based on resume claims
                        and role gaps:
                      </p>
                      <div className="space-y-2.5 text-xs">
                        {activeReport.result.interviewPreparationTopics?.map((item, idx) => (
                          <div
                            key={idx}
                            className="p-3 rounded-xl bg-slate-50 dark:bg-slate-950/70 border border-slate-200/90 dark:border-slate-800 space-y-1"
                          >
                            <p className="font-bold text-slate-900 dark:text-white">{item.topic}</p>
                            <p className="text-slate-600 dark:text-slate-400">{item.reason}</p>
                            {item.sampleQuestion && (
                              <p className="text-cyan-700 dark:text-cyan-400 italic">Q: “{item.sampleQuestion}”</p>
                            )}
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </>
          ) : (
            <div className="p-12 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm text-center space-y-4">
              <FileSearch className="w-10 h-10 text-slate-500 mx-auto" />
              <p className="text-sm text-slate-700 dark:text-slate-300">No resume analysis report is currently open.</p>
              <button
                type="button"
                onClick={handleStartNewAnalysis}
                className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-xs font-semibold text-white transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
              >
                Start a New Screening
              </button>
            </div>
          )}
        </div>
      )}

      {/* TAB 3: PERSISTENT SCREENING HISTORY */}
      {activeTab === 'history' && (
        <div className="space-y-6">
          {/* Search & Filter Controls */}
          <div className="p-5 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm flex flex-col lg:flex-row gap-3 items-stretch lg:items-center justify-between">
            <div className="relative flex-1">
              <Search className="w-4 h-4 text-slate-500 dark:text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
              <input
                type="text"
                aria-label="Search screening history"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search by resume filename, job title, or role keywords..."
                className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-white dark:bg-slate-950 border border-slate-300 dark:border-slate-800 text-xs text-slate-900 dark:text-white placeholder-slate-400 dark:placeholder-slate-500 focus:outline-none focus:border-indigo-600 dark:focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              />
            </div>

            <div className="flex flex-wrap items-center gap-2.5">
              <div className="flex items-center gap-1.5 text-xs text-slate-600 dark:text-slate-400">
                <Filter className="w-3.5 h-3.5" />
                <span>Status:</span>
              </div>
              <select
                aria-label="Filter by status"
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value as any)}
                className="px-3 py-2 rounded-xl bg-white dark:bg-slate-950 border border-slate-300 dark:border-slate-800 text-xs text-slate-800 dark:text-slate-200 focus:outline-none focus:border-indigo-600 dark:focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              >
                <option value="ALL">All Statuses</option>
                <option value="COMPLETED">Completed</option>
                <option value="FAILED">Failed</option>
                <option value="PROCESSING">Processing</option>
              </select>

              <select
                aria-label="Filter by score range"
                value={scoreFilter}
                onChange={(e) => setScoreFilter(e.target.value as any)}
                className="px-3 py-2 rounded-xl bg-white dark:bg-slate-950 border border-slate-300 dark:border-slate-800 text-xs text-slate-800 dark:text-slate-200 focus:outline-none focus:border-indigo-600 dark:focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              >
                <option value="ALL">All Scores</option>
                <option value="HIGH">High Match (80–100)</option>
                <option value="MEDIUM">Moderate Match (60–79)</option>
                <option value="LOW">Needs Work (&lt;60)</option>
              </select>

              <select
                aria-label="Sort screening history"
                value={sortOrder}
                onChange={(e) => setSortOrder(e.target.value as any)}
                className="px-3 py-2 rounded-xl bg-white dark:bg-slate-950 border border-slate-300 dark:border-slate-800 text-xs text-slate-800 dark:text-slate-200 focus:outline-none focus:border-indigo-600 dark:focus:border-indigo-500 focus:ring-2 focus:ring-indigo-500/20"
              >
                <option value="NEWEST">Newest First</option>
                <option value="OLDEST">Oldest First</option>
                <option value="HIGHEST_SCORE">Highest Score</option>
                <option value="LOWEST_SCORE">Lowest Score</option>
              </select>

              <button
                type="button"
                onClick={() => fetchHistory(historyPage)}
                className="p-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-300 dark:bg-slate-800 dark:hover:bg-slate-700 dark:text-slate-200 dark:border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                title="Refresh history"
              >
                <RefreshCw className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* History List */}
          {historyLoading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((n) => (
                <div
                  key={n}
                  className="p-5 rounded-2xl bg-white dark:bg-slate-900/60 border border-slate-200 dark:border-slate-800 animate-pulse h-24"
                />
              ))}
            </div>
          ) : historyError ? (
            <div className="p-8 rounded-2xl bg-white dark:bg-slate-900/90 border border-rose-200 dark:border-rose-500/30 shadow-sm text-center space-y-3">
              <AlertCircle className="w-8 h-8 text-rose-600 dark:text-rose-400 mx-auto" />
              <p className="text-sm text-rose-700 dark:text-rose-300">{historyError}</p>
              <button
                type="button"
                onClick={() => fetchHistory(historyPage)}
                className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-800 border border-slate-300 dark:bg-slate-800 dark:hover:bg-slate-700 dark:text-white dark:border-transparent text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
              >
                Retry
              </button>
            </div>
          ) : historyItems.length === 0 ? (
            <div className="p-12 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 shadow-sm text-center space-y-4">
              <History className="w-10 h-10 text-slate-500 mx-auto" />
              <div className="space-y-1">
                <h3 className="text-base font-bold text-slate-900 dark:text-white">No Screening History Found</h3>
                <p className="text-xs text-slate-600 dark:text-slate-400 max-w-md mx-auto">
                  {searchQuery || statusFilter !== 'ALL' || scoreFilter !== 'ALL'
                    ? 'No saved resume analyses match your active search or filters.'
                    : 'Upload a PDF or DOCX resume and paste a job description to run your first AI rubric evaluation.'}
                </p>
              </div>
              <button
                type="button"
                onClick={handleStartNewAnalysis}
                className="px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-xs font-semibold text-white inline-flex items-center gap-2 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
              >
                <PlusCircle className="w-4 h-4" />
                Run First Screening
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {historyItems.map((item) => {
                const badge = getScoreBadge(item.overallScore);
                return (
                  <div
                    key={item.id}
                    data-testid={`history-item-${item.id}`}
                    className="p-5 rounded-2xl bg-white dark:bg-slate-900/90 border border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700 shadow-sm transition-all flex flex-col md:flex-row md:items-center justify-between gap-4"
                  >
                    <div className="space-y-1.5 min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2.5">
                        <span className="text-sm font-bold text-slate-900 dark:text-white truncate">
                          {item.originalFilename}
                        </span>
                        <span className="px-2 py-0.5 rounded text-[10px] font-mono uppercase bg-slate-100 text-slate-700 border border-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:border-transparent">
                          {formatBytes(item.fileSizeBytes)}
                        </span>
                        {item.analysisStatus === 'COMPLETED' && item.overallScore !== null ? (
                          <span
                            className={`px-2.5 py-0.5 rounded-full text-xs font-bold border ${badge.color}`}
                          >
                            {item.overallScore}/100 • {badge.label}
                          </span>
                        ) : (
                          <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-50 text-rose-700 border border-rose-200 dark:bg-rose-500/15 dark:text-rose-400 dark:border-rose-500/30">
                            {item.analysisStatus}
                          </span>
                        )}
                      </div>

                      <p className="text-xs font-medium text-indigo-600 dark:text-indigo-400">
                        {item.jobTitle || 'Target Role Evaluation'}
                      </p>
                      <p className="text-xs text-slate-600 dark:text-slate-400 line-clamp-2">
                        {item.jobDescriptionSummary}
                      </p>

                      <div className="flex flex-wrap items-center gap-4 text-[11px] text-slate-500 dark:text-slate-400 pt-1">
                        <span className="flex items-center gap-1">
                          <Clock className="w-3.5 h-3.5" />
                          {formatDate(item.createdAt)}
                        </span>
                        {item.aiProvider && (
                          <span>
                            Provider: {item.aiProvider} ({item.aiModel})
                          </span>
                        )}
                      </div>
                    </div>

                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        type="button"
                        data-testid={`view-report-${item.id}`}
                        onClick={() => navigate(`/resume-screener/${item.id}`)}
                        className="px-3.5 py-2 rounded-xl bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 dark:bg-indigo-600/20 dark:hover:bg-indigo-600/30 dark:text-indigo-300 dark:border-indigo-500/30 text-xs font-semibold flex items-center gap-1.5 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                      >
                        <FileCheck className="w-3.5 h-3.5" />
                        View Report
                      </button>
                      <button
                        type="button"
                        data-testid={`delete-report-${item.id}`}
                        aria-label={`Delete analysis for ${item.originalFilename}`}
                        onClick={() => setDeleteTarget(item)}
                        className="p-2 rounded-xl bg-rose-50 hover:bg-rose-100 text-rose-600 border border-rose-200 dark:bg-rose-500/10 dark:hover:bg-rose-500/20 dark:text-rose-400 dark:border-rose-500/20 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                );
              })}

              {/* Pagination Controls */}
              {historyTotalPages > 1 && (
                <div className="flex items-center justify-between pt-4">
                  <span className="text-xs text-slate-600 dark:text-slate-400">
                    Page {historyPage + 1} of {historyTotalPages} ({historyTotalElements} total
                    records)
                  </span>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      disabled={historyPage <= 0}
                      onClick={() => fetchHistory(historyPage - 1)}
                      className="px-3 py-1.5 rounded-lg bg-white hover:bg-slate-100 border border-slate-300 text-slate-700 dark:bg-slate-800 dark:hover:bg-slate-700 dark:border-transparent dark:text-slate-200 disabled:opacity-40 text-xs flex items-center gap-1 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                    >
                      <ChevronLeft className="w-3.5 h-3.5" />
                      Previous
                    </button>
                    <button
                      type="button"
                      disabled={historyPage + 1 >= historyTotalPages}
                      onClick={() => fetchHistory(historyPage + 1)}
                      className="px-3 py-1.5 rounded-lg bg-white hover:bg-slate-100 border border-slate-300 text-slate-700 dark:bg-slate-800 dark:hover:bg-slate-700 dark:border-transparent dark:text-slate-200 disabled:opacity-40 text-xs flex items-center gap-1 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
                    >
                      Next
                      <ChevronRight className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}

      {/* Delete Confirmation Modal */}
      {deleteTarget && (
        <div
          role="dialog"
          aria-modal="true"
          className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm flex items-center justify-center p-4"
        >
          <div className="max-w-md w-full p-6 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 space-y-4 shadow-2xl">
            <div className="flex items-start gap-3">
              <div className="w-10 h-10 rounded-xl bg-rose-50 dark:bg-rose-500/15 border border-rose-200 dark:border-rose-500/30 flex items-center justify-center text-rose-600 dark:text-rose-400 shrink-0">
                <Trash2 className="w-5 h-5" />
              </div>
              <div className="space-y-1">
                <h3 className="text-base font-bold text-slate-900 dark:text-white">Delete Saved Resume Analysis?</h3>
                <p className="text-xs text-slate-600 dark:text-slate-400 leading-relaxed">
                  Are you sure you want to permanently delete the screening report for{' '}
                  <span className="text-slate-900 dark:text-slate-200 font-semibold">
                    {deleteTarget.originalFilename}
                  </span>
                  ? This action cannot be undone.
                </p>
              </div>
            </div>
            <div className="flex items-center justify-end gap-2.5 pt-2">
              <button
                type="button"
                disabled={deleting}
                onClick={() => setDeleteTarget(null)}
                className="px-4 py-2 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 border border-slate-300 dark:bg-slate-800 dark:hover:bg-slate-700 dark:text-slate-200 dark:border-transparent text-xs font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500"
              >
                Cancel
              </button>
              <button
                type="button"
                data-testid="confirm-delete-btn"
                disabled={deleting}
                onClick={handleConfirmDelete}
                className="px-4 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-xs font-semibold text-white flex items-center gap-1.5 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-rose-500 focus-visible:ring-offset-2"
              >
                {deleting ? (
                  <>
                    <Loader2 className="w-3.5 h-3.5 animate-spin" />
                    Deleting...
                  </>
                ) : (
                  <>
                    <Trash2 className="w-3.5 h-3.5" />
                    Delete Permanently
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
