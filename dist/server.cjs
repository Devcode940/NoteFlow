var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
  // If the importer is in node compatibility mode or this is not an ESM
  // file that has been converted to a CommonJS file using a Babel-
  // compatible transform (i.e. "__esModule" has not been set), then set
  // "default" to the CommonJS "module.exports" for node compatibility.
  isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
  mod
));

// server.ts
var import_express = __toESM(require("express"), 1);
var import_path = __toESM(require("path"), 1);
var import_vite = require("vite");
var import_genai = require("@google/genai");
var import_dotenv = __toESM(require("dotenv"), 1);
var import_cors = __toESM(require("cors"), 1);
var import_express_rate_limit = __toESM(require("express-rate-limit"), 1);
import_dotenv.default.config();
var app = (0, import_express.default)();
var PORT = 3e3;
var allowedOrigins = process.env.ALLOWED_ORIGINS?.split(",") || ["http://localhost:3000", "http://localhost:5173"];
app.use((0, import_cors.default)({
  origin: function(origin, callback) {
    if (!origin) return callback(null, true);
    if (allowedOrigins.indexOf(origin) !== -1 || origin.endsWith(".vercel.app") || origin.endsWith(".netlify.app")) {
      callback(null, true);
    } else {
      callback(new Error("Not allowed by CORS"));
    }
  },
  credentials: true,
  methods: ["GET", "POST"],
  allowedHeaders: ["Content-Type", "Authorization"]
}));
var aiRateLimiter = (0, import_express_rate_limit.default)({
  windowMs: 60 * 1e3,
  // 1 minute
  max: 20,
  // Limit each IP to 20 requests per minute
  message: { error: "Too many requests, please try again later." },
  standardHeaders: true,
  legacyHeaders: false
});
var generateRateLimiter = (0, import_express_rate_limit.default)({
  windowMs: 60 * 1e3,
  // 1 minute
  max: 10,
  // Limit each IP to 10 requests per minute
  message: { error: "Too many generation requests, please try again later." },
  standardHeaders: true,
  legacyHeaders: false
});
app.use(import_express.default.json({ limit: "50mb" }));
var aiInstance = null;
function getGeminiClient() {
  if (!aiInstance) {
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      throw new Error("GEMINI_API_KEY environment variable is not configured. Please add it in the Secrets panel in Settings.");
    }
    aiInstance = new import_genai.GoogleGenAI({
      apiKey,
      httpOptions: {
        headers: {
          "User-Agent": "aistudio-build"
        }
      }
    });
  }
  return aiInstance;
}
async function generateAIContent({ systemInstruction, messages, temperature = 0.7 }) {
  const openRouterKey = process.env.OPENROUTER_API_KEY;
  if (openRouterKey) {
    const reqMessages = [];
    if (systemInstruction) {
      reqMessages.push({ role: "system", content: systemInstruction });
    }
    reqMessages.push(...messages);
    const res = await fetch("https://openrouter.ai/api/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${openRouterKey}`,
        "Content-Type": "application/json",
        "HTTP-Referer": "https://ai.studio",
        "X-Title": "NoteFlow"
      },
      body: JSON.stringify({
        model: "google/gemini-2.5-flash",
        // Default OpenRouter model
        messages: reqMessages,
        temperature
      })
    });
    if (!res.ok) {
      throw new Error(`OpenRouter API error: ${await res.text()}`);
    }
    const data = await res.json();
    return data.choices[0].message.content;
  }
  const ai = getGeminiClient();
  const contents = messages.map((m) => ({
    role: m.role === "assistant" ? "model" : "user",
    parts: [{ text: m.content }]
  }));
  const response = await ai.models.generateContent({
    model: "gemini-3.5-flash",
    contents,
    config: {
      systemInstruction,
      temperature
    }
  });
  return response.text;
}
app.get("/api/health", (req, res) => {
  res.json({ status: "healthy", timestamp: (/* @__PURE__ */ new Date()).toISOString() });
});
app.post("/api/ai/chat", aiRateLimiter, async (req, res) => {
  try {
    const { messages, systemContext } = req.body;
    if (!messages || !Array.isArray(messages)) {
      return res.status(400).json({ error: "Messages array is required." });
    }
    if (messages.length > 50) {
      return res.status(400).json({ error: "Maximum 50 messages allowed per request." });
    }
    for (const msg of messages) {
      if (!msg.content || typeof msg.content !== "string") {
        return res.status(400).json({ error: "Each message must have a string content." });
      }
      if (msg.content.length > 5e4) {
        return res.status(400).json({ error: "Message content exceeds maximum length of 50,000 characters." });
      }
    }
    const sanitizedContext = systemContext ? String(systemContext).substring(0, 1e4) : "";
    const systemInstruction = `You are a helpful NoteFlow AI Assistant. Adhere to formatting guidelines, keep answers highly readable. Use the user's active notes context if relevant:

${sanitizedContext}`;
    const responseText = await generateAIContent({
      systemInstruction,
      messages,
      temperature: 0.7
    });
    res.json({ content: responseText || "I was unable to formulate a response. Please try reframing your query." });
  } catch (err) {
    console.error("Error in /api/ai/chat:", err);
    res.status(500).json({ error: "An error occurred processing your request. Please try again later." });
  }
});
app.post("/api/ai/action", aiRateLimiter, async (req, res) => {
  try {
    const { action, text, instructions } = req.body;
    if (!text || !action) {
      return res.status(400).json({ error: "Text and action types are required." });
    }
    if (typeof text !== "string") {
      return res.status(400).json({ error: "Text must be a string." });
    }
    if (text.length > 1e5) {
      return res.status(400).json({ error: "Text exceeds maximum length of 100,000 characters." });
    }
    const validActions = ["autotitle", "summarize", "improve", "expand", "simplify", "translate", "fix", "tone", "bullets", "headline", "todos", "questions"];
    if (!validActions.includes(action)) {
      return res.status(400).json({ error: "Invalid action type." });
    }
    let sanitizedInstructions = "";
    if (instructions) {
      sanitizedInstructions = String(instructions).substring(0, 500);
    }
    let actionPrompt = "";
    switch (action) {
      case "autotitle":
        actionPrompt = "Generate a single, short, concise title (max 6 words) for this text. Do not include quotes or extra commentary. Return ONLY the chosen title and nothing else.";
        break;
      case "summarize":
        actionPrompt = "Create a clear, structured summary of this text with a few crisp key takeaways in bullet points.";
        break;
      case "improve":
        actionPrompt = "Proofread, refine of vocabulary, and improve clarity and professional flow. Preserve the general tone unless it is messy.";
        break;
      case "expand":
        actionPrompt = "Elaborate on this writing, expanding the concepts and providing more vivid details and paragraphs where relevant.";
        break;
      case "simplify":
        actionPrompt = "De-jargon and simplify this text to make it easy to understand for a general reader.";
        break;
      case "translate":
        const targetLang = sanitizedInstructions || "Spanish";
        actionPrompt = `Translate this text accurately into ${targetLang}, ensuring local idioms and formatting are natural.`;
        break;
      case "fix":
        actionPrompt = "Fix all grammatical, spelling, and structural punctuation issues. Return only the revised text with no external commentary.";
        break;
      case "tone":
        const targetTone = sanitizedInstructions || "Professional";
        actionPrompt = `Change the tone of this text to ${targetTone} style. Keep the core information intact.`;
        break;
      case "bullets":
        actionPrompt = "Convert this text block or list into clean, beautifully indented markdown bullet points.";
        break;
      case "headline":
        actionPrompt = "Create a set of a few catchy, relevant alternative headlines or titles based on this text.";
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
      messages: [
        { role: "user", content: `Instruction: ${actionPrompt}

Content to Process:
"""
${text}
"""` }
      ],
      temperature: 0.3
    });
    res.json({ result: responseText || "" });
  } catch (err) {
    console.error("Error in /api/ai/action:", err);
    res.status(500).json({ error: "An error occurred processing the action. Please try again later." });
  }
});
app.post("/api/ai/generate", generateRateLimiter, async (req, res) => {
  try {
    const { type, prompt, noteContext } = req.body;
    if (!prompt) {
      return res.status(400).json({ error: "Prompt/Goal is required." });
    }
    if (typeof prompt !== "string") {
      return res.status(400).json({ error: "Prompt must be a string." });
    }
    if (prompt.length > 1e4) {
      return res.status(400).json({ error: "Prompt exceeds maximum length of 10,000 characters." });
    }
    const validTypes = ["outline", "draft", "ideas", "continue"];
    if (type && !validTypes.includes(type)) {
      return res.status(400).json({ error: "Invalid generation type." });
    }
    let sanitizedContext = "";
    if (noteContext) {
      sanitizedContext = String(noteContext).substring(0, 2e4);
    }
    const generateInstruction = "";
    let actualInstruction = "";
    if (type === "outline") {
      actualInstruction = "Create a structured, detailed hierarchical markdown outline based on the prompt.";
    } else if (type === "draft") {
      actualInstruction = "Compose a solid initial draft with multiple detailed sections based on the prompt.";
    } else if (type === "ideas") {
      actualInstruction = "Generate a rich list of 10 creative, actionable brainstorming ideas, angles, or notes based on the prompt.";
    } else if (type === "continue") {
      actualInstruction = `Continue writing from the perspective of the existing note context.
Context Notes:
"""
${sanitizedContext}
"""`;
    } else {
      actualInstruction = "Write relevant document content based on the prompt.";
    }
    const responseText = await generateAIContent({
      systemInstruction: "You are a versatile content creator and documents specialist. Write ready-to-use, gorgeous, functional note sheets and outlines with elegant formatting. Do not include chat meta-commentary.",
      messages: [
        { role: "user", content: `Task requested: ${actualInstruction}

User Instruction/Topic:
"${prompt}"` }
      ],
      temperature: 0.7
    });
    res.json({ result: responseText || "" });
  } catch (err) {
    console.error("Error in /api/ai/generate:", err);
    res.status(500).json({ error: "An error occurred generating the content. Please try again later." });
  }
});
async function startServer() {
  if (process.env.NODE_ENV !== "production") {
    const vite = await (0, import_vite.createServer)({
      server: { middlewareMode: true },
      appType: "spa"
    });
    app.use(vite.middlewares);
  } else {
    const distPath = import_path.default.join(process.cwd(), "dist");
    app.use(import_express.default.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(import_path.default.join(distPath, "index.html"));
    });
  }
  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running in ${process.env.NODE_ENV || "development"} mode on http://localhost:${PORT}`);
  });
}
startServer();
//# sourceMappingURL=server.cjs.map
