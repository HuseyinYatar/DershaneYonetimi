// Demo data: 2 teachers, 2 classrooms of 10 students, a guardian, 5 TYT and 2 AYT exams, attendance, tasks.
// Re-runnable: every step looks for what it would create.
//   node local/seed/seed.mjs            local docker compose stack (Node 22+, --profile services)
//   SEED_TARGET=prod ...                the cluster, run by the demo-seed Job (k8s/platform/demo-seed)
// Almost everything goes through the real APIs (the gateway) so events flow and replicas fill.
// The local-only exceptions (see local/README.md "Mock veri"): the exam catalog (CLI in the exam-analysis
// container; in the cluster the exam-analysis migration Job loads it), checking the replicas with SQL,
// running the generate-sessions job, and moving two assignments' due time into the past with SQL so the
// real check-deadlines job marks them OVERDUE. In the cluster the CronJobs do the scheduled work.
import {
  GATEWAY,
  KEYCLOAK,
  LOCAL,
  TARGET,
  api,
  compose,
  listAll,
  log,
  login,
  psql,
  requiredActionsOf,
  setPassword,
  setRequiredActions,
  sleep,
  until,
  waitFor,
} from './lib.mjs';

// Shared password of every demo account (prod: Secrets Manager dershane/demo/password). Locally the known
// value below; the super-admin keeps its own (local realm: admin; prod: dershane/keycloak/superadmin).
const PASSWORD = process.env.SEED_PASSWORD ?? 'Demo1234!';
const TEMP_PASSWORD = 'Gecici123!'; // local only: Keycloak asks for a new one at first login
const ADMIN = {
  user: process.env.SEED_ADMIN_USER ?? 'superadmin@dershane.local',
  password: process.env.SEED_ADMIN_PASSWORD ?? 'admin',
};
const MEMBER_SINCE = '2026-09-01';
// Extra branches (each with one classroom) and two more teachers of MRKZ / Demo 12-A, with weekly hours for the schedule demo.
const EXTRA_BRANCHES = [
  { code: 'KDKY', name: 'Kadıköy Şubesi', classroom: { name: 'Kadıköy 12-A', gradeLevel: 12 } },
  { code: 'CNKY', name: 'Çankaya Şubesi', classroom: { name: 'Çankaya 11-A', gradeLevel: 11 } },
];
const EXTRA_SUBJECTS = [['FIZ', 'Fizik'], ['KIM', 'Kimya']];
const EXTRA_TEACHERS = [
  { email: 'zeynep.karaca@dershane.local', firstName: 'Zeynep', lastName: 'Karaca', phone: '05553334488', subject: 'FIZ', weeklyHours: 4 },
  { email: 'murat.erdem@dershane.local', firstName: 'Murat', lastName: 'Erdem', phone: '05553334477', subject: 'KIM', weeklyHours: 3 },
];

const TEACHERS = [
  { key: 'kemal', email: 'ogretmen@dershane.local', firstName: 'Kemal', lastName: 'Aksoy', phone: '05553334455', specialty: 'MAT' },
  { key: 'seda', email: 'ogretmen2@dershane.local', firstName: 'Seda', lastName: 'Kılıç', phone: '05553334466', specialty: 'TUR' },
];

// Each classroom has its own teacher and weekly schedule (different days, so the two never clash).
const CLASSROOMS = [
  {
    name: 'Demo 12-A',
    teacher: 'kemal',
    schedule: [['MONDAY', 'MAT'], ['WEDNESDAY', 'TUR'], ['FRIDAY', 'MAT']],
    students: [
      ['1001', 'Ayşe', 'Yılmaz'],
      ['1002', 'Mehmet', 'Kaya'],
      ['1003', 'Elif', 'Demir'],
      ['1004', 'Can', 'Öztürk'],
      ['1005', 'Zeynep', 'Arslan'],
      ['1006', 'Burak', 'Şahin'],
      ['1007', 'Selin', 'Çelik'],
      ['1008', 'Emre', 'Yıldız'],
      ['1009', 'Deniz', 'Aydın'],
      ['1010', 'Merve', 'Koç'], // keeps a temporary password locally
    ],
  },
  {
    name: 'Demo 12-B',
    teacher: 'seda',
    schedule: [['TUESDAY', 'TUR'], ['THURSDAY', 'MAT']],
    students: [
      ['1011', 'Ahmet', 'Polat'],
      ['1012', 'Ebru', 'Kurt'],
      ['1013', 'Hakan', 'Özdemir'],
      ['1014', 'İrem', 'Aslan'],
      ['1015', 'Kerem', 'Doğan'],
      ['1016', 'Nazlı', 'Er'],
      ['1017', 'Onur', 'Güneş'],
      ['1018', 'Pınar', 'Tekin'],
      ['1019', 'Serkan', 'Avcı'],
      ['1020', 'Yağmur', 'Bulut'],
    ],
  },
];
// Prod: every demo account shares one password (decision of 2026-11-18), so no temporary-password student.
const TEMP_STUDENT_NO = LOCAL ? '1010' : null;
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
const rowsOf = (res) => (Array.isArray(res) ? res : (res.items ?? res.content ?? []));

// --- 1. branch, subjects, classrooms, people --------------------------------------------------------
async function seedPeople(admin) {
  step('Branch, subjects, classrooms');
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

  const existingRooms = await listAll(admin, '/classrooms', { branchId: branch.id });
  const rooms = [];
  for (const def of CLASSROOMS) {
    let room = existingRooms.find((c) => c.name === def.name);
    if (!room) {
      room = await api(admin, 'POST', '/classrooms', { branchId: branch.id, name: def.name, gradeLevel: 12, term: '2026-2027' });
      log(`created classroom ${def.name}`);
    } else {
      log(`classroom ${def.name} exists, reusing it`);
    }
    rooms.push({ def, room });
  }

  step('Teachers');
  const existingTeachers = await listAll(admin, '/teachers');
  const teachers = {};
  for (const t of TEACHERS) {
    let teacher = existingTeachers.find((x) => x.email === t.email);
    if (!teacher) {
      const created = await api(admin, 'POST', '/teachers', {
        branchId: branch.id, firstName: t.firstName, lastName: t.lastName, email: t.email, phone: t.phone,
        specialtySubjectId: subjects[t.specialty].id,
      });
      teacher = created.user;
      log(`created teacher ${t.firstName} ${t.lastName}`);
    }
    teachers[t.key] = { ...teacher, email: t.email };
  }
  for (const { def, room } of rooms) {
    const teacher = teachers[def.teacher];
    const assigned = rowsOf(await api(admin, 'GET', `/teachers/${teacher.id}/assignments`));
    for (const code of ['MAT', 'TUR']) {
      if (!assigned.some((a) => a.classroomId === room.id && a.subjectId === subjects[code].id)) {
        await api(admin, 'POST', `/teachers/${teacher.id}/assignments`, { classroomId: room.id, subjectId: subjects[code].id });
        log(`assigned ${def.teacher} to ${def.name} / ${code}`);
      }
    }
  }

  step('Students and guardian');
  const existing = await listAll(admin, '/students', { branchId: branch.id });
  const classrooms = [];
  const students = [];
  for (const { def, room } of rooms) {
    const own = [];
    for (const [no, firstName, lastName] of def.students) {
      let s = existing.find((x) => x.studentNumber === no);
      if (!s) {
        const created = await api(admin, 'POST', '/students', {
          branchId: branch.id, firstName, lastName, studentNumber: no, gradeLevel: 12, targetExam: 'YKS',
        });
        s = created.user;
        log(`created student ${no} (${s.username})`);
      }
      own.push(s);
    }
    const members = await listAll(admin, `/classrooms/${room.id}/students`);
    for (const s of own) {
      if (!members.some((m) => (m.studentId ?? m.id) === s.id)) {
        await api(admin, 'PUT', `/students/${s.id}/classroom`, { classroomId: room.id, startDate: MEMBER_SINCE });
        log(`added ${s.studentNumber} to ${def.name}`);
      }
    }
    classrooms.push({ ...def, id: room.id, teacher: teachers[def.teacher], students: own });
    students.push(...own);
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
  for (const t of TEACHERS) await setPassword(t.email, PASSWORD, false);
  log('passwords set');

  return { branch, subjects, classrooms, teachers, students, ayse, guardian };
}

/** Extra branches and teachers (Physics/Chemistry in the first classroom); runs after seedPeople, which owns the main branch and classrooms. */
async function seedExtras(admin, ctx) {
  step('Extra branches and teachers');
  const mainRoom = ctx.classrooms[0];
  const branches = await listAll(admin, '/branches');
  for (const b of EXTRA_BRANCHES) {
    let branch = branches.find((x) => x.code === b.code);
    if (!branch) {
      branch = await api(admin, 'POST', '/branches', { name: b.name, code: b.code });
      log(`created branch ${b.code}`);
    }
    const rooms = await listAll(admin, '/classrooms', { branchId: branch.id });
    if (!rooms.some((c) => c.name === b.classroom.name)) {
      await api(admin, 'POST', '/classrooms', { branchId: branch.id, name: b.classroom.name, gradeLevel: b.classroom.gradeLevel, term: '2026-2027' });
      log(`created classroom ${b.classroom.name}`);
    }
  }

  const all = await listAll(admin, '/subjects');
  const subjects = {};
  for (const [code, name] of EXTRA_SUBJECTS) {
    subjects[code] = all.find((s) => s.code === code) ?? (await api(admin, 'POST', '/subjects', { code, name }));
  }

  const teachers = await listAll(admin, '/teachers');
  for (const t of EXTRA_TEACHERS) {
    let teacher = teachers.find((x) => x.email === t.email);
    if (!teacher) {
      teacher = (await api(admin, 'POST', '/teachers', {
        branchId: ctx.branch.id, firstName: t.firstName, lastName: t.lastName, email: t.email, phone: t.phone,
        specialtySubjectId: subjects[t.subject].id,
      })).user;
      log(`created teacher ${t.email}`);
    }
    const res = await api(admin, 'GET', `/teachers/${teacher.id}/assignments`);
    const rows = Array.isArray(res) ? res : (res.content ?? []);
    let row = rows.find((a) => a.classroomId === mainRoom.id && a.subjectId === subjects[t.subject].id);
    if (!row) {
      row = await api(admin, 'POST', `/teachers/${teacher.id}/assignments`, { classroomId: mainRoom.id, subjectId: subjects[t.subject].id });
      log(`assigned ${t.email} to ${mainRoom.name} / ${t.subject}`);
    }
    if (row.weeklyHours !== t.weeklyHours) {
      await api(admin, 'PUT', `/teachers/${teacher.id}/assignments/${row.id}`, { weeklyHours: t.weeklyHours });
      log(`weekly hours of ${t.email}: ${t.weeklyHours}`);
    }
    await setPassword(t.email, PASSWORD, false);
  }
}

// --- 2. wait until the other services have the events -----------------------------------------------
async function waitForReplicas(admin, ctx) {
  step('Waiting for events to reach the services');
  const { classrooms, students, ayse } = ctx;
  const n = students.length;
  if (!LOCAL) {
    // No database access in the cluster: exam-analysis answers a student's results (200) once its copy of
    // the student exists and 404 before. Attendance and task copies are waited for where they are used.
    await waitFor('exam-analysis student copies', async () => {
      let known = 0;
      for (const s of students) {
        if ((await api(admin, 'GET', `/students/${s.id}/results`, undefined, { ok404: true })) !== null) known++;
      }
      return { done: known >= n, info: `${known}/${n}` };
    }, { timeoutMs: 300_000, everyMs: 5000 });
    log('ok: exam-analysis student copies');
    return;
  }
  const rooms = classrooms.map((c) => `'${c.id}'`).join(',');
  const ids = students.map((s) => `'${s.id}'`).join(',');
  const checks = {
    'exam-analysis student copy': () => psql('exam_analysis', `select count(*) from student_replica where class_id in (${rooms}) and is_active`),
    'attendance roster copy': () => psql('attendance_service', `select count(*) from membership_replica where classroom_id in (${rooms}) and end_date is null`),
    'task roster copy': () => psql('task_service', `select count(*) from membership_replica where classroom_id in (${rooms}) and end_date is null`),
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
// [base skill, gain per exam] of every student but Ayşe (built separately).
const SKILL = {
  1002: [0.82, 0.01], 1003: [0.7, 0.03], 1004: [0.58, 0.05], 1005: [0.78, 0.0], 1006: [0.38, 0.04],
  1007: [0.62, -0.02], 1008: [0.5, 0.03], 1009: [0.3, 0.06], 1010: [0.66, 0.02],
  1011: [0.74, 0.02], 1012: [0.55, 0.04], 1013: [0.42, 0.05], 1014: [0.86, 0.0], 1015: [0.6, 0.03],
  1016: [0.35, 0.06], 1017: [0.68, 0.01], 1018: [0.48, 0.04], 1019: [0.64, -0.01], 1020: [0.52, 0.05],
};
// TYT Deneme 5 comes after both AYT exams, so their linked TYT nets (below) stay as before.
const EXAMS = [
  ['Demo TYT Deneme 1', ymd(addDays(today, -55))],
  ['Demo TYT Deneme 2', ymd(addDays(today, -40))],
  ['Demo TYT Deneme 3', ymd(addDays(today, -26))],
  ['Demo TYT Deneme 4', ymd(addDays(today, -12))],
  ['Demo TYT Deneme 5', ymd(addDays(today, -2))],
];
const SECTIONS = [['TUR', 40], ['SOS', 20], ['MAT', 40], ['FEN', 20]]; // tyt-fwf-v1 answer segments

/** Leaf topics and ids of an exam type's subjects (the answer key needs both). */
async function catalogOf(admin, examType) {
  const leaves = {};
  const subjectId = {};
  for (const s of await api(admin, 'GET', `/exam-types/${examType.id}/subjects`)) {
    const topics = await api(admin, 'GET', `/subjects/${s.id}/topics`);
    const parents = new Set(topics.map((t) => t.parentTopicId));
    leaves[s.code] = topics.filter((t) => !parents.has(t.id)).sort((a, b) => (a.name < b.name ? -1 : 1));
    subjectId[s.code] = s.id;
  }
  return { leaves, subjectId };
}

/** Uploads an optical file, waits for the import and ignores rows that did not match a student. */
async function importOptical(admin, exam, name, formatId, lines, fileName) {
  const form = new FormData();
  form.set('opticalFormatId', formatId);
  form.set('file', new Blob([Buffer.from(lines.join('\n') + '\n', 'latin1')], { type: 'text/plain' }), fileName);
  const job = await api(admin, 'POST', `/mock-exams/${exam.id}/import-jobs`, undefined, { form });
  log(`${name}: import job ${job.id} queued`);
  let final;
  await waitFor(`import job of ${name}`, async () => {
    final = await api(admin, 'GET', `/import-jobs/${job.id}`);
    return { done: ['COMPLETED', 'FAILED'].includes(final.status), info: final.status };
  }, { timeoutMs: 180_000 });
  if (final.status !== 'COMPLETED') throw new Error(`import job of ${name} failed: ${final.errorMessage}`);
  for (const row of rowsOf(await api(admin, 'GET', `/import-jobs/${job.id}/unmatched-rows`))) {
    if (row.status === 'PENDING' || row.status === 'OPEN') {
      log(`${name}: unmatched row ${row.lineNo} (${row.reason}) ignored`);
      await api(admin, 'POST', `/unmatched-rows/${row.id}/ignore`, {});
    }
  }
  log(`${name}: ${final.succeededRows}/${final.totalRows} rows scored`);
}

async function seedExams(admin, ctx) {
  step('Exam analysis');
  const { branch, students } = ctx;
  if (LOCAL) {
    // In the cluster the exam-analysis migration Job has already loaded the catalog.
    try {
      compose('exec', '-T', 'exam-analysis-api', 'python', '-m', 'app.cli', 'seed-catalog');
    } catch (e) {
      throw new Error(`seed-catalog failed (is the stack started with --profile services?): ${e.stderr ?? e.message}`);
    }
  }
  const tyt = (await api(admin, 'GET', '/exam-types')).find((t) => t.code === 'TYT');
  const { leaves, subjectId } = await catalogOf(admin, tyt);
  const format = (await api(admin, 'GET', '/optical-formats')).find((f) => f.code === 'tyt-fwf-v1');

  await api(admin, 'PUT', `/branches/${branch.id}/weak-topic-setting`, { windowSize: 3, successThreshold: 0.5 });
  log('weak topic setting: last 3 exams, threshold 0.5');

  const list = rowsOf(await api(admin, 'GET', `/mock-exams?branchId=${branch.id}`));
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
      // One line per student of both classrooms. Ayşe improves each exam but stays weak on the WEAK topics.
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
      await importOptical(admin, exam, name, format.id, lines, `deneme-${e + 1}.txt`);
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
  1011: 'SAY', 1012: 'EA', 1013: 'SAY', 1014: 'SAY', 1015: 'EA',
  1016: 'EA', 1017: 'SAY', 1018: 'EA', 1019: 'SAY', 1020: 'SAY',
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
  step('AYT exams (SAY and EA students of both classrooms)');
  const { branch, students } = ctx;
  const ayt = (await api(admin, 'GET', '/exam-types')).find((t) => t.code === 'AYT');
  const { leaves, subjectId } = await catalogOf(admin, ayt);
  const format = (await api(admin, 'GET', '/optical-formats')).find((f) => f.code === 'ayt-fwf-v1');
  const list = rowsOf(await api(admin, 'GET', `/mock-exams?branchId=${branch.id}`));

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
      await importOptical(admin, exam, name, format.id, lines, `ayt-deneme-${e + 1}.txt`);
    }
    await api(admin, 'POST', `/mock-exams/${exam.id}/publish`, {});
    log(`${name}: published`);
  }
}

// --- 4. attendance ----------------------------------------------------------------------------------
const TIMES = ['16:00:00', '17:30:00'];

/** The attendance roster comes from events; until it arrives the full list is a ROSTER_MISMATCH. */
async function takeAttendance(admin, sessionId, body) {
  for (let i = 0; ; i++) {
    try {
      return await api(admin, 'POST', `/class-sessions/${sessionId}/attendance`, body);
    } catch (e) {
      if (e.code !== 'ROSTER_MISMATCH' || i >= 30) throw e;
      await sleep(5000);
    }
  }
}

async function seedAttendance(admin, ctx) {
  step('Attendance');
  const { branch, classrooms, subjects, ayse } = ctx;
  const schedules = await listAll(admin, '/class-schedules', { branchId: branch.id });
  for (const room of classrooms) {
    for (const [day, code] of room.schedule) {
      if (!schedules.some((s) => s.classroomId === room.id && s.dayOfWeek === day)) {
        await api(admin, 'POST', '/class-schedules', {
          branchId: branch.id, classroomId: room.id, teacherId: room.teacher.id, subjectId: subjects[code].id,
          dayOfWeek: day, startsAt: TIMES[0], endsAt: TIMES[1], validFrom: ymd(today),
        });
        log(`weekly schedule ${room.name} ${day} ${code}`);
      }
    }
  }

  // Past sessions of the last 4 weeks: an admin may open a session on a past date and take it late with a reason.
  // Ayşe (12-A): mostly present, two late, one absent, one excused (by session index, oldest first).
  const ayseMarks = { 2: 'LATE', 5: 'ABSENT', 7: 'LATE', 9: 'EXCUSED' };
  for (const [r, room] of classrooms.entries()) {
    const sessions = await listAll(admin, '/class-sessions', { branchId: branch.id, classroomId: room.id });
    const rng = mulberry32(4242 + r);
    let index = 0;
    for (let back = 28; back >= 1; back--) {
      const date = addDays(today, -back);
      const entry = room.schedule.find(([day]) => day === DAYS[date.getDay()]);
      if (!entry) continue;
      const myIndex = index++;
      let session = sessions.find((s) => s.sessionDate === ymd(date) && s.classroomId === room.id);
      if (!session) {
        session = await api(admin, 'POST', '/class-sessions', {
          branchId: branch.id, classroomId: room.id, teacherId: room.teacher.id, subjectId: subjects[entry[1]].id,
          sessionDate: ymd(date), startsAt: TIMES[0], endsAt: TIMES[1],
        });
      }
      if (session.status === 'COMPLETED') continue;
      const entries = room.students.map((s) => {
        if (s.id === ayse.id) return { studentId: s.id, status: ayseMarks[myIndex] ?? 'PRESENT' };
        const roll = rng();
        return { studentId: s.id, status: roll < 0.06 ? 'ABSENT' : roll < 0.13 ? 'LATE' : 'PRESENT' };
      });
      await takeAttendance(admin, session.id, { entries, reason: 'Demo verisi: geçmiş yoklama toplu girişi' });
      log(`attendance taken for ${room.name} ${ymd(date)}`);
    }
  }
  if (LOCAL) {
    // In the cluster the generate-sessions CronJob does this every night.
    try {
      compose('run', '--rm', '-T', 'attendance', '--attendance.job=generate-sessions');
      log('generate-sessions job done (upcoming sessions of the next 7 days)');
    } catch (e) {
      throw new Error(`generate-sessions failed: ${e.stderr ?? e.message}`);
    }
  }
}

// --- 5. tasks ---------------------------------------------------------------------------------------
const TASKS_12A = [
  { key: 'paragraf', title: 'Paragraf soru çözümü: 40 soru', description: 'Deneme kitabından 40 paragraf sorusu çöz, yanlışlarını not et.', recurrence: 'ONCE', days: 2, dueTime: '18:00:00' },
  { key: 'turev', title: 'Türev konu özeti', description: 'Türev kurallarını bir sayfada özetle ve 10 örnek soru ekle.', recurrence: 'ONCE', days: 4, dueTime: '20:00:00' },
  { key: 'geometri', title: 'Geometri: Çember ve Daire testi', description: 'Çember ve daire testindeki tüm soruları çöz.', recurrence: 'ONCE', days: 7, dueTime: '17:00:00' },
  { key: 'rapor', title: 'Deneme analiz raporu', description: 'Son denemenin yanlış sorularını konulara göre grupla.', recurrence: 'ONCE', days: 9, dueTime: '19:00:00' },
  { key: 'okuma', title: 'Günlük kitap okuma: 30 sayfa', description: 'Her gün en az 30 sayfa oku.', recurrence: 'DAILY', days: 13, dueTime: '22:00:00' },
  { key: 'limit', title: 'Limit çalışma kağıdı', description: 'Çalışma kağıdındaki limit sorularını tamamla.', recurrence: 'ONCE', days: 1, dueTime: '12:00:00' },
  { key: 'vektor', title: 'Fizik: Vektörler tekrar testi', description: 'Vektörler konusundan 25 soruluk testi çöz.', recurrence: 'ONCE', days: 1, dueTime: '14:00:00' },
];
const TASKS_12B = [
  { key: 'dilbilgisi', title: 'Dil bilgisi: Fiilimsiler testi', description: 'Fiilimsiler testindeki 30 soruyu çöz.', recurrence: 'ONCE', days: 3, dueTime: '18:00:00' },
  { key: 'problemler', title: 'Problemler: Yaş ve işçi soruları', description: 'Çalışma kağıdındaki 20 problemi çöz, çözüm yolunu yaz.', recurrence: 'ONCE', days: 6, dueTime: '19:00:00' },
];

async function createTasks(teacherToken, classroom, defs) {
  const existing = await listAll(teacherToken, '/tasks');
  const byKey = {};
  for (const d of defs) {
    let task = existing.find((t) => t.title === d.title);
    if (!task) {
      task = await retryTask(() =>
        api(teacherToken, 'POST', '/tasks', {
          title: d.title, description: d.description, targetType: 'CLASSROOM', targetId: classroom.id,
          recurrence: d.recurrence, startDate: ymd(today), endDate: ymd(addDays(today, d.days)), dueTime: d.dueTime,
        }),
      );
      log(`created task for ${classroom.name}: ${d.title}`);
    }
    byKey[d.key] = task;
  }
  return byKey;
}

async function seedTasks(ctx) {
  step('Tasks');
  const [roomA, roomB] = ctx.classrooms;
  const kemal = await login(roomA.teacher.email, PASSWORD);
  const seda = await login(roomB.teacher.email, PASSWORD);
  const ayseToken = await login(ctx.ayse.username, PASSWORD);
  const byKey = await createTasks(kemal, roomA, TASKS_12A);
  await createTasks(seda, roomB, TASKS_12B);

  // Ayşe's assignments (a student sees only her own); they appear once the task service has her membership.
  let mine = [];
  await waitFor("Ayşe's assignments", async () => {
    mine = await listAll(ayseToken, '/task-assignments');
    return { done: ['paragraf', 'turev', 'geometri'].every((k) => mine.some((a) => a.taskId === byKey[k].id)), info: `${mine.length}` };
  }, { timeoutMs: 120_000 });
  const of = (key) => mine.find((a) => a.taskId === byKey[key].id);
  const toSubmit = [
    ['paragraf', 'Ödevi tamamladım, 4 yanlışım var.'],
    ['turev', 'Türev özetini hazırladım, örnek soruları da ekledim.'],
    ['geometri', 'Tüm soruları çözdüm.'],
  ];
  for (const [key, text] of toSubmit) {
    const a = of(key);
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
    const a = (await listAll(kemal, '/task-assignments', { studentId: ctx.ayse.id })).find((x) => x.taskId === byKey[key].id);
    if (a && a.status === 'SUBMITTED') {
      await api(kemal, 'PUT', `/task-assignments/${a.id}/evaluation`, { score, feedback });
      log(`graded: ${byKey[key].title} -> ${score}`);
    }
  }

  if (!LOCAL) return; // in the cluster the check-deadlines CronJob marks overdue assignments when time passes
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
      if (i >= 30 || e.status === 403) throw e;
      await sleep(3000);
    }
  }
}

// --- main -------------------------------------------------------------------------------------------
async function main() {
  log(`Seed target: ${TARGET} (gateway ${GATEWAY}, Keycloak ${KEYCLOAK})`);
  if (!LOCAL) {
    // The realm gives the super-admin a temporary first password; a login would stop at the change-password
    // page. While it is still temporary, make it permanent for the run and ask for the change again at the
    // end. Once the super-admin has chosen its own password the first one no longer works: the demo data
    // was loaded before, so this run stops instead of resetting that password.
    const actions = await until('Keycloak', () => requiredActionsOf(ADMIN.user));
    if (actions.includes('UPDATE_PASSWORD')) {
      await setPassword(ADMIN.user, ADMIN.password, false);
    } else {
      try {
        await login(ADMIN.user, ADMIN.password);
      } catch {
        log('The super-admin has changed its first password; demo data was loaded before. Nothing to do.');
        return;
      }
    }
  }
  let admin = await login(ADMIN.user, ADMIN.password);
  // Prod: the services may still be starting when the Job runs.
  await until('the user service through the gateway', async () => {
    admin = await login(ADMIN.user, ADMIN.password);
    return listAll(admin, '/branches');
  });
  const ctx = await seedPeople(admin);
  await seedExtras(admin, ctx);
  await waitForReplicas(await login(ADMIN.user, ADMIN.password), ctx);
  await seedExams(await login(ADMIN.user, ADMIN.password), ctx);
  await seedAytExams(await login(ADMIN.user, ADMIN.password), ctx);
  await seedAttendance(await login(ADMIN.user, ADMIN.password), ctx);
  await seedTasks(ctx);

  step('Check');
  const fresh = await login(ctx.ayse.username, PASSWORD);
  const unread = await api(fresh, 'GET', '/notifications/unread-count');
  log(`Ayşe unread notifications: ${JSON.stringify(unread)}`);

  if (!LOCAL) await setRequiredActions(ADMIN.user, ['UPDATE_PASSWORD']);
  // Prod never prints the password (Secrets Manager dershane/demo/password); locally it is the known value.
  const shown = LOCAL ? PASSWORD : '<dershane/demo/password>';
  log('\nDone. Demo accounts (password ' + shown + '):');
  for (const t of TEACHERS) log(`  teacher  ${t.email}`);
  for (const room of ctx.classrooms) log(`  students ${room.name}: ${room.students.map((s) => s.username).join(', ')}`);
  log(`  guardian ${ctx.guardian.username}`);
  if (TEMP_STUDENT_NO) log(`  (student ${TEMP_STUDENT_NO} has the temporary password ${TEMP_PASSWORD})`);
  if (!LOCAL) log(`  super-admin ${ADMIN.user}: first password in dershane/keycloak/superadmin, changed at first login`);
}

main().catch((e) => {
  console.error(`\nSEED FAILED: ${e.message}`);
  process.exit(1);
});
