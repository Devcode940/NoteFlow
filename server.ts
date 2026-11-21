import express from "express";
import path from "path";
import { GoogleGenAI } from "@google/genai";
import dotenv from "dotenv";
import cors from "cors";
import rateLimit from "express-rate-limit";
import { admin, verifyIdToken } from "./src/auth";

dotenv.config();

const app = express();
const PORT = Number(process.env.PORT || 3000);

// ---------------------------------------------------------------------------
// Security configuration
// ---------------------------------------------------------------------------

// Strict CORS allowlist. Android emulators (10.0.2.2) and real devices use
// no Origin header or the configured backend URL — both are permitted.
// Production deployments must set ALLOWED_ORIGINS to their exact domain(s).
const allowedOrigins = process.env.ALLOWED_ORIGINS
  ? process.env.ALLOWED_ORIGINS.split(",").map((s) => s.trim())
  : ["http://10.0.2.2:3000", "http://localhost:3000", "http://localhost:5173"];

app.use(cors({
  origin: function (origin, callback) {
    // Allow requests without Origin (mobile apps, curl, Postman).
    if (!origin) return callback(null, true);
    if (
      allowedOrigins.includes(origin) ||
      origin.endsWith(".vercel.app") ||
      origin.endsWith(".netlify.app")
    ) {
      callback(null, true);
    } else {
      callback(new Error("Not allowed by CORS"));
    }
  },
  credentials: true,
  methods: ["GET", "POST", "OPTIONS"],
  allowedHeaders: ["Content-Type", "Authorization"],
}));

// JSON body size limits per endpoint (applied below).
const jsonSmall = express.json({ limit: "64kb" });   // health
const jsonMedium = express.json({ limit: "512kb" }); // chat actions
const jsonLarge = express.json({ limit: "4mb" });    // chat with note context

// ---------------------------------------------------------------------------
// AI client (server-side only — the key never reaches the client)
// ---------------------------------------------------------------------------

let aiInstance: GoogleGenAI | null = null;
function getGeminiClient(): GoogleGenAI {
  if (!aiInstance) {
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      throw new Error("Missing GEMINI_API_KEY in .env");
    }
    aiInstance = new GoogleGenAI({
      apiKey,
      httpOptions: {
        headers: { "User-Agent": "noteflow-backend" },
      },
    });
  }
  return aiInstance;
}

// Per-user request counters (in-memory; use Redis in multi-instance deploys).
const userRequestCounts = new Map<string, { count: number; resetAt: number }>();
const AI_QUOTA_PER_MIN = 20;
const GENERATE_QUOTA_PER_MIN = 10;

function checkQuota(userId: string | undefined, limit: number): boolean {
  if (!userId) return true; // unauthenticated requests are not rate-limited by user
  const now = Date.now();
  let entry = userRequestCounts.get(userId);
  if (!entry || now > entry.resetAt) {
    entry = { count: 0, resetAt: now + 60 * 1000 };
    userRequestCounts.set(userId, entry);
  }
  entry.count++;
  return entry.count <= limit;
}

async function generateAIContent({ systemInstruction, messages, temperature = 0.7 }: {
  systemInstruction: string;
  messages: { role: string; content: string }[];
  temperature?: number;
}) {
  const openRouterKey = process.env.OPENROUTER_API_KEY;
  if (openRouterKey) {
    const reqMessages = [];
    if (systemInstruction) reqMessages.push({ role: "system", content: systemInstruction });
    reqMessages.push(...messages);

    const res = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${openRouterKey}`,
        "Content-Type": "application/json",
        "HTTP-Referer": "https://ai.studio",
        "X-Title": "NoteFlow",
      },
      body: JSON.stringify({
        model: "google/gemini-2.5-flash",
        messages: reqMessages,
        temperature,
      }),
    });

    if (!res.ok) {
      throw new Error(`OpenRouter API error: ${await res.text()}`);
    }

    const data = await res.json();
    return data.choices[0]?.message?.content ?? "";
  }

  const ai = getGeminiClient();
  const contents = messages.map((m) => ({
    role: m.role === "assistant" ? "model" : "user",
    parts: [{ text: m.content }],
  }));

  const response = await ai.models.generateContent({
    model: "gemini-2.5-flash",
    contents,
    config: { systemInstruction, temperature },
  });

  return response.text ?? "";
}

// ---------------------------------------------------------------------------
// Auth middleware: verifies Firebase ID token on every AI endpoint
// ---------------------------------------------------------------------------

async function requireAuth(req: express.Request, res: express.Response, next: express.NextFunction) {
  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith("Bearer ")) {
    return res.status(401).json({ error: "Authorization: Bearer <Firebase ID token> required" });
  }
  const idToken = authHeader.substring(7);
  try {
    const decoded = await verifyIdToken(idToken);
    (req as any).user = { uid: decoded.uid, email: decoded.email };
    next();
  } catch (err) {
    return res.status(401).json({ error: "Invalid or expired Firebase ID token" });
  }
}

// ---------------------------------------------------------------------------
// Health check (no auth)
// ---------------------------------------------------------------------------

app.get("/api/health", jsonSmall, (_req, res) => {
  res.json({ status: "healthy", timestamp: new Date().toISOString() });
});

// ---------------------------------------------------------------------------
// Chat endpoint (non-streaming, authenticated)
// ---------------------------------------------------------------------------

app.post("/api/ai/chat", jsonMedium, requireAuth, async (req, res) => {
  const userId = (req as any).user?.uid;
  if (!checkQuota(userId, AI_QUOTA_PER_MIN)) {
    return res.status(429).json({ error: "AI chat quota exceeded. Try again later." });
  }

  try {
    const { messages, systemContext } = req.body as {
      messages?: { role: string; content: string }[];
      systemContext?: string;
    };
    if (!messages || !Array.isArray(messages)) {
      return res.status(400).json({ error: "Messages array is required." });
    }
    if (messages.length > 50) {
      return res.status(400).json({ error: "Too many messages (max 50)" });
    }
    for (const msg of messages) {
      if (!msg.content || typeof msg.content !== "string") {
        return res.status(400).json({ error: "Message must be a string" });
      }
      if (msg.content.length > 50000) {
        return res.status(400).json({ error: "Message too long (max 50k chars)" });
      }
    }

    const sanitizedContext = systemContext ? String(systemContext).substring(0, 10000) : "";
    const systemInstruction = `You are a helpful assistant. Use the user's active notes context if relevant:\n\n${sanitizedContext}`;

    const responseText = await generateAIContent({ systemInstruction, messages, temperature: 0.7 });
    res.json({ content: responseText || "I was unable to formulate a response. Please try reframing your query." });
  } catch (err: any) {
    console.error("Error in chat endpoint:", err);
    res.status(500).json({ error: "Something went wrong, please try again." });
  }
});

// ---------------------------------------------------------------------------
// Chat streaming endpoint (SSE, authenticated)
//   Accepts the same body as /api/ai/chat. Streams tokens as
//   `data: <chunk>` lines, terminated by `data: [DONE]`.
//   Client cancels the HTTP call to abort generation.
// ---------------------------------------------------------------------------

app.post("/api/ai/chat/stream", jsonMedium, requireAuth, async (req, res) => {
  const userId = (req as any).user?.uid;
  if (!checkQuota(userId, AI_QUOTA_PER_MIN)) {
    return res.status(429).json({ error: "AI chat quota exceeded. Try again later." });
  }

  try {
    const { messages, systemContext } = req.body as {
      messages?: { role: string; content: string }[];
      systemContext?: string;
    };
    if (!messages || !Array.isArray(messages)) {
      return res.status(400).json({ error: "Messages array is required." });
    }

    const sanitizedContext = systemContext ? String(systemContext).substring(0, 10000) : "";
    const systemInstruction = `You are a helpful assistant. Use the user's active notes context if relevant:\n\n${sanitizedContext}`;

    res.setHeader("Content-Type", "text/event-stream");
    res.setHeader("Cache-Control", "no-cache, no-transform");
    res.setHeader("X-Accel-Buffering", "no"); // disable nginx buffering
    res.write("data: {\"type\":\"start\"}\n\n");

    const openRouterKey = process.env.OPENROUTER_API_KEY;
    let done = false;

    if (openRouterKey) {
      // OpenRouter does not expose native streaming in this snippet; fall back
      // to a single complete response framed as SSE.
      const reqMessages = [];
      if (systemInstruction) reqMessages.push({ role: "system", content: systemInstruction });
      reqMessages.push(...messages);

      const streamRes = await fetch("https://openrouter.ai/api/v1/chat/completions", {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${openRouterKey}`,
          "Content-Type": "application/json",
          "HTTP-Referer": "https://ai.studio",
          "X-Title": "NoteFlow",
        },
        body: JSON.stringify({
          model: "google/gemini-2.5-flash",
          messages: reqMessages,
          temperature: 0.7,
          stream: true,
        }),
      });

      if (!streamRes.ok) {
        throw new Error(`OpenRouter API error: ${await streamRes.text()}`);
      }

      const reader = streamRes.body?.getReader();
      if (!reader) throw new Error("No response body from OpenRouter");

      const decoder = new TextDecoder();
      while (!done) {
        const { value, done: d } = await reader.read();
        done = d;
        if (value) {
          const chunk = decoder.decode(value);
          for (const line of chunk.split("\n")) {
            if (line.startsWith("data: ")) {
              const payload = line.slice(6).trim();
              if (payload === "[DONE]") { done = true; break; }
              try {
                const parsed = JSON.parse(payload);
                const token = parsed.choices?.[0]?.delta?.content ?? "";
                if (token) {
                  res.write(`data: ${JSON.stringify(token)}\n\n`);
                }
              } catch {
                // ignore unparseable lines
              }
            }
          }
        }
      }
    } else {
      const ai = getGeminiClient();
      const contents = messages.map((m) => ({
        role: m.role === "assistant" ? "model" : "user",
        parts: [{ text: m.content }],
      }));

      // Gemini streaming via generateContentStream
      const streamingResponse = await ai.models.generateContentStream({
        model: "gemini-2.5-flash",
        contents,
        config: { systemInstruction, temperature: 0.7 },
      });

      for await (const chunk of streamingResponse) {
        if (done) break;
        const token = chunk.text ?? "";
        if (token) {
          res.write(`data: ${JSON.stringify(token)}\n\n`);
        }
      }
    }

    if (!done) {
      res.write("data: [DONE]\n\n");
    }
    res.end();
  } catch (err: any) {
    console.error("Error in chat stream endpoint:", err);
    if (!res.headersSent) {
      res.status(500).json({ error: "Streaming failed" });
    } else {
      try { res.write(`data: ${JSON.stringify({ error: err.message })}\n\n`); res.write("data: [DONE]\n\n"); res.end(); } catch { /* ignore */ }
    }
  }
});

// ---------------------------------------------------------------------------
// Action endpoint (12 quick actions, authenticated)
// ---------------------------------------------------------------------------

app.post("/api/ai/action", jsonMedium, requireAuth, async (req, res) => {
  const userId = (req as any).user?.uid;
  if (!checkQuota(userId, AI_QUOTA_PER_MIN)) {
    return res.status(429).json({ error: "AI action quota exceeded. Try again later." });
  }

  try {
    const { action, text, instructions } = req.body as {
      action?: string;
      text?: string;
      instructions?: string;
    };
    if (!text || !action) {
      return res.status(400).json({ error: "Text and action types are required." });
    }
    if (typeof text !== "string") {
      return res.status(400).json({ error: "Text must be a string" });
    }
    if (text.length > 100000) {
      return res.status(400).json({ error: "Text too long (max 100k chars)" });
    }

    const validActions = [
      "autotitle", "summarize", "improve", "expand", "simplify", "translate",
      "fix", "tone", "bullets", "headline", "todos", "questions",
    ];
    if (!validActions.includes(action)) {
      return res.status(400).json({ error: "Invalid action" });
    }

    let sanitizedInstructions = "";
    if (instructions) sanitizedInstructions = String(instructions).substring(0, 500);

    let actionPrompt = "";
    switch (action) {
      case "autotitle":
        actionPrompt = "Generate a single, short, concise title (max 6 words) for this text. Do not include quotes or extra commentary. Return ONLY the chosen title and nothing else.";
        break;
      case "summarize":
        actionPrompt = "Create a clear, structured summary of this text with a few crisp key takeaways in bullet points.";
        break;
      case "improve":
        actionPrompt = "Proofread, refine vocabulary, and improve clarity and professional flow. Preserve the general tone unless it is messy.";
        break;
      case "expand":
        actionPrompt = "Elaborate on this writing, expanding the concepts and providing more vivid details and paragraphs where relevant.";
        break;
      case "simplify":
        actionPrompt = "De-jargon and simplify this text to make it easy to understand for a general reader.";
        break;
      case "translate":
        actionPrompt = `Translate this text accurately into ${sanitizedInstructions || "Spanish"}, ensuring local idioms and formatting are natural.`;
        break;
      case "fix":
        actionPrompt = "Fix all grammatical, spelling, and structural punctuation issues. Return only the revised text with no external commentary.";
        break;
      case "tone":
        actionPrompt = `Change the tone of this text to ${sanitizedInstructions || "Professional"} style. Keep the core information intact.`;
        break;
      case "bullets":
        actionPrompt = "Convert this text block or list into clean, beautifully indented markdown bullet points.";
        break;
      case "headline":
        actionPrompt = "Create a few catchy, relevant alternative headlines or titles based on this text.";
        break;
      case "todos":
        actionPrompt = "Extract all implicit or explicit actionable tasks, to-dos, and next steps from this text and format them as a markdown checklist (- [ ] Task).";
        break;
      case "questions":
        actionPrompt = "Generate 3-5 thought-provoking reflection questions or test points based on this material.";
        break;
      default:
        actionPrompt = "Analyze and optimize this text.";
    }

    const systemInstruction = "You are an elite, professional text editor and copywriter. Return ONLY the processed text with beautiful, valid markdown, and exclude any introductory greetings or chat meta-commentary.";
    const responseText = await generateAIContent({
      systemInstruction,
      messages: [{ role: "user", content: `Instruction: ${actionPrompt}\n\nContent to Process:\n"""\n${text}\n"""` }],
      temperature: 0.3,
    });

    res.json({ result: responseText || "" });
  } catch (err: any) {
    console.error("Error in action endpoint:", err);
    res.status(500).json({ error: "Something went wrong with that action" });
  }
});

// ---------------------------------------------------------------------------
// Generate endpoint (4 generators, authenticated)
// ---------------------------------------------------------------------------

app.post("/api/ai/generate", jsonLarge, requireAuth, async (req, res) => {
  const userId = (req as any).user?.uid;
  if (!checkQuota(userId, GENERATE_QUOTA_PER_MIN)) {
    return res.status(429).json({ error: "AI generate quota exceeded. Try again later." });
  }

  try {
    const { type, prompt, noteContext } = req.body as {
      type?: string;
      prompt?: string;
      noteContext?: string;
    };
    if (!prompt) {
      return res.status(400).json({ error: "Prompt/Goal is required." });
    }
    if (typeof prompt !== "string") {
      return res.status(400).json({ error: "Prompt must be a string" });
    }
    if (prompt.length > 10000) {
      return res.status(400).json({ error: "Prompt too long (max 10k chars)" });
    }

    const validTypes = ["outline", "draft", "ideas", "continue"];
    if (type && !validTypes.includes(type)) {
      return res.status(400).json({ error: "Invalid type" });
    }

    let sanitizedContext = "";
    if (noteContext) sanitizedContext = String(noteContext).substring(0, 20000);

    let actualInstruction = "";
    if (type === "outline") {
      actualInstruction = "Create a structured, detailed hierarchical markdown outline based on the prompt.";
    } else if (type === "draft") {
      actualInstruction = "Compose a solid initial draft with multiple detailed sections based on the prompt.";
    } else if (type === "ideas") {
      actualInstruction = "Generate a rich list of 10 creative, actionable brainstorming ideas, angles, or notes based on the prompt.";
    } else if (type === "continue") {
      actualInstruction = `Continue writing from the perspective of the existing note context.\nContext Notes:\n"""\n${sanitizedContext}\n"""`;
    } else {
      actualInstruction = "Write relevant document content based on the prompt.";
    }

    const responseText = await generateAIContent({
      systemInstruction: "You are a versatile content creator and documents specialist. Write ready-to-use, gorgeous, functional note sheets and outlines with elegant formatting. Do not include chat meta-commentary.",
      messages: [{ role: "user", content: `Task requested: ${actualInstruction}\n\nUser Instruction/Topic:\n"${prompt}"` }],
      temperature: 0.7,
    });

    res.json({ result: responseText || "" });
  } catch (err: any) {
    console.error("Error in generate endpoint:", err);
    res.status(500).json({ error: "Something went wrong generating content" });
  }
});

// ---------------------------------------------------------------------------
// Production static serving
// ---------------------------------------------------------------------------

function startServer() {
  if (process.env.NODE_ENV === "production") {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (_req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
    app.listen(PORT, "0.0.0.0", () => {
      console.log(`Server running in production mode on http://0.0.0.0:${PORT}`);
    });
  } else {
    app.listen(PORT, "0.0.0.0", () => {
      console.log(`API server running on http://0.0.0.0:${PORT}`);
    });
  }
}

startServer();

export {};
