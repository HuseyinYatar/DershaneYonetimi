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

function detail(scores: unknown[], subjects: unknown[] = [subject]) {
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
    subjects,
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

function renderPage(scores: unknown[], subjects?: unknown[]) {
  mocks.useStudentResult.mockReturnValue({ data: detail(scores, subjects), isError: false });
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
    expect(screen.getByText("391,40")).toBeTruthy();
    expect(screen.getAllByText("tahmini (kurum içi)").length).toBeGreaterThan(0);
    expect(screen.queryByText("Türkiye geneli istatistik henüz girilmedi")).toBeNull();
  });

  it("labels a coefficient score with its year and the TYT it counted", () => {
    renderPage([
      {
        ...institutional,
        scoreTypeCode: "SAY",
        estimatedScore: "402.10",
        source: null,
        coefficientYear: 2026,
        linkedExamName: "Demo TYT Deneme 4",
      },
    ]);
    expect(screen.getByText("402,10")).toBeTruthy();
    expect(screen.getAllByText("tahmini (2026 katsayılarıyla)").length).toBeGreaterThan(0);
    expect(screen.getByText(/TYT netleri: Demo TYT Deneme 4/)).toBeTruthy();
  });

  it("lists where a coefficient score comes from", () => {
    renderPage([
      {
        ...institutional,
        scoreTypeCode: "EA",
        estimatedScore: "301.94",
        source: null,
        coefficientYear: 2026,
        linkedExamName: "Demo TYT Deneme 4",
        breakdown: [
          { label: "Sabit puan", points: "126.15" },
          { label: "TYT netleri (Demo TYT Deneme 4)", points: "80.57" },
          { label: "Matematik", points: "95.22" },
          { label: "Türk Dili ve Edebiyatı", points: "0.00" },
        ],
      },
    ]);
    const sources = screen.getByLabelText("EA puanının kaynağı");
    expect(sources.textContent).toContain("Sabit puan126,15");
    expect(sources.textContent).toContain("Matematik95,22");
    expect(sources.textContent).toContain("Türk Dili ve Edebiyatı0,00");
  });

  it("shows an untaken AYT test as not taken instead of zeros", () => {
    renderPage(
      [institutional],
      [
        subject,
        {
          subjectId: "s2",
          subjectName: "Türk Dili ve Edebiyatı",
          correct: 0,
          wrong: 0,
          blank: 0,
          net: "0",
          taken: false,
          questionCount: 24,
        },
      ],
    );
    expect(screen.getAllByText("Türk Dili ve Edebiyatı").length).toBeGreaterThan(0);
    expect(screen.getByText(/24 soru · girilmedi/)).toBeTruthy();
    expect(screen.getAllByText("Girilmedi").length).toBeGreaterThan(0);
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
