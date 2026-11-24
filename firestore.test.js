const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_author_123";
const BOB_UID = "bob_reader_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write books or subcollections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("books").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("books").doc("book_1").set({
      userId: ALICE_UID,
      title: "Livro Teste",
      subject: "Teologia",
      currentStep: 1,
      structureApproved: false,
      targetLanguage: "pt-BR",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, update metadata & layout, and delete BookProject; ghost fields and immutable changes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const bookRef = aliceDb.collection("users").doc(ALICE_UID).collection("books").doc("book_1");

  await assertSucceeds(
    bookRef.set({
      userId: ALICE_UID,
      title: "A Jornada da Palavra",
      subject: "Comunicação e Oratória",
      currentStep: 2,
      structureApproved: false,
      targetLanguage: "pt-BR",
      layoutPageSize: "A5",
      layoutFontFamily: "MERRIWEATHER",
      layoutFontSizePt: 11,
      layoutLineSpacing: 1.5,
      layoutMarginInnerMm: 20,
      layoutMarginOuterMm: 15,
      layoutParagraphIndentMm: 8,
      layoutJustified: true,
      layoutShowHeaders: true,
      layoutShowPageNumbers: true,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial metadata update succeeds
  await assertSucceeds(
    bookRef.update({
      title: "A Jornada da Palavra Viva",
      preface: "Prefácio editado pelo autor.",
      structureApproved: true,
      currentStep: 8,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Partial layout update succeeds
  await assertSucceeds(
    bookRef.update({
      layoutPageSize: "16x23",
      layoutFontSizePt: 12,
      layoutJustified: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field
  await assertFails(
    bookRef.update({
      isVerified: true,
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Reject mutating createdAt
  await assertFails(
    bookRef.update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject @DocumentId "id" key in create payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("books").doc("book_2").set({
      id: "book_2",
      userId: ALICE_UID,
      title: "Extra ID",
      subject: "Teste",
      currentStep: 1,
      structureApproved: false,
      targetLanguage: "pt-BR",
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user isolation: Bob cannot read or write Alice's books, recordings, or chapters", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db.collection("users").doc(ALICE_UID).collection("books").doc("book_1").set({
      userId: ALICE_UID,
      title: "Privado",
      subject: "Privado",
      currentStep: 1,
      structureApproved: false,
      targetLanguage: "pt-BR",
      createdAt: new Date(Date.now() - 5000),
      updatedAt: new Date(Date.now() - 5000),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("books").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("books").doc("book_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("books").doc("book_1").update({
      title: "Hackeado",
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Recordings, Chapters, Versions, and QC Reports follow parent-gate and immutability rules", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const bookRef = aliceDb.collection("users").doc(ALICE_UID).collection("books").doc("book_1");

  // Orphaned recording without existing parent book must fail
  await assertFails(
    bookRef.collection("recordings").doc("rec_1").set({
      userId: ALICE_UID,
      bookId: "book_1",
      title: "Aula 01",
      fileName: "aula01.mp3",
      mimeType: "audio/mpeg",
      sizeBytes: 1024000,
      orderIndex: 0,
      status: "PENDING",
      translateToPtBr: true,
      chunksCompleted: 0,
      totalChunks: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Create parent book
  await assertSucceeds(
    bookRef.set({
      userId: ALICE_UID,
      title: "Livro Completo",
      subject: "Educação",
      currentStep: 3,
      structureApproved: false,
      targetLanguage: "pt-BR",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Recording create & progress update succeed
  await assertSucceeds(
    bookRef.collection("recordings").doc("rec_1").set({
      userId: ALICE_UID,
      bookId: "book_1",
      title: "Aula 01",
      fileName: "aula01.mp3",
      mimeType: "audio/mpeg",
      sizeBytes: 1024000,
      orderIndex: 0,
      status: "PENDING",
      translateToPtBr: true,
      chunksCompleted: 0,
      totalChunks: 2,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    bookRef.collection("recordings").doc("rec_1").update({
      status: "COMPLETED",
      chunksCompleted: 2,
      detectedLanguage: "en",
      verbatimTranscript: "Texto fiel transcrito.",
      editorialTranscript: "Texto editorial lapidado.",
      keyTopics: ["Introdução", "Método"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Chapter create & edit succeed
  await assertSucceeds(
    bookRef.collection("chapters").doc("chap_1").set({
      userId: ALICE_UID,
      bookId: "book_1",
      chapterNumber: 1,
      title: "Capítulo 1: Fundamentos",
      content: "Primeiro parágrafo do livro.",
      orderIndex: 0,
      approved: false,
      needsReviewCount: 0,
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    bookRef.collection("chapters").doc("chap_1").update({
      approved: true,
      content: "Primeiro parágrafo revisado.",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Version snapshot create succeeds, update fails (immutable)
  await assertSucceeds(
    bookRef.collection("versions").doc("ver_1").set({
      userId: ALICE_UID,
      bookId: "book_1",
      label: "Versão 1",
      chapterCount: 1,
      wordCount: 3,
      chaptersSnapshot: [{ title: "Capítulo 1: Fundamentos", content: "Primeiro parágrafo revisado." }],
      createdAt: created,
    })
  );
  await assertFails(
    bookRef.collection("versions").doc("ver_1").update({ label: "Alterado" })
  );

  // QC Report create succeeds, update fails
  await assertSucceeds(
    bookRef.collection("qcReports").doc("qc_1").set({
      userId: ALICE_UID,
      bookId: "book_1",
      summary: "1 ponto requer revisão humana antes da publicação.",
      readyWithCaveats: true,
      issueCount: 1,
      issues: [{ severity: "WARNING", category: "TRECHO_REVISAO", location: "Cap. 1", message: "Conferir termo." }],
      createdAt: created,
    })
  );
  await assertFails(
    bookRef.collection("qcReports").doc("qc_1").update({ issueCount: 0 })
  );
});
