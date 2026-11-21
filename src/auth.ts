import { initializeApp, cert, getApps } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { ServiceAccount } from "firebase-admin/app";

let adminInstance: ReturnType<typeof initializeApp> | null = null;

export const admin = {
  get auth() {
    if (!adminInstance) {
      // Optional: initialize with a service account when GOOGLE_APPLICATION_CREDENTIALS
      // or FIREBASE_SERVICE_ACCOUNT is set. Otherwise Firebase Admin uses the
      // default environment credentials (e.g. Cloud Run default service account).
      if (getApps().length === 0) {
        adminInstance = initializeApp();
      } else {
        adminInstance = getApps()[0];
      }
    }
    return getAuth(adminInstance);
  },
};

/** Verifies a Firebase ID token and returns the decoded claims. */
export async function verifyIdToken(idToken: string) {
  return admin.auth.verifyIdToken(idToken);
}
