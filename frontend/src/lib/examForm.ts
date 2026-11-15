import { z } from "zod";
import type { components } from "@/api/schema/exam-analysis";

type Schemas = components["schemas"];
type CreateRequest = Schemas["MockExamCreate"];
type NationalItem = Schemas["NationalStatisticItem"];
type SettingUpdate = Schemas["WeakTopicSettingUpdate"];

export const SCORING_SOURCE_OPTIONS: { value: CreateRequest["scoringSource"]; label: string }[] = [
  { value: "INSTITUTIONAL", label: "Kurum içi (dershane öğrencilerinden hesaplanır)" },
  { value: "NATIONAL", label: "Türkiye geneli (yönetici istatistik girer)" },
];

export const mockExamFormSchema = z.object({
  examTypeId: z.string().min(1, "Sınav türü seçin."),
  name: z
    .string()
    .trim()
    .min(1, "Deneme adı gerekli.")
    .max(200, "Ad en çok 200 karakter olabilir."),
  publisher: z.string().trim().max(200, "Yayınevi en çok 200 karakter olabilir."),
  examDate: z.string().min(1, "Tarih gerekli."),
  scoringSource: z.enum(["INSTITUTIONAL", "NATIONAL"]),
});

export type MockExamFormValues = z.infer<typeof mockExamFormSchema>;

export function toCreateMockExam(v: MockExamFormValues, branchId: string): CreateRequest {
  return {
    examTypeId: v.examTypeId,
    name: v.name.trim(),
    publisher: v.publisher.trim() || undefined,
    examDate: v.examDate,
    branchId,
    scoringSource: v.scoringSource,
  };
}

// Admins type Turkish decimals ("12,5"); the API wants a dot.
const toDecimal = (text: string) => Number(text.trim().replace(",", "."));

const subjectRow = z.object({
  subjectId: z.string(),
  mean: z.string(),
  stdDev: z.string(),
  participantCount: z.string(),
});

// A row left completely empty is skipped; a partly filled one is an error. At least one row is needed.
export const nationalStatsSchema = z.object({ rows: z.array(subjectRow) }).superRefine((v, ctx) => {
  const filled = v.rows.filter(
    (r) => r.mean.trim() || r.stdDev.trim() || r.participantCount.trim(),
  );
  if (filled.length === 0) {
    ctx.addIssue({
      code: "custom",
      path: ["rows"],
      message: "En az bir ders için istatistik girin.",
    });
  }
  v.rows.forEach((row, i) => {
    if (!(row.mean.trim() || row.stdDev.trim() || row.participantCount.trim())) return;
    const fail = (field: string, message: string) =>
      ctx.addIssue({ code: "custom", path: ["rows", i, field], message });
    if (!Number.isFinite(toDecimal(row.mean)) || row.mean.trim() === "")
      fail("mean", "Ortalama gerekli.");
    const std = toDecimal(row.stdDev);
    if (row.stdDev.trim() === "" || !(std > 0))
      fail("stdDev", "Standart sapma 0'dan büyük olmalı.");
    if (!/^[1-9]\d*$/.test(row.participantCount.trim()))
      fail("participantCount", "Katılımcı sayısı pozitif tam sayı olmalı.");
  });
});

export type NationalStatsValues = z.infer<typeof nationalStatsSchema>;

export function toNationalItems(v: NationalStatsValues): NationalItem[] {
  return v.rows
    .filter((r) => r.mean.trim() || r.stdDev.trim() || r.participantCount.trim())
    .map((r) => ({
      subjectId: r.subjectId,
      mean: toDecimal(r.mean),
      stdDev: toDecimal(r.stdDev),
      participantCount: Number(r.participantCount),
    }));
}

// The threshold is typed as a percentage ("60") and sent as a ratio (0.6).
export const weakTopicSettingSchema = z.object({
  windowSize: z
    .string()
    .regex(/^\d+$/, "Deneme sayısı tam sayı olmalı.")
    .refine((v) => Number(v) >= 1 && Number(v) <= 50, "Deneme sayısı 1 ile 50 arasında olmalı."),
  thresholdPercent: z
    .string()
    .regex(/^\d{1,3}([.,]\d{1,2})?$/, "Eşik yüzde olarak girilmeli (ör. 60).")
    .refine((v) => toDecimal(v) > 0 && toDecimal(v) <= 100, "Eşik %0 ile %100 arasında olmalı."),
});

export type WeakTopicSettingValues = z.infer<typeof weakTopicSettingSchema>;

export function toSettingUpdate(v: WeakTopicSettingValues): SettingUpdate {
  return {
    windowSize: Number(v.windowSize),
    successThreshold: Number((toDecimal(v.thresholdPercent) / 100).toFixed(4)),
  };
}

// API ratio ("0.6000") -> input text ("60").
export function thresholdToPercent(ratio: string | number): string {
  return String(Math.round(Number(ratio) * 10000) / 100);
}
