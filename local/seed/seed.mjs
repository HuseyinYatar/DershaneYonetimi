// Mock data for the LOCAL docker compose stack. Re-runnable: every step looks for what it would create.
//   node local/seed/seed.mjs            (Node 22+, no dependencies; stack started with --profile services)
// LOCAL ONLY: sets known passwords in the local Keycloak and touches the local databases through docker.
// Almost everything goes through the real APIs (gateway :8000) so events flow and replicas fill.
// The exceptions (see local/README.md "Mock veri"): Keycloak passwords (admin REST API), the exam catalog
// (CLI in the exam-analysis container), and moving two assignments' due time into the past with SQL so the
// real check-deadlines job marks them OVERDUE.
import { api, compose, listAll, log, login, psql, setPassword, sleep, waitFor } from './lib.mjs';

const PASSWORD = 'Demo1234!'; // known local password of every seeded account (not the temp-password student)
const TEMP_PASSWORD = 'Gecici123!'; // temporary: Keycloak asks for a new one at first login
const ADMIN = { user: 'superadmin@dershane.local', password: 'admin' };
const TEACHER_EMAIL = 'ogretmen@dershane.local';
const CLASSROOM = 'Demo 12-A'; // marker of the seed
const MEMBER_SINCE = '2026-09-01';

const STUDENTS = [
  ['1001', 'Ayşe', 'Yılmaz'],
  ['1002', 'Mehmet', 'Kaya'],
  ['1003', 'Elif', 'Demir'],
  ['1004', 'Can', 'Öztürk'],
  ['1005', 'Zeynep', 'Arslan'],
  ['1006', 'Burak', 'Şahin'],
  ['1007', 'Selin', 'Çelik'],
  ['1008', 'Emre', 'Yıldız'],
  ['1009', 'Deniz', 'Aydın'],
  ['1010', 'Merve', 'Koç'], // keeps a temporary password
];
const TEMP_STUDENT_NO = '1010';
const MAIN_STUDENT_NO = '1001';
const GUARDIAN = { firstName: 'Fatma', lastName: 'Yılmaz', phone: '05551234567' };

// --- small utilities ------------------------------------------------------------------------------
const pad = (n) => String(n).padStart(2, '0');
const ymd = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const addDays = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
const today = addDays(new Date(), 0);
const DAYS = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];

/** Deterministic PRNG so every run produces the same exams. */
function mulberry32(seed) {
  let a = seed;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const step = (name) => log(`\n== ${name}`);

// --- 1. branch, subjects, classroom, people ---------------------------------------------------------
async function seedPeople(admin) {
  step('Branch, subjects, classroom');
  const branches = await listAll(admin, '/branches');
  let branch = branches.find((b) => b.code === 'MRKZ') ?? branches[0];
  if (!branch) {
    branch = await api(admin, 'POST', '/branches', { name: 'Demo Şube', code: 'DMO' });
    log('created branch DMO');
  }
  log(`branch ${branch.code} (${branch.id})`);

  const subjects = {};
  for (const [code, name] of [['MAT', 'Matematik'], ['TUR', 'Türkçe']]) {
    const all = await listAll(admin, '/subjects');
    subjects[code] = all.find((s) => s.code === code) ?? (await api(admin, 'POST', '/subjects', { code, name }));
  }

  const classrooms = await listAll(admin, '/classrooms', { branchId: branch.id });
  let classroom = classrooms.find((c) => c.name === CLASSROOM);
  if (!classroom) {
    classroom = await api(admin, 'POST', '/classrooms', { branchId: branch.id, name: CLASSROOM, gradeLevel: 12, term: '2026-2027' });
    log(`created classroom ${CLASSROOM}`);
  } else {
    log(`classroom ${CLASSROOM} exists, reusing it`);
  }

  step('Teacher');
  const teachers = await listAll(admin, '/teachers');
  let teacher = teachers.find((t) => t.email === TEACHER_EMAIL);
  if (!teacher) {
    const created = await api(admin, 'POST', '/teachers', {
      branchId: branch.id, firstName: 'Kemal', lastName: 'Aksoy', email: TEACHER_EMAIL, phone: '05553334455',
      specialtySubjectId: subjects.MAT.id,
    });
    teacher = created.user;
    log('created teacher');
  }
  const assigned = await api(admin, 'GET', `/teachers/${teacher.id}/assignments`);
  const assignedRows = Array.isArray(assigned) ? assigned : (assigned.content ?? []);
  for (const code of ['MAT', 'TUR']) {
    if (!assignedRows.some((a) => a.classroomId === classroom.id && a.subjectId === subjects[code].id)) {
      await api(admin, 'POST', `/teachers/${teacher.id}/assignments`, { classroomId: classroom.id, subjectId: subjects[code].id });
      log(`assigned teacher to ${CLASSROOM} / ${code}`);
    }
  }

  step('Students and guardian');
  const existing = await listAll(admin, '/students', { branchId: branch.id });
  const students = [];
  for (const [no, firstName, lastName] of STUDENTS) {
    let s = existing.find((x) => x.studentNumber === no);
    if (!s) {
      const created = await api(admin, 'POST', '/students', {
        branchId: branch.id, firstName, lastName, studentNumber: no, gradeLevel: 12, targetExam: 'YKS',
      });
      s = created.user;
      log(`created student ${no} (${s.username})`);
    }
    students.push(s);
  }
  const members = await listAll(admin, `/classrooms/${classroom.id}/students`);
  for (const s of students) {
    if (!members.some((m) => (m.studentId ?? m.id) === s.id)) {
      await api(admin, 'PUT', `/students/${s.id}/classroom`, { classroomId: classroom.id, startDate: MEMBER_SINCE });
      log(`added ${s.studentNumber} to ${CLASSROOM}`);
    }
  }

  const guardians = await listAll(admin, '/guardians', { branchId: branch.id });
  let guardian = guardians.find((g) => g.phone === GUARDIAN.phone);
  if (!guardian) {
    guardian = (await api(admin, 'POST', '/guardians', { branchId: branch.id, ...GUARDIAN })).user;
    log('created guardian');
  }
  const ayse = students.find((s) => s.studentNumber === MAIN_STUDENT_NO);
  const link = await api(admin, 'GET', `/students/${ayse.id}/guardian`, undefined, { ok404: true });
  if (!link || (link.guardianId ?? link.guardian?.id) !== guardian.id) {
    await api(admin, 'PUT', `/students/${ayse.id}/guardian`, { guardianId: guardian.id, relationship: 'MOTHER' });
    log('linked guardian to Ayşe');
  }

  step('Passwords (Keycloak admin API)');
  for (const s of students) {
    const temp = s.studentNumber === TEMP_STUDENT_NO;
    await setPassword(s.username, temp ? TEMP_PASSWORD : PASSWORD, temp);
  }
  await setPassword(guardian.username, PASSWORD, false);
  await setPassword(TEACHER_EMAIL, PASSWORD, false);
  log('passwords set');

  return { branch, subjects, classroom, teacher, students, ayse, guardian };
}

// --- 2. wait until the other services have the events -----------------------------------------------
async function waitForReplicas(ctx) {
  step('Waiting for events to reach the services');
  const { classroom, students, ayse } = ctx;
  const ids = students.map((s) => `'${s.id}'`).join(',');
  const n = students.length;
  const checks = {
    'exam-analysis student copy': () => psql('exam_analysis', `select count(*) from student_replica where class_id='${classroom.id}' and is_active`),
    'attendance roster copy': () => psql('attendance_service', `select count(*) from membership_replica where classroom_id='${classroom.id}' and end_date is null`),
    'task roster copy': () => psql('task_service', `select count(*) from membership_replica where classroom_id='${classroom.id}' and end_date is null`),
    'notification contacts': () => psql('notification', `select count(*) from contact_replica where user_id in (${ids})`),
    'messaging users': () => psql('messaging_service', `select count(*) from user_replica where user_id in (${ids})`),
  };
  for (const [name, q] of Object.entries(checks)) {
    await waitFor(name, async () => {
      let count = '?';
      try {
        count = await q();
      } catch {
        return { done: name === 'messaging users', info: 'query failed' };
      }
      return { done: Number(count) >= n, info: `${count}/${n}` };
    }, { timeoutMs: 120_000 });
    log(`ok: ${name}`);
  }
  for (const [db, table] of [['attendance_service', 'guardian_link_replica'], ['task_service', 'guardian_link_replica'], ['notification', 'guardian_link_replica']]) {
    await waitFor(`${db} guardian link`, async () => {
      const c = psql(db, `select count(*) from ${table} where student_id='${ayse.id}'`);
      return { done: Number(c) >= 1, info: c };
    }, { timeoutMs: 120_000 });
    log(`ok: ${db} guardian link`);
  }
}

// --- 3. exam-analysis -------------------------------------------------------------------------------
const WEAK = new Set(['Paragraf', 'Cümlenin Ögeleri', 'Fonksiyonlar', 'Olasılık', 'Hareket Problemleri', 'Elektrik', 'İklim Bilgisi']);
// [number, base skill, gain per exam] for the other students; Ayşe is built separately.
const SKILL = { 1002: [0.82, 0.01], 1003: [0.7, 0.03], 1004: [0.58, 0.05], 1005: [0.78, 0.0], 1006: [0.38, 0.04], 1007: [0.62, -0.02], 1008: [0.5, 0.03], 1009: [0.3, 0.06], 1010: [0.66, 0.02] };
const EXAMS = [
  ['Demo TYT Deneme 1', ymd(addDays(today, -55))],
  ['Demo TYT Deneme 2', ymd(addDays(today, -40))],
  ['Demo TYT Deneme 3', ymd(addDays(today, -26))],
  ['Demo TYT Deneme 4', ymd(addDays(today, -12))],
];
const SECTIONS = [['TUR', 40], ['SOS', 20], ['MAT', 40], ['FEN', 20]]; // tyt-fwf-v1 answer segments

async function seedExams(admin, ctx) {
  step('Exam analysis');
  const { branch, students } = ctx;
  try {
    compose('exec', '-T', 'exam-analysis-api', 'python', '-m', 'app.cli', 'seed-catalog');
  } catch (e) {
    throw new Error(`seed-catalog failed (is the stack started with --profile services?): ${e.stderr ?? e.message}`);
  }
  const types = await api(admin, 'GET', '/exam-types');
  const tyt = types.find((t) => t.code === 'TYT');
  const subjects = await api(admin, 'GET', `/exam-types/${tyt.id}/subjects`);
  const leaves = {};
  const subjectId = {};
  for (const s of subjects) {
    const topics = await api(admin, 'GET', `/subjects/${s.id}/topics`);
    const parents = new Set(topics.map((t) => t.parentTopicId));
    leaves[s.code] = topics.filter((t) => !parents.has(t.id)).sort((a, b) => (a.name < b.name ? -1 : 1));
    subjectId[s.code] = s.id;
  }
  const format = (await api(admin, 'GET', '/optical-formats')).find((f) => f.code === 'tyt-fwf-v1');

  await api(admin, 'PUT', `/branches/${branch.id}/weak-topic-setting`, { windowSize: 3, successThreshold: 0.5 });
  log('weak topic setting: last 3 exams, threshold 0.5');

  const existing = await api(admin, 'GET', `/mock-exams?branchId=${branch.id}`);
  const list = Array.isArray(existing) ? existing : (existing.items ?? existing.content ?? []);

  for (let e = 0; e < EXAMS.length; e++) {
    const [name, examDate] = EXAMS[e];
    let exam = list.find((x) => x.name === name);
    if (!exam) {
      exam = await api(admin, 'POST', '/mock-exams', {
        examTypeId: tyt.id, name, publisher: 'Örnek Yayınları', examDate, branchId: branch.id, scoringSource: 'INSTITUTIONAL',
      });
      log(`created ${name}`);
    }
    if (exam.status === 'PUBLISHED') {
      log(`${name}: already published`);
      continue;
    }
    const rng = mulberry32(7000 + e);
    // Answer key: question i of a subject covers leaf topic i mod count; one key per booklet (same answers).
    const items = [];
    const answers = [];
    const topicOfQuestion = [];
    let qNo = 0;
    for (const [code, count] of SECTIONS) {
      for (let i = 0; i < count; i++) {
        qNo++;
        const topic = leaves[code][i % leaves[code].length];
        const correct = 'ABCDE'[Math.floor(rng() * 5)];
        answers.push(correct);
        topicOfQuestion.push(topic.name);
        for (const booklet of 'AB') {
          items.push({ booklet, questionNo: qNo, subjectId: subjectId[code], topicId: topic.id, correctAnswer: correct });
        }
      }
    }
    if (exam.status === 'DRAFT') {
      await api(admin, 'PUT', `/mock-exams/${exam.id}/answer-key`, { items });
      log(`${name}: answer key uploaded`);
    }

    if (['DRAFT', 'KEY_READY'].includes(exam.status) || !exam.status) {
      // Optical file: one line per student. Ayşe improves each exam but stays weak on the WEAK topics.
      const sheetRng = mulberry32(9100 + e);
      const lines = students.map((s) => {
        const no = s.studentNumber;
        let strong;
        let weak;
        if (no === MAIN_STUDENT_NO) {
          strong = 0.5 + 0.075 * e;
          weak = 0.1 + 0.05 * e;
        } else {
          const [base, gain] = SKILL[no];
          strong = Math.min(0.95, base + gain * e);
          weak = strong;
        }
        const sheet = answers.map((right, qi) => {
          const p = WEAK.has(topicOfQuestion[qi]) ? weak : strong;
          const roll = sheetRng();
          if (roll < 0.06) return ' ';
          if (sheetRng() < p) return right;
          return 'ABCDE'.replace(right, '')[Math.floor(sheetRng() * 4)];
        });
        return `${no.padEnd(10)}${no === '1003' ? 'B' : 'A'}${sheet.join('')}`;
      });
      const form = new FormData();
      form.set('opticalFormatId', format.id);
      form.set('file', new Blob([Buffer.from(lines.join('\n') + '\n', 'latin1')], { type: 'text/plain' }), `deneme-${e + 1}.txt`);
      const job = await api(admin, 'POST', `/mock-exams/${exam.id}/import-jobs`, undefined, { form });
      log(`${name}: import job ${job.id} queued`);
      let final;
      await waitFor(`import job of ${name}`, async () => {
        final = await api(admin, 'GET', `/import-jobs/${job.id}`);
        return { done: ['COMPLETED', 'FAILED'].includes(final.status), info: final.status };
      }, { timeoutMs: 120_000 });
      if (final.status !== 'COMPLETED') throw new Error(`import job of ${name} failed: ${final.errorMessage}`);
      const unmatched = await api(admin, 'GET', `/import-jobs/${job.id}/unmatched-rows`);
      for (const row of Array.isArray(unmatched) ? unmatched : (unmatched.items ?? [])) {
        if (row.status === 'PENDING' || row.status === 'OPEN') {
          log(`${name}: unmatched row ${row.lineNo} (${row.reason}) ignored`);
          await api(admin, 'POST', `/unmatched-rows/${row.id}/ignore`, {});
        }
      }
      log(`${name}: ${final.succeededRows}/${final.totalRows} rows scored`);
    }
    await api(admin, 'POST', `/mock-exams/${exam.id}/publish`, {});
    log(`${name}: published`);
  }
  return { tyt };
}

// --- 3b. AYT ----------------------------------------------------------------------------------------
// ayt-fwf-v1 has one 160-column segment in booklet order. Groups are the ÖSYM tests of the 0,5 net rule.
const AYT_SECTIONS = [
  ['EDB', 24], ['TAR1', 10], ['COG1', 6], // Türk Dili ve Edebiyatı-Sosyal Bilimler-1
  ['TAR2', 11], ['COG2', 11], ['FEL', 12], ['DIN', 6], // Sosyal Bilimler-2
  ['MAT', 40],
  ['FIZ', 14], ['KIM', 13], ['BIY', 13], // Fen Bilimleri
];
// SAY students answer Matematik + Fen, EA students Matematik + TDE-SB1; every other test stays blank.
const TRACK_TESTS = {
  SAY: new Set(['MAT', 'FIZ', 'KIM', 'BIY']),
  EA: new Set(['MAT', 'EDB', 'TAR1', 'COG1']),
};
const TRACK = {
  1001: 'SAY', 1002: 'SAY', 1003: 'SAY', 1005: 'SAY', 1007: 'SAY', 1010: 'SAY',
  1004: 'EA', 1006: 'EA', 1008: 'EA', 1009: 'EA',
};
// [name, date, index of the TYT exam it follows]. Each AYT links to the latest published TYT on or before
// its date (exam-analysis), so Deneme 1 counts the nets of TYT Deneme 3 and Deneme 2 those of TYT Deneme 4.
const AYT_EXAMS = [
  ['Demo AYT Deneme 1', ymd(addDays(today, -19)), 2],
  ['Demo AYT Deneme 2', ymd(addDays(today, -5)), 3],
];
// Every student answers the 80 questions of their two tests; at least 60 are correct (60-74 by TYT skill).
const AYT_MIN_CORRECT = 60;
const AYT_MAX_CORRECT = 74;

function aytCorrectCount(no, tytIndex) {
  const tyt = no === MAIN_STUDENT_NO ? 0.5 + 0.075 * tytIndex : SKILL[no][0] + SKILL[no][1] * tytIndex;
  const share = Math.max(0, Math.min(1, (tyt - 0.3) / 0.6)); // TYT skill 0.3..0.9 -> 0..1
  return Math.round(AYT_MIN_CORRECT + share * (AYT_MAX_CORRECT - AYT_MIN_CORRECT));
}

/** Fisher-Yates with the seeded PRNG, so a student's correct questions are spread over both tests. */
function shuffled(items, rng) {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(rng() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

async function seedAytExams(admin, ctx) {
  step('AYT exams (6 SAY, 4 EA students)');
  const { branch, students } = ctx;
  const ayt = (await api(admin, 'GET', '/exam-types')).find((t) => t.code === 'AYT');
  const leaves = {};
  const subjectId = {};
  for (const s of await api(admin, 'GET', `/exam-types/${ayt.id}/subjects`)) {
    const topics = await api(admin, 'GET', `/subjects/${s.id}/topics`);
    const parents = new Set(topics.map((t) => t.parentTopicId));
    leaves[s.code] = topics.filter((t) => !parents.has(t.id)).sort((a, b) => (a.name < b.name ? -1 : 1));
    subjectId[s.code] = s.id;
  }
  const format = (await api(admin, 'GET', '/optical-formats')).find((f) => f.code === 'ayt-fwf-v1');
  const existing = await api(admin, 'GET', `/mock-exams?branchId=${branch.id}`);
  const list = Array.isArray(existing) ? existing : (existing.items ?? existing.content ?? []);

  for (let e = 0; e < AYT_EXAMS.length; e++) {
    const [name, examDate, tytIndex] = AYT_EXAMS[e];
    let exam = list.find((x) => x.name === name);
    if (!exam) {
      exam = await api(admin, 'POST', '/mock-exams', {
        examTypeId: ayt.id, name, publisher: 'Örnek Yayınları', examDate, branchId: branch.id, scoringSource: 'INSTITUTIONAL',
      });
      log(`created ${name}`);
    }
    if (exam.status === 'PUBLISHED') {
      log(`${name}: already published`);
      continue;
    }
    const rng = mulberry32(7200 + e);
    const items = [];
    const answers = [];
    const subjectOfQuestion = [];
    let qNo = 0;
    for (const [code, count] of AYT_SECTIONS) {
      for (let i = 0; i < count; i++) {
        qNo++;
        const topic = leaves[code][i % leaves[code].length];
        const correct = 'ABCDE'[Math.floor(rng() * 5)];
        answers.push(correct);
        subjectOfQuestion.push(code);
        for (const booklet of 'AB') {
          items.push({ booklet, questionNo: qNo, subjectId: subjectId[code], topicId: topic.id, correctAnswer: correct });
        }
      }
    }
    if (exam.status === 'DRAFT') {
      await api(admin, 'PUT', `/mock-exams/${exam.id}/answer-key`, { items });
      log(`${name}: answer key uploaded (160 questions)`);
    }
    if (['DRAFT', 'KEY_READY'].includes(exam.status) || !exam.status) {
      const sheetRng = mulberry32(9200 + e);
      const lines = students.map((s) => {
        const no = s.studentNumber;
        const tests = TRACK_TESTS[TRACK[no]];
        // The questions of the student's two tests, in random order: the first `correct` are right,
        // the next 2-5 blank, the rest wrong. The other two tests stay blank (not taken).
        const own = shuffled(answers.map((_, qi) => qi).filter((qi) => tests.has(subjectOfQuestion[qi])), sheetRng);
        const correct = aytCorrectCount(no, tytIndex);
        const blanks = 2 + Math.floor(sheetRng() * 4);
        const sheet = answers.map(() => ' ');
        own.forEach((qi, rank) => {
          const right = answers[qi];
          if (rank < correct) sheet[qi] = right;
          else if (rank < correct + blanks) sheet[qi] = ' ';
          else sheet[qi] = 'ABCDE'.replace(right, '')[Math.floor(sheetRng() * 4)];
        });
        return `${no.padEnd(10)}A${sheet.join('')}`;
      });
      const form = new FormData();
      form.set('opticalFormatId', format.id);
      form.set('file', new Blob([Buffer.from(lines.join('\n') + '\n', 'latin1')], { type: 'text/plain' }), `ayt-deneme-${e + 1}.txt`);
      const job = await api(admin, 'POST', `/mock-exams/${exam.id}/import-jobs`, undefined, { form });
      log(`${name}: import job ${job.id} queued`);
      let final;
      await waitFor(`import job of ${name}`, async () => {
        final = await api(admin, 'GET', `/import-jobs/${job.id}`);
        return { done: ['COMPLETED', 'FAILED'].includes(final.status), info: final.status };
      }, { timeoutMs: 120_000 });
      if (final.status !== 'COMPLETED') throw new Error(`import job of ${name} failed: ${final.errorMessage}`);
      log(`${name}: ${final.succeededRows}/${final.totalRows} rows scored`);
    }
    await api(admin, 'POST', `/mock-exams/${exam.id}/publish`, {});
    log(`${name}: published`);
  }
}

// --- 4. attendance ----------------------------------------------------------------------------------
const SCHEDULE = [['MONDAY', 'MAT'], ['WEDNESDAY', 'TUR'], ['FRIDAY', 'MAT']];
const TIMES = ['16:00:00', '17:30:00'];

async function seedAttendance(admin, ctx) {
  step('Attendance');
  const { branch, classroom, teacher, subjects, students } = ctx;
  const schedules = await listAll(admin, '/class-schedules', { branchId: branch.id });
  for (const [day, code] of SCHEDULE) {
    if (!schedules.some((s) => s.classroomId === classroom.id && s.dayOfWeek === day)) {
      await api(admin, 'POST', '/class-schedules', {
        branchId: branch.id, classroomId: classroom.id, teacherId: teacher.id, subjectId: subjects[code].id,
        dayOfWeek: day, startsAt: TIMES[0], endsAt: TIMES[1], validFrom: ymd(today),
      });
      log(`weekly schedule ${day} ${code}`);
    }
  }

  // Past sessions of the last 4 weeks: an admin may open a session on a past date and take it late with a reason.
  const sessions = await listAll(admin, '/class-sessions', { branchId: branch.id, classroomId: classroom.id });
  const rng = mulberry32(4242);
  const ayse = ctx.ayse;
  // Ayşe: mostly present, two late, one absent, one excused (by session index, oldest first).
  const ayseMarks = { 2: 'LATE', 5: 'ABSENT', 7: 'LATE', 9: 'EXCUSED' };
  let index = 0;
  for (let back = 28; back >= 1; back--) {
    const date = addDays(today, -back);
    const entry = SCHEDULE.find(([day]) => day === DAYS[date.getDay()]);
    if (!entry) continue;
    const myIndex = index++;
    let session = sessions.find((s) => s.sessionDate === ymd(date) && s.classroomId === classroom.id);
    if (!session) {
      session = await api(admin, 'POST', '/class-sessions', {
        branchId: branch.id, classroomId: classroom.id, teacherId: teacher.id, subjectId: subjects[entry[1]].id,
        sessionDate: ymd(date), startsAt: TIMES[0], endsAt: TIMES[1],
      });
    }
    if (session.status === 'COMPLETED') continue;
    const entries = students.map((s) => {
      if (s.id === ayse.id) return { studentId: s.id, status: ayseMarks[myIndex] ?? 'PRESENT' };
      const roll = rng();
      return { studentId: s.id, status: roll < 0.06 ? 'ABSENT' : roll < 0.13 ? 'LATE' : 'PRESENT' };
    });
    await api(admin, 'POST', `/class-sessions/${session.id}/attendance`, { entries, reason: 'Demo verisi: geçmiş yoklama toplu girişi' });
    log(`attendance taken for ${ymd(date)}`);
  }
  try {
    compose('run', '--rm', '-T', 'attendance', '--attendance.job=generate-sessions');
    log('generate-sessions job done (upcoming sessions of the next 7 days)');
  } catch (e) {
    throw new Error(`generate-sessions failed: ${e.stderr ?? e.message}`);
  }
}

// --- 5. tasks ---------------------------------------------------------------------------------------
async function seedTasks(teacherToken, ayseToken, ctx) {
  step('Tasks');
  const { classroom } = ctx;
  const start = ymd(today);
  const defs = [
    { key: 'paragraf', title: 'Paragraf soru çözümü: 40 soru', description: 'Deneme kitabından 40 paragraf sorusu çöz, yanlışlarını not et.', recurrence: 'ONCE', endDate: ymd(addDays(today, 2)), dueTime: '18:00:00' },
    { key: 'turev', title: 'Türev konu özeti', description: 'Türev kurallarını bir sayfada özetle ve 10 örnek soru ekle.', recurrence: 'ONCE', endDate: ymd(addDays(today, 4)), dueTime: '20:00:00' },
    { key: 'geometri', title: 'Geometri: Çember ve Daire testi', description: 'Çember ve daire testindeki tüm soruları çöz.', recurrence: 'ONCE', endDate: ymd(addDays(today, 7)), dueTime: '17:00:00' },
    { key: 'rapor', title: 'Deneme analiz raporu', description: 'Son denemenin yanlış sorularını konulara göre grupla.', recurrence: 'ONCE', endDate: ymd(addDays(today, 9)), dueTime: '19:00:00' },
    { key: 'okuma', title: 'Günlük kitap okuma: 30 sayfa', description: 'Her gün en az 30 sayfa oku.', recurrence: 'DAILY', endDate: ymd(addDays(today, 13)), dueTime: '22:00:00' },
    { key: 'limit', title: 'Limit çalışma kağıdı', description: 'Çalışma kağıdındaki limit sorularını tamamla.', recurrence: 'ONCE', endDate: ymd(addDays(today, 1)), dueTime: '12:00:00' },
    { key: 'vektor', title: 'Fizik: Vektörler tekrar testi', description: 'Vektörler konusundan 25 soruluk testi çöz.', recurrence: 'ONCE', endDate: ymd(addDays(today, 1)), dueTime: '14:00:00' },
  ];
  const existing = await listAll(teacherToken, '/tasks');
  const byKey = {};
  for (const d of defs) {
    let task = existing.find((t) => t.title === d.title);
    if (!task) {
      task = await retryTask(() =>
        api(teacherToken, 'POST', '/tasks', {
          title: d.title, description: d.description, targetType: 'CLASSROOM', targetId: classroom.id,
          recurrence: d.recurrence, startDate: start, endDate: d.endDate, dueTime: d.dueTime,
        }),
      );
      log(`created task: ${d.title}`);
    }
    byKey[d.key] = task;
  }

  // Ayşe's assignments (a student sees only her own).
  const mine = await listAll(ayseToken, '/task-assignments');
  const of = (key) => mine.find((a) => a.taskId === byKey[key].id);
  const toSubmit = [
    ['paragraf', 'Ödevi tamamladım, 4 yanlışım var.'],
    ['turev', 'Türev özetini hazırladım, örnek soruları da ekledim.'],
    ['geometri', 'Tüm soruları çözdüm.'],
  ];
  for (const [key, text] of toSubmit) {
    const a = of(key);
    if (!a) throw new Error(`Ayşe has no assignment for ${key}; task roster copy missing?`);
    if (a.status === 'ASSIGNED') {
      await api(ayseToken, 'PUT', `/task-assignments/${a.id}/submission`, { description: text });
      log(`Ayşe submitted: ${byKey[key].title}`);
    }
  }
  const grades = [
    ['turev', 85, 'Güzel bir özet, örnekler yerinde. Zincir kuralına biraz daha çalış.'],
    ['geometri', 70, 'Çemberde teğet sorularını tekrar et.'],
  ];
  for (const [key, score, feedback] of grades) {
    const a = (await listAll(teacherToken, '/task-assignments', { studentId: ctx.ayse.id })).find((x) => x.taskId === byKey[key].id);
    if (a && a.status === 'SUBMITTED') {
      await api(teacherToken, 'PUT', `/task-assignments/${a.id}/evaluation`, { score, feedback });
      log(`graded: ${byKey[key].title} -> ${score}`);
    }
  }

  // OVERDUE cannot be reached through the API (time has to pass): move due_at into the past with SQL, then let the
  // real check-deadlines job mark the assignments. Only ASSIGNED rows of the two demo tasks are touched.
  const ids = [byKey.limit.id, byKey.vektor.id].map((i) => `'${i}'`).join(',');
  const moved = psql('task_service', `update assignment set due_at = now() - interval '2 days' where task_id in (${ids}) and status = 'ASSIGNED' returning id`);
  if (moved) {
    log(`moved ${moved.split('\n').length} assignment due times into the past (SQL)`);
    try {
      compose('run', '--rm', '-T', 'task', '--task.job=check-deadlines');
      log('check-deadlines job done');
    } catch (e) {
      throw new Error(`check-deadlines failed: ${e.stderr ?? e.message}`);
    }
  }
}

/** Task creation needs the roster copy of the task service; retry briefly while the events arrive. */
async function retryTask(fn) {
  for (let i = 0; ; i++) {
    try {
      return await fn();
    } catch (e) {
      if (i >= 10 || e.status === 403) throw e;
      await sleep(2000);
    }
  }
}

// --- main -------------------------------------------------------------------------------------------
async function main() {
  const admin = await login(ADMIN.user, ADMIN.password);
  const ctx = await seedPeople(admin);
  await waitForReplicas(ctx);
  await seedExams(await login(ADMIN.user, ADMIN.password), ctx);
  await seedAytExams(await login(ADMIN.user, ADMIN.password), ctx);
  await seedAttendance(await login(ADMIN.user, ADMIN.password), ctx);
  const teacherToken = await login(TEACHER_EMAIL, PASSWORD);
  const ayseToken = await login(ctx.ayse.username, PASSWORD);
  await seedTasks(teacherToken, ayseToken, ctx);

  step('Check');
  const fresh = await login(ctx.ayse.username, PASSWORD);
  const unread = await api(fresh, 'GET', '/notifications/unread-count');
  log(`Ayşe unread notifications: ${JSON.stringify(unread)}`);
  log(`\nDone. Student login: ${ctx.ayse.username} / ${PASSWORD}`);
}

main().catch((e) => {
  console.error(`\nSEED FAILED: ${e.message}`);
  process.exit(1);
});
