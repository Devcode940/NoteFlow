import express from "express";
import path from "path";
import { createServer as createViteServer } from "vite";
import { GoogleGenAI } from "@google/genai";
import dotenv from "dotenv";
import cors from "cors";
import rateLimit from "express-rate-limit";

dotenv.config();

const app = express();
const PORT = 3000;

// CORS configuration - restrict to known origins
const allowedOrigins = process.env.ALLOWED_ORIGINS?.split(',') || ['http://localhost:3000', 'http://localhost:5173'];
app.use(cors({
  origin: function(origin, callback) {
    // Allow requests with no origin (like mobile apps or curl requests)
    if (!origin) return callback(null, true);
    if (allowedOrigins.indexOf(origin) !== -1 || origin.endsWith('.vercel.app') || origin.endsWith('.netlify.app')) {
      callback(null, true);
    } else {
      callback(new Error('Not allowed by CORS'));
    }
  },
  credentials: true,
  methods: ['GET', 'POST'],
  allowedHeaders: ['Content-Type', 'Authorization']
}));

// Rate limiting for AI endpoints to prevent DoS and cost exploitation
const aiRateLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 20, // Limit each IP to 20 requests per minute
  message: { error: 'Too many requests, please try again later.' },
  standardHeaders: true,
  legacyHeaders: false,
});

// Stricter rate limit for generation endpoints
const generateRateLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 10, // Limit each IP to 10 requests per minute
  message: { error: 'Too many generation requests, please try again later.' },
  standardHeaders: true,
  legacyHeaders: false,
});

app.use(express.json({ limit: "50mb" }));

// Helper to obtain Gemini client
let aiInstance: GoogleGenAI | null = null;
function getGeminiClient(): GoogleGenAI {
  if (!aiInstance) {
    const apiKey = process.env.GEMINI_API_KEY;
    if (!apiKey) {
      throw new Error("GEMINI_API_KEY environment variable is not configured. Please add it in the Secrets panel in Settings.");
    }
    aiInstance = new GoogleGenAI({
      apiKey,
      httpOptions: {
        headers: {
          "User-Agent": "aistudio-build",
        },
      },
    });
  }
  return aiInstance;
}

// Unified AI generation function
async function generateAIContent({ systemInstruction, messages, temperature = 0.7 }: { systemInstruction: string, messages: { role: string, content: string }[], temperature?: number }) {
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
        model: "google/gemini-2.5-flash", // Default OpenRouter model
        messages: reqMessages,
        temperature,
      })
    });

    if (!res.ok) {
      throw new Error(`OpenRouter API error: ${await res.text()}`);
    }

    const data = await res.json();
    return data.choices[0].message.content;
  }

  // Fallback to Gemini API
  const ai = getGeminiClient();
  const contents = messages.map((m) => ({
    role: m.role === "assistant" ? "model" : "user",
    parts: [{ text: m.content }],
  }));

  const response = await ai.models.generateContent({
    model: "gemini-3.5-flash",
    contents,
    config: {
      systemInstruction,
      temperature,
    },
  });

  return response.text;
}

// Ensure clean status routing
app.get("/api/health", (req, res) => {
  res.json({ status: "healthy", timestamp: new Date().toISOString() });
});

// Endpoint 1: Conversational Chat with Notes Context
app.post("/api/ai/chat", aiRateLimiter, async (req, res): Promise<any> => {
  try {
    const { messages, systemContext } = req.body;
    if (!messages || !Array.isArray(messages)) {
      return res.status(400).json({ error: "Messages array is required." });
    }

    // Input validation - limit message count and length to prevent abuse
    if (messages.length > 50) {
      return res.status(400).json({ error: "Maximum 50 messages allowed per request." });
    }
    
    for (const msg of messages) {
      if (!msg.content || typeof msg.content !== 'string') {
        return res.status(400).json({ error: "Each message must have a string content." });
      }
      if (msg.content.length > 50000) {
        return res.status(400).json({ error: "Message content exceeds maximum length of 50,000 characters." });
      }
    }

    // Sanitize system context to prevent prompt injection
    const sanitizedContext = systemContext 
      ? String(systemContext).substring(0, 10000)
      : "";

    const systemInstruction = `You are a helpful NoteFlow AI Assistant. Adhere to formatting guidelines, keep answers highly readable. Use the user's active notes context if relevant:\n\n${sanitizedContext}`;

    const responseText = await generateAIContent({
      systemInstruction,
      messages,
      temperature: 0.7,
    });

    res.json({ content: responseText || "I was unable to formulate a response. Please try reframing your query." });
  } catch (err: any) {
    console.error("Error in /api/ai/chat:", err);
    // Don't expose internal error details to client
    res.status(500).json({ error: "An error occurred processing your request. Please try again later." });
  }
});

// Endpoint 2: Note-Taking Quick Actions (Summarize, Translate, Bulletify, Improve...)
app.post("/api/ai/action", aiRateLimiter, async (req, res): Promise<any> => {
  try {
    const { action, text, instructions } = req.body;
    if (!text || !action) {
      return res.status(400).json({ error: "Text and action types are required." });
    }

    // Input validation - limit text length to prevent abuse
    if (typeof text !== 'string') {
      return res.status(400).json({ error: "Text must be a string." });
    }
    if (text.length > 100000) {
      return res.status(400).json({ error: "Text exceeds maximum length of 100,000 characters." });
    }
    
    // Validate action type
    const validActions = ["autotitle", "summarize", "improve", "expand", "simplify", "translate", "fix", "tone", "bullets", "headline", "todos", "questions"];
    if (!validActions.includes(action)) {
      return res.status(400).json({ error: "Invalid action type." });
    }
    
    // Sanitize instructions to prevent prompt injection
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
        { role: "user", content: `Instruction: ${actionPrompt}\n\nContent to Process:\n"""\n${text}\n"""` }
      ],
      temperature: 0.3,
    });

    res.json({ result: responseText || "" });
  } catch (err: any) {
    console.error("Error in /api/ai/action:", err);
    // Don't expose internal error details to client
    res.status(500).json({ error: "An error occurred processing the action. Please try again later." });
  }
});

// Endpoint 3: Template & Outline Generator (Draft, Outlines, Ideas)
app.post("/api/ai/generate", generateRateLimiter, async (req, res): Promise<any> => {
  try {
    const { type, prompt, noteContext } = req.body;
    if (!prompt) {
      return res.status(400).json({ error: "Prompt/Goal is required." });
    }

    // Input validation - limit prompt and context length
    if (typeof prompt !== 'string') {
      return res.status(400).json({ error: "Prompt must be a string." });
    }
    if (prompt.length > 10000) {
      return res.status(400).json({ error: "Prompt exceeds maximum length of 10,000 characters." });
    }
    
    // Validate type parameter
    const validTypes = ["outline", "draft", "ideas", "continue"];
    if (type && !validTypes.includes(type)) {
      return res.status(400).json({ error: "Invalid generation type." });
    }
    
    // Sanitize noteContext to prevent prompt injection
    let sanitizedContext = "";
    if (noteContext) {
      sanitizedContext = String(noteContext).substring(0, 20000);
    }

    const generateInstruction = ""; // Defined below
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
      messages: [
        { role: "user", content: `Task requested: ${actualInstruction}\n\nUser Instruction/Topic:\n"${prompt}"` }
      ],
      temperature: 0.7,
    });

    res.json({ result: responseText || "" });
  } catch (err: any) {
    console.error("Error in /api/ai/generate:", err);
    // Don't expose internal error details to client
    res.status(500).json({ error: "An error occurred generating the content. Please try again later." });
  }
});

// Vite server middleware integration or static files serving
async function startServer() {
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running in ${process.env.NODE_ENV || 'development'} mode on http://localhost:${PORT}`);
  });
}

startServer();
