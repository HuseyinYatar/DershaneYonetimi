import { describe, expect, it } from "vitest";
import { parseAnswerKey, type KeySubject } from "./answerKey";
import {
  mockExamFormSchema,
  nationalStatsSchema,
  thresholdToPercent,
  toCreateMockExam,
  toNationalItems,
  toSettingUpdate,
  weakTopicSettingSchema,
} from "./examForm";

const subjects: KeySubject[] = [
  { id: "s-tur", code: "TUR", topics: [{ id: "t-par", name: "Paragraf" }] },
  { id: "s-mat", code: "MAT", topics: [{ id: "t-fon", name: "Fonksiyonlar" }] },
];

describe("parseAnswerKey", () => {
  it("maps subject codes and topic names to ids and skips a header", () => {
    const text = "kitapçık;soru;ders;konu;cevap\nA;1;tur;paragraf;b\nA;2;MAT;Fonksiyonlar;E";
    expect(parseAnswerKey(text, subjects)).toEqual({
      errors: [],
      items: [
        { booklet: "A", questionNo: 1, subjectId: "s-tur", topicId: "t-par", correctAnswer: "B" },
        { booklet: "A", questionNo: 2, subjectId: "s-mat", topicId: "t-fon", correctAnswer: "E" },
      ],
    });
  });

  it("reports each bad line with its number and keeps the good ones", () => {
    const text = "A,1,TUR,Paragraf,C\nA,2,XXX,Paragraf,C\nA,3,TUR,Yok,C\nA,4,TUR,Paragraf,F\nA,5";
    const { items, errors } = parseAnswerKey(text, subjects);
    expect(items).toHaveLength(1);
    expect(errors).toEqual([
      'Satır 2: bilinmeyen ders kodu "XXX".',
      'Satır 3: "TUR" dersinde "Yok" konusu yok.',
      "Satır 4: cevap A-E arası bir harf olmalı.",
      "Satır 5: 5 sütun olmalı (kitapçık, soru no, ders kodu, konu, cevap).",
    ]);
  });

  it("accepts tab separated text", () => {
    expect(parseAnswerKey("B\t10\tMAT\tFonksiyonlar\tA", subjects).items).toHaveLength(1);
  });
});

describe("mock exam form", () => {
  it("requires type, name and date", () => {
    const result = mockExamFormSchema.safeParse({
      examTypeId: "",
      name: " ",
      publisher: "",
      examDate: "",
      scoringSource: "INSTITUTIONAL",
    });
    expect(result.error?.issues).toHaveLength(3);
  });

  it("drops an empty publisher", () => {
    const request = toCreateMockExam(
      {
        examTypeId: "t",
        name: " TYT-1 ",
        publisher: " ",
        examDate: "2026-11-20",
        scoringSource: "NATIONAL",
      },
      "b-1",
    );
    expect(request.publisher).toBeUndefined();
    expect(request.name).toBe("TYT-1");
  });
});

describe("national statistics form", () => {
  const row = (subjectId: string, mean = "", stdDev = "", participantCount = "") => ({
    subjectId,
    mean,
    stdDev,
    participantCount,
  });

  it("needs at least one filled row", () => {
    expect(nationalStatsSchema.safeParse({ rows: [row("a"), row("b")] }).success).toBe(false);
  });

  it("flags a partly filled row and accepts decimal commas", () => {
    const bad = nationalStatsSchema.safeParse({ rows: [row("a", "12,5", "0", "x")] });
    expect(bad.error?.issues.map((i) => i.path.join("."))).toEqual([
      "rows.0.stdDev",
      "rows.0.participantCount",
    ]);
    const ok = { rows: [row("a", "12,5", "4,25", "1500"), row("b")] };
    expect(nationalStatsSchema.safeParse(ok).success).toBe(true);
    expect(toNationalItems(ok)).toEqual([
      { subjectId: "a", mean: 12.5, stdDev: 4.25, participantCount: 1500 },
    ]);
  });
});

describe("weak topic setting form", () => {
  it("converts a percentage to a ratio and back", () => {
    expect(toSettingUpdate({ windowSize: "3", thresholdPercent: "52,5" })).toEqual({
      windowSize: 3,
      successThreshold: 0.525,
    });
    expect(thresholdToPercent("0.6000")).toBe("60");
  });

  it("rejects out-of-range values", () => {
    expect(
      weakTopicSettingSchema.safeParse({ windowSize: "0", thresholdPercent: "60" }).success,
    ).toBe(false);
    expect(
      weakTopicSettingSchema.safeParse({ windowSize: "3", thresholdPercent: "101" }).success,
    ).toBe(false);
    expect(
      weakTopicSettingSchema.safeParse({ windowSize: "3", thresholdPercent: "60" }).success,
    ).toBe(true);
  });
});
