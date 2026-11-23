const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validFormPayload(uid, timestampDate) {
  return {
    userId: uid,
    title: "Product Feedback Pulse",
    description: "Quick mobile-first check-in.",
    category: "FEEDBACK",
    accentColorKey: "INDIGO",
    acceptingResponses: true,
    questions: [
      {
        id: "q1",
        prompt: "How smooth was the gesture flow?",
        type: "STAR_RATING",
        required: true,
        options: [],
        scaleMin: 1,
        scaleMax: 5,
        minLabel: "Rough",
        maxLabel: "Effortless",
      },
    ],
    responseCount: 0,
    createdAt: timestampDate,
    updatedAt: timestampDate,
  };
}

function validResponsePayload(uid, formId, timestampDate) {
  return {
    userId: uid,
    formId: formId,
    respondentName: "Alex Rivera",
    answers: [
      {
        questionId: "q1",
        questionType: "STAR_RATING",
        textValue: "",
        numericValue: 5,
        selectedOptions: [],
      },
    ],
    completionSeconds: 14,
    submittedOffline: false,
    submittedAt: timestampDate,
  };
}

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

test("Unauthenticated user: cannot read or write forms or responses", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 5000);

  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("forms").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("forms")
      .doc("form_1")
      .set(validFormPayload(ALICE_UID, past))
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("forms")
      .doc("form_1")
      .collection("responses")
      .get()
  );
});

test("Authenticated owner: valid form create, list, partial update, and response submission succeed", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const formsRef = aliceDb.collection("users").doc(ALICE_UID).collection("forms");

  // Empty list query succeeds without undefined existing() property errors
  await assertSucceeds(formsRef.get());

  // Create form matching Kotlin payload (no `id` field)
  await assertSucceeds(
    formsRef.doc("form_1").set(validFormPayload(ALICE_UID, created))
  );

  // Partial update of mutable fields while preserving createdAt and userId
  await assertSucceeds(
    formsRef.doc("form_1").update({
      title: "Updated Pulse Survey",
      acceptingResponses: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Create response under existing form
  const responsesRef = formsRef.doc("form_1").collection("responses");
  await assertSucceeds(responsesRef.get());
  await assertSucceeds(
    responsesRef
      .doc("resp_1")
      .set(validResponsePayload(ALICE_UID, "form_1", new Date(Date.now() - 1000)))
  );

  // Increment responseCount atomically via update
  await assertSucceeds(
    formsRef.doc("form_1").update({
      responseCount: 1,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Security bounds: reject cross-user access, ghost fields, future timestamps, immutable edits, and orphan responses", async () => {
  const created = new Date(Date.now() - 10000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(BOB_UID)
      .collection("forms")
      .doc("bob_form")
      .set(validFormPayload(BOB_UID, created));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const aliceForms = aliceDb.collection("users").doc(ALICE_UID).collection("forms");

  // Cross-user isolation
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("forms").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("forms")
      .doc("bob_form")
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("forms")
      .doc("bob_form")
      .update({ title: "Hijacked" })
  );

  // Ghost field (`id` or `isAdmin`) must be rejected
  await assertFails(
    aliceForms.doc("form_ghost").set({
      ...validFormPayload(ALICE_UID, created),
      id: "form_ghost",
    })
  );

  // Future timestamp tampering must be rejected
  await assertFails(
    aliceForms
      .doc("form_future")
      .set(validFormPayload(ALICE_UID, new Date(Date.now() + 60000)))
  );

  // Orphan response (non-existent parent form) must be rejected
  await assertFails(
    aliceForms
      .doc("non_existent_form")
      .collection("responses")
      .doc("resp_orphan")
      .set(
        validResponsePayload(
          ALICE_UID,
          "non_existent_form",
          new Date(Date.now() - 1000)
        )
      )
  );

  // Immutable createdAt mutation on existing form must be rejected
  await assertSucceeds(
    aliceForms.doc("form_valid").set(validFormPayload(ALICE_UID, created))
  );
  await assertFails(
    aliceForms.doc("form_valid").update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Updating an immutable response must be rejected
  await assertSucceeds(
    aliceForms
      .doc("form_valid")
      .collection("responses")
      .doc("resp_1")
      .set(
        validResponsePayload(
          ALICE_UID,
          "form_valid",
          new Date(Date.now() - 1000)
        )
      )
  );
  await assertFails(
    aliceForms
      .doc("form_valid")
      .collection("responses")
      .doc("resp_1")
      .update({ respondentName: "Tampered" })
  );
});
