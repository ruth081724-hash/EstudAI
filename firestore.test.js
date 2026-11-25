const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_1";
const BOB_UID = "bob_student_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/homeworkTasks`).get());
  await assertFails(unauthDb.collection(`users/${ALICE_UID}/conversations`).get());
  await assertFails(
    unauthDb.collection(`users/${ALICE_UID}/homeworkTasks`).doc("task_1").set({
      userId: ALICE_UID,
      title: "Ensayo",
      subject: "Historia",
      notes: "",
      priority: "HIGH",
      dueAtMillis: Date.now() + 86400000,
      reminderEnabled: true,
      reminderAtMillis: Date.now() + 3600000,
      completed: false,
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, list, partially update, and delete homework tasks", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const pastTime = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/homeworkTasks`).doc("task_1").set({
      userId: ALICE_UID,
      title: "Ejercicios de derivadas",
      subject: "Matemáticas",
      notes: "Páginas 42 a 45",
      priority: "HIGH",
      dueAtMillis: Date.now() + 86400000,
      reminderEnabled: true,
      reminderAtMillis: Date.now() + 3600000,
      completed: false,
      createdAt: pastTime,
      updatedAt: pastTime,
    })
  );

  // List query matching Kotlin observeHomeworkTasks()
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/homeworkTasks`).get()
  );

  // Partial update preserving createdAt and userId
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/homeworkTasks`).doc("task_1").update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdAt tamper
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/homeworkTasks`).doc("task_1").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject unexpected @DocumentId `id` key on create
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/homeworkTasks`).doc("task_2").set({
      id: "task_2",
      userId: ALICE_UID,
      title: "Con ID extra",
      subject: "Física",
      notes: "",
      priority: "MEDIUM",
      dueAtMillis: Date.now() + 86400000,
      reminderEnabled: false,
      reminderAtMillis: 0,
      completed: false,
      createdAt: pastTime,
      updatedAt: pastTime,
    })
  );

  // Reject invalid priority enum
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/homeworkTasks`).doc("task_3").set({
      userId: ALICE_UID,
      title: "Prioridad inválida",
      subject: "Física",
      notes: "",
      priority: "URGENT",
      dueAtMillis: Date.now() + 86400000,
      reminderEnabled: false,
      reminderAtMillis: 0,
      completed: false,
      createdAt: pastTime,
      updatedAt: pastTime,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/homeworkTasks`).doc("task_1").delete()
  );
});

test("Cross-user access to homework, conversations, flashcards, and sessions is denied", async () => {
  const pastTime = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection(`users/${BOB_UID}/homeworkTasks`).doc("bob_hw").set({
      userId: BOB_UID,
      title: "Tarea de Bob",
      subject: "Química",
      notes: "",
      priority: "LOW",
      dueAtMillis: Date.now() + 86400000,
      reminderEnabled: false,
      reminderAtMillis: 0,
      completed: false,
      createdAt: pastTime,
      updatedAt: pastTime,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection(`users/${BOB_UID}/homeworkTasks`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/homeworkTasks`).doc("bob_hw").get());
  await assertFails(
    aliceDb.collection(`users/${BOB_UID}/homeworkTasks`).doc("bob_hw").update({
      completed: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(aliceDb.collection(`users/${BOB_UID}/conversations`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/flashcardDecks`).get());
  await assertFails(aliceDb.collection(`users/${BOB_UID}/studySessions`).get());
});

test("Owner can create conversations and messages, and messages require existing parent", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const pastTime = new Date(Date.now() - 5000);

  // Message without parent conversation must fail
  await assertFails(
    aliceDb
      .collection(`users/${ALICE_UID}/conversations/missing_conv/messages`)
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        role: "user",
        text: "Hola",
        modelUsed: "gemini-3.8-flash",
        hasImage: false,
        createdAt: pastTime,
      })
  );

  // Create conversation
  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/conversations`).doc("conv_1").set({
      userId: ALICE_UID,
      title: "Dudas de Cálculo",
      modelId: "gemini-3.8-flash",
      studyMode: "TUTOR",
      lastMessagePreview: "¿Cómo derivo x^2?",
      createdAt: pastTime,
      updatedAt: pastTime,
    })
  );

  // Create message inside conversation
  await assertSucceeds(
    aliceDb
      .collection(`users/${ALICE_UID}/conversations/conv_1/messages`)
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        role: "user",
        text: "¿Cómo derivo x^2?",
        modelUsed: "gemini-3.8-flash",
        hasImage: false,
        createdAt: pastTime,
      })
  );

  // Reject disallowed modelId (e.g. paid model)
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/conversations`).doc("conv_2").set({
      userId: ALICE_UID,
      title: "Modelo no permitido",
      modelId: "gemini-4-argon",
      studyMode: "TUTOR",
      lastMessagePreview: "",
      createdAt: pastTime,
      updatedAt: pastTime,
    })
  );
});

test("Owner can create flashcard decks and study sessions with valid bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const pastTime = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/flashcardDecks`).doc("deck_1").set({
      userId: ALICE_UID,
      title: "Fotosíntesis",
      subject: "Biología",
      cards: [
        {
          question: "¿Qué orgánulo realiza la fotosíntesis?",
          answer: "El cloroplasto.",
          hint: "Contiene clorofila",
          masteryLevel: 0,
        },
      ],
      createdAt: pastTime,
      updatedAt: pastTime,
    })
  );

  await assertSucceeds(
    aliceDb.collection(`users/${ALICE_UID}/studySessions`).doc("sess_1").set({
      userId: ALICE_UID,
      technique: "FEYNMAN",
      topic: "Leyes de Newton",
      durationMinutes: 15,
      scoreOrRating: 85,
      summary: "Explicación clara de la segunda ley.",
      createdAt: pastTime,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection(`users/${ALICE_UID}/studySessions`).doc("sess_future").set({
      userId: ALICE_UID,
      technique: "POMODORO",
      topic: "Historia",
      durationMinutes: 25,
      scoreOrRating: 100,
      summary: "",
      createdAt: new Date(Date.now() + 60000),
    })
  );
});
