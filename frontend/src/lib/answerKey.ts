import type { components } from "@/api/schema/exam-analysis";

type Item = components["schemas"]["AnswerKeyItemIn"];

export interface KeySubject {
  id: string;
  code: string;
  topics: { id: string; name: string }[];
}

export interface ParsedKey {
  items: Item[];
  errors: string[];
}

const norm = (text: string) => text.trim().toLocaleLowerCase("tr-TR");

// One question per line: booklet, question no, subject code, topic name, correct answer.
// Separator is a tab, ";" or ",", whichever the text uses first in that order; a header line is skipped.
export function parseAnswerKey(text: string, subjects: KeySubject[]): ParsedKey {
  const bySubject = new Map(subjects.map((s) => [norm(s.code), s]));
  const items: Item[] = [];
  const errors: string[] = [];
  const lines = text.split(/\r?\n/).map((line, i) => ({ line: line.trim(), no: i + 1 }));
  const separator = ["\t", ";", ","].find((s) => text.includes(s)) ?? ",";

  for (const { line, no } of lines) {
    if (!line) continue;
    const cells = line.split(separator).map((c) => c.trim());
    if (no === 1 && !/^\d+$/.test(cells[1] ?? "")) continue; // header row
    if (cells.length !== 5) {
      errors.push(`Satır ${no}: 5 sütun olmalı (kitapçık, soru no, ders kodu, konu, cevap).`);
      continue;
    }
    const [booklet, questionNo, subjectCode, topicName, answer] = cells;
    const subject = bySubject.get(norm(subjectCode));
    const topic = subject?.topics.find((t) => norm(t.name) === norm(topicName));
    const problem = !/^[A-Za-z]$/.test(booklet)
      ? "kitapçık tek harf olmalı"
      : !/^[1-9]\d{0,2}$/.test(questionNo)
        ? "soru numarası geçersiz"
        : !subject
          ? `bilinmeyen ders kodu "${subjectCode}"`
          : !topic
            ? `"${subjectCode}" dersinde "${topicName}" konusu yok`
            : !/^[A-Ea-e]$/.test(answer)
              ? "cevap A-E arası bir harf olmalı"
              : undefined;
    if (problem || !subject || !topic) {
      errors.push(`Satır ${no}: ${problem}.`);
      continue;
    }
    items.push({
      booklet: booklet.toUpperCase(),
      questionNo: Number(questionNo),
      subjectId: subject.id,
      topicId: topic.id,
      correctAnswer: answer.toUpperCase(),
    });
  }
  return { items, errors };
}
