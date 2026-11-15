import { cleanup, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ResultDetailPage } from "./ResultDetailPage";

const mocks = vi.hoisted(() => ({
  useStudentResult: vi.fn(),
  useWeakTopics: vi.fn(),
  useExamSubjectNames: vi.fn(),
  useSubjectTopicIds: vi.fn(),
}));
vi.mock("@/api/queries/exam", () => mocks);
// The header's bell and avatar load their own data; they are not under test here.
vi.mock("@/components/layout/HeaderActions", () => ({ HeaderActions: () => null }));

const subject = {
  subjectId: "s1",
  subjectName: "Matematik",
  correct: 24,
  wrong: 8,
  blank: 8,
  net: "22.00",
};

function detail(scores: unknown[]) {
  return {
    mockExamId: "m1",
    examName: "TYT Deneme 3",
    examTypeId: "t1",
    examDate: "2026-11-08",
    net: "22.00",
    branchRank: null,
    booklet: "A",
    correct: 24,
    wrong: 8,
    blank: 8,
    scores,
    subjects: [subject],
    topics: [
      {
        topicId: "p1",
        topicName: "Üslü sayılar",
        questionCount: 6,
        correct: 2,
        successRate: "0.3300",
      },
    ],
  };
}

const institutional = {
  scoreTypeId: "st1",
  scoreTypeCode: "TYT",
  estimatedScore: "391.40",
  isEstimated: true,
  source: "INSTITUTIONAL",
  branchRank: 7,
};
const pendingNational = {
  scoreTypeId: "st2",
  scoreTypeCode: "SAY",
  estimatedScore: null,
  isEstimated: true,
  source: null,
  branchRank: null,
};

function renderPage(scores: unknown[]) {
  mocks.useStudentResult.mockReturnValue({ data: detail(scores), isError: false });
  render(
    <MemoryRouter initialEntries={["/students/u1/results/m1"]}>
      <Routes>
        <Route path="/students/:studentId/results/:mockExamId" element={<ResultDetailPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  mocks.useWeakTopics.mockReturnValue({ data: { windowSize: 3, weakTopics: [] } });
  mocks.useExamSubjectNames.mockReturnValue(new Map());
  mocks.useSubjectTopicIds.mockReturnValue({
    bySubject: new Map([["s1", new Set(["p1"])]]),
    isPending: false,
  });
});
afterEach(cleanup);

describe("ResultDetailPage", () => {
  it("labels an estimated score with its source", () => {
    renderPage([institutional]);
    expect(screen.getByText("391,4")).toBeTruthy();
    expect(screen.getAllByText("tahmini (kurum içi)").length).toBeGreaterThan(0);
    expect(screen.queryByText("Türkiye geneli istatistik henüz girilmedi")).toBeNull();
  });

  it("explains a missing national score instead of showing a number", () => {
    renderPage([pendingNational]);
    expect(screen.getByText("Türkiye geneli istatistik henüz girilmedi")).toBeTruthy();
    expect(screen.queryByText(/tahmini/)).toBeNull();
  });

  it("shows the totals without spelling out the net formula", () => {
    renderPage([institutional]);
    expect(screen.queryByText(/Net = Doğru/)).toBeNull();
    expect(screen.getByText("Matematik toplam")).toBeTruthy();
  });
});
