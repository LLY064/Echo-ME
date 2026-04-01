# Echo-ME: On-Device Intelligent Q&A App

A Kotlin + Jetpack Compose Android application featuring dual-memory architecture, multi-agent collaboration, and local model inference for on-device RAG (Retrieval-Augmented Generation).

## Project Structure

```
Echo-ME/
├── app/src/main/java/com/ml/shubham0204/docqa/
│   ├── data/                    # Data Layer
│   │   ├── DataModels.kt        # Core entities (Chunk, Document)
│   │   ├── LLMManager.kt        # Local GGUF model manager
│   │   ├── DualMemoryData.kt    # PFM/PEK data models
│   │   ├── DualMemoryDB.kt      # SQLite dual-memory storage
│   │   └── DualMemoryManager.kt # Dual-memory manager
│   │
│   ├── domain/                  # Domain Layer
│   │   ├── SentenceEmbeddingProvider.kt  # Vector encoding
│   │   ├── agents/              # Multi-agent system
│   │   │   ├── MemoryAgent.kt
│   │   │   ├── GenerationAgent.kt
│   │   │   └── MultiAgentOrchestrator.kt
│   │   └── cache/
│   │       └── HierarchicalCache.kt # L1/L2 hierarchical cache
│   │
│   ├── ui/                       # UI Layer
│   │   ├── MainActivity.kt       # Navigation
│   │   ├── theme/               # Theme (Color, Theme, Type)
│   │   ├── components/          # AppDialog, ProgressDialog
│   │   └── screens/
│   │       ├── chat/           # Main chat screen
│   │       ├── docs/           # Document management
│   │       ├── echome/          # Echo-ME advanced inference
│   │       ├── naiverag/        # Naive RAG benchmark
│   │       └── edgerag/         # Edge RAG benchmark
│   │
│   └── di/
│       └── AppModule.kt          # Koin DI module
```

## Core Modules

| Module | Description |
|--------|-------------|
| **LLMManager** | Unified local GGUF model loading & inference |
| **DualMemory** | PFM (Plain Fact Memory) + PEK (Experience Knowledge) dual storage |
| **MemoryAgent** | Extracts facts and policies from conversations |
| **GenerationAgent** | RAG retrieval and response generation |
| **HierarchicalCache** | Query-driven L1/L2 hierarchical caching |

## Tech Stack

- **UI**: Jetpack Compose + Material 3
- **DI**: Koin
- **DB**: ObjectBox (vectors) + SQLite (dual-memory)
- **LLM**: SmolLM (local GGUF)
- **Embedding**: ONNX Sentence Transformer

## Build Steps

### 1. Prerequisites
- JDK 17
- Android Studio (latest)
- Android SDK (API 34)

### 2. Clone Project
```bash
git clone <repository-url>
cd Echo-ME
```

### 3. Open in Android Studio
1. Open Android Studio
2. File → Open → Select project root directory
3. Wait for Gradle sync to complete

### 4. Build Debug APK
```bash
./gradlew assembleDebug
```

Or in Android Studio:
- Build → Build Bundle(s) / APK(s) → Build APK(s)

### 5. Run
1. Connect Android device or start emulator
2. Run → Run 'app'

## Usage Flow

1. **Add Documents** → Import PDF/DOCX/TXT/MD in Documents page
2. **Load Model** → Select GGUF model file in Echo-ME page
3. **Ask Questions** → Enter questions in main chat, get responses from local model

## Dependencies

- `app/libs/sentence_embeddings.aar` - Embedding library
- `app/src/main/assets/all-MiniLM-L6-V2.onnx` - Embedding model
- `app/src/main/assets/tokenizer.json` - Tokenizer config

## Notes

- Model files must be selected manually (supports .gguf format)
- Model must be loaded in Echo-ME page before first use
- Document vectors are stored in ObjectBox database
- Dual-memory data is stored in SQLite