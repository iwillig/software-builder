# Memory Decay and Recursive Consolidation

This document describes how memory decays over time and how the system uses human-in-the-loop review to create a recursive, self-organizing memory architecture.

## Core Philosophy

Our memory system mimics human cognition:

1. **Memories decay exponentially** when not accessed
2. **Review strengthens memories** and prevents decay
3. **Sleep (consolidation) reorganizes** short-term into long-term
4. **No memory is ever truly lost** - just harder to retrieve
5. **Humans and LLMs review memories together** to decide what matters

## The Forgetting Curve

Based on Ebbinghaus's research and Chessa & Murre's mathematical model:

### Decay Formula

```
recall_strength(t) = μ * e^(-a * t)
```

Where:
- `μ` (mu): Initial memory strength (0.1 to 1.0)
- `a` (alpha): Decay rate - how fast we forget
- `t`: Time since last recall/review
- `e`: Natural logarithm base (~2.718)

### Decay Rate by Memory Type

| Memory Type | Decay Rate (a) | Half-Life | Why |
|-------------|----------------|-----------|-----|
| **Raw Interaction** | 0.8 | ~1 day | Transient, detailed |
| **Episodic Summary** | 0.4 | ~2 days | Condensed experience |
| **Extracted Fact** | 0.2 | ~3 days | Core knowledge |
| **Procedural Pattern** | 0.1 | ~7 days | Strong utility |
| **User Preference** | 0.05 | ~14 days | Critical, rarely forgotten |

### Recall Probability

The probability of a memory being retrieved combines:

```
p_recall = f(recall_strength, semantic_relevance, context_match)
```

A memory is available for retrieval only if `p_recall > threshold`.

---

## Memory Consolidation Process

### The Review Cycle

Like human sleep, your agent goes through periodic "consolidation sessions" where you (the engineer) and the LLM review memories together:

```
┌─────────────────────────────────────────────────────────────┐
│                    CONSOLIDATION SESSION                    │
│                     (Triggered Periodically)                │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. PREPARATION                                             │
│     └─> System identifies memories needing review          │
│         - High decay memories (about to fall below threshold)│
│         - New memories (not yet consolidated)               │
│         - Random sampling (surprise recall)                 │
│                                                             │
│  2. HUMAN + AI REVIEW                                       │
│     └─> Present memories to user with LLM context           │
│         - "Remember when you worked on the auth system?"    │
│         - Show related code, decisions, lessons             │
│                                                             │
│  3. DECISION POINTS                                         │
│     ├─> KEEP (strengthens memory, reduces decay rate)        │
│     ├─> MERGE (combines with similar memories)              │
│     ├─> EXTRACT (pull out facts/patterns into new memory) │
│     └─> CONDEMN (archived but not deleted - can be revived) │
│                                                             │
│  4. HIERARCHICAL ORGANIZATION                               │
│     └─> Create summaries from reviewed interactions        │
│         - Episodes → Summaries → Themes → Archetypes       │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### Consolidation Triggers

| Trigger | When | Action |
|---------|------|--------|
| **Time-based** | Every 4 hours of active coding | Background consolidation |
| **Volume-based** | 50 new interactions accumulated | Prioritized review queue |
| **Session-end** | User ends session | Full consolidation pass |
| **Threshold alert** | Important memory decaying | Immediate attention |
| **Manual** | User requests review | Ad-hoc consolidation |

---

## Memory Hierarchy and Recursive Summarization

Memories exist in a recursive hierarchy - each level summarizes the level below:

### Level 0: Raw Interactions (Leaf Nodes)
```
{:interaction/id "int-001"
 :interaction/content "Let's add authentication to the API"
 :interaction/timestamp <timestamp>
 :interaction/embedding <vector>
 :memory/decay-rate 0.8
 :memory/last-recall <timestamp>
 :memory/strength-calculated <current-value>}
```

### Level 1: Episodes (Session Chunks)
```clojure
{:episode/id "ep-001"
 :episode/summary "Implemented JWT authentication middleware"
 :episode/interactions ["int-001" "int-002" "int-003"]
 :episode/key-facts [:fact/auth-pattern :fact/jwt-library]
 :episode/consolidated-from <timestamp>
 :episode/decay-rate 0.4  ;; Slower decay than raw
 :extracted-entities [:entity/auth-system :entity/clojure-ring]}
```

### Level 2: Themes (Cross-Episode Patterns)
```clojure
{:theme/id "theme-001"
 :theme/name "Authentication Patterns"
 :theme/description "Recurring approaches to auth across projects"
 :theme/episodes ["ep-001" "ep-005" "ep-012"]
 :theme/patterns [:pattern/jwt-middleware 
                  :pattern/session-store
                  :pattern/oauth-flow]
 :theme/decay-rate 0.2}
```

### Level 3: Archetypes (Fundamental Knowledge)
```clojure
{:archetype/id "arch-001"
 :archetype/name "Secure API Design"
 :archetype/principles [:principle/least-privilege
                        :principle/token-validation
                        :principle/refresh-strategy]
 :archetype/themes ["theme-001" "theme-008"]
 :archetype/decay-rate 0.05  ;; Very stable
 :archetype/lifetime :permanent}
```

### Recursive Consolidation Flow

```
Raw Interactions
       ↓ (consolidate)
   Episodes
       ↓ (summarize)
    Themes
       ↓ (abstract)
  Archetypes
```

Each consolidation pass:
1. Reviews memories at current level
2. Identifies connections and patterns
3. Creates higher-level abstractions
4. Updates decay rates (slower at higher levels)
5. Maintains links (episodic → theme → archetype)

---

## The Human-in-the-Loop Review Process

### Review Interface

During consolidation, the system presents:

```
┌────────────────────────────────────────────────────────────────┐
│ Memory Review - Session: "Auth Implementation"                  │
│                                                               │
│ MEMORY CANDIDATE:                                             │
│ ───────────────────────────────────────────────────────────── │
│ "Implemented JWT middleware in Clojure Ring"                  │
│ From: 3 days ago  |  Strength: 0.34 (decaying)               │
│                                                               │
│ Related to:                                                    │
│   • Middleware patterns (Theme)                               │
│   • API Security (Theme)                                      │
│   • Project: software-builder (Episode)                       │
│                                                               │
│ WHAT THE LLM LEARNED:                                         │
│ • You prefer buddy/core over jsonista for JWT                 │
│ • You like middleware composition over monolithic handlers    │
│ • You've used this pattern in 2 other projects                │
│                                                               │
│ [KEEP - Strengthen]  [MERGE - Similar to...]  [EXTRACT - Facts]│
│ [EDIT - Refine]     [SKIP - Let decay]        [ARCHIVE - Hide]│
└────────────────────────────────────────────────────────────────┘
```

### Decision Outcomes

#### 1. KEEP
- Memory strength reset to 1.0
- Decay rate reduced by 50%
- Becomes more persistent
- Future reviews less frequent

#### 2. MERGE
- Combine with existing similar memory
- New memory synthesized from both
- Originals linked as "sources"
- Decay rate based on combined importance

#### 3. EXTRACT
Create new distilled memories:
```clojure
{:fact/id "fact-001"
 :fact/content "User prefers buddy/core for JWT"
 :fact/source-memories ["int-001" "int-045"]
 :fact/decay-rate 0.1  ;; Very stable
 :fact/type :preference}
```

#### 4. ARCHIVE
- Reduced decay rate (0.9 - almost forgotten)
- Not actively retrieved
- But **never deleted**
- Can be "rediscovered" via surprise recall

---

## Surprise Recall

Even "forgotten" memories can resurface when contextually relevant.

### Mechanism

1. User mentions "authentication" in current conversation
2. Semantic search finds low-strength but related memories
3. `p_recall` temporarily boosted by context similarity
4. Memory surfaces: "Reminds me of when you..."

### Example

```
User: "I'm thinking about adding OAuth to this project"

Agent: "That reminds me - 3 months ago you implemented JWT 
        auth in software-builder. You preferred buddy/core 
        over other libraries. Would that pattern work here?"

[Memory retrieved despite decay - context match was strong enough]
```

---

## Implementation Schema

### Memory Entity with Decay

```clojure
{:memory/id <uuid>
 :memory/content "..."
 :memory/type :interaction | :episode | :theme | :archetype | :fact
 
 ;; Decay tracking
 :decay/initial-strength μ              ;; 0.1 to 1.0
 :decay/current-strength <calculated>   ;; μ * e^(-a * t)
 :decay/rate α                         ;; Per-type default
 :decay/last-reviewed <timestamp>
 :decay/review-count 5                  ;; More reviews = slower decay
 
 ;; Review history
 :review/records [{:timestamp <ts>
                   :decision :keep|:merge|:extract|:archive
                   :reviewer :human|:system
                   :notes "..."}]
 
 ;; Hierarchy links
 :hierarchy/level 0                     ;; 0=raw, 3=archetype
 :hierarchy/children [ids]             ;; For themes/episodes
 :hierarchy/parent <id>                ;; Link upward
 :hierarchy/sources [ids]              ;; Raw memories this came from
 
 ;; Retrieval metadata
 :retrieval/times-recalled 12
 :retrieval/contexts [:auth :clojure :middleware]
 :retrieval/embedding <vector>}
```

### Consolidation Queue

```clojure
;; Memories needing attention
{:queue/pending [{:memory/id "mem-001"
                  :priority :high        ;; Calculated
                  :urgency :decaying     ;; Why in queue
                  :suggested-action :review}]
 
 :queue/stats {:total-pending 47
               :by-urgency {:decaying 12
                            :new 30
                            :random-sample 5}}}
```

---

## Decay Visualization

### Strength Over Time

```
Strength
   1.0 ┤●
       ││\                    ● Keep (strengthened)
   0.8 ┤│ \                  /│
       ││  \                / │
   0.6 ┤│   \              /  │
       ││    \            /   │\  Archive (still decaying)
   0.4 ┤│     ●──────────┤    │ \
       ││    Extract     │    │  \●
   0.2 ┤│                │    │   │
       ││                │    │   │
   0.0 ┼┴────────────────┴────┴───┴──────
       t0     t1        t2   t3   t4
       
       [Interaction]    [Review]    [Archive]
```

### Review Impact

Each review extends the "half-life":

| Review Count | Effective Decay Rate | Memory Type |
|--------------|---------------------|-------------|
| 0 (initial)  | a = 0.8              | Fleeting    |
| 1            | a = 0.4              | Episodic    |
| 2            | a = 0.2              | Semantic    |
| 3+           | a = 0.05             | Permanent   |

---

## Practical Workflow

### Day 1: Active Coding
```
→ Raw interactions stored with high decay (0.8)
→ Working memory in context window
→ At 4 hours: "You have 23 memories to review"
```

### Day 2: Morning Consolidation
```
→ System presents 12 memories from yesterday
→ You + LLM decide: KEEP 5, MERGE 3, EXTRACT 4
→ 5 memories promoted to episodic level (decay: 0.4)
→ 4 facts created (decay: 0.1)
```

### Day 7: Pattern Recognition
```
→ System notices auth-related episodes across 3 projects
→ Proposes "Authentication Patterns" theme
→ You review and approve
→ Theme created (decay: 0.2)
```

### Day 30: Deep Learning
```
→ Multiple themes related to "API Design" detected
→ Proposes "Secure API Archetype" 
→ Becomes permanent knowledge (decay: 0.05)
→ Guides future auth implementations
```

---

## Key Principles

1. **Decay is not deletion** - Memories fade but can be rediscovered
2. **Review strengthens** - Each human review makes memories more permanent
3. **Hierarchy emerges** - Patterns self-organize into themes and archetypes
4. **Context triggers recall** - Semantic similarity resurrects "forgotten" memories
5. **Human judgment matters** - LLM suggests, human decides what to keep
6. **Recursive refinement** - Each level of abstraction is itself reviewed

---

## References

### Primary Research

1. **Hou, Y., Tamoto, H., & Miyashita, H. (2024).** "My agent understands me better": Integrating Dynamic Human-like Memory Recall and Consolidation in LLM-Based Agents. CHI Conference on Human Factors in Computing Systems (CHI EA '24), Honolulu, HI, USA.
   - *Key contribution*: Human-like memory recall triggered by contextual cues; mathematical consolidation model combining relevance, elapsed time, and recall frequency [1][2][3].

2. **Chessa, A., & Murre, J. M. J. (2007).** A neurocognitive model of long-term memory: The TECO model. *Journal of Mathematical Psychology, 51*(5), 343-357.
   - *Key contribution*: Mathematical model of recall probability using Poisson processes; exponential decay function: `r(t) = μ * e^(-a * t)` [4].

### Historical Foundations

3. **Ebbinghaus, H. (1885).** *Memory: A Contribution to Experimental Psychology*.
   - *Key contribution*: First empirical study of forgetting; forgetting curve showing exponential decay of memory retention over time [5][6].

### Memory Consolidation Theory

4. **Stickgold, R., & Walker, M. P. (2013).** Sleep-dependent memory consolidation and reconsolidation. *Sleep Medicine, 14*(12), 1111-1116.
   - *Key contribution*: Sleep-dependent memory consolidation enables transition from hippocampal to neocortical storage; systems consolidation theory [7][8].

5. **Rasch, B., & Born, J. (2013).** About sleep's role in memory. *Physiological Reviews, 93*(2), 681-766.
   - *Key contribution*: Experimental evidence for replay and reactivation during sleep supporting memory consolidation [9].

### Spaced Repetition Research

6. **Cepeda, N. J., Vul, E., Rohrer, D., Wixted, J. T., & Pashler, H. (2008).** Spacing effects in learning: A temporal ridgeline of optimal retention. *Psychological Science, 19*(11), 1095-1102.
   - *Key contribution*: Optimal intervals for spaced repetition; inverted-U curve for spacing effects [10][11].

7. **Cepeda, N. J., Coburn, N., Rohrer, D., Wixted, J. T., Mozer, M. C., & Pashler, H. (2009).** Optimizing distributed practice: Theoretical analysis and practical implications. *Experimental Psychology, 56*(4), 236-246.
   - *Key contribution*: Mathematical optimization of review intervals for maximum retention [12].

### Related AI Memory Systems

8. **Letta (2024).** Agent Memory: How to Build Agents that Learn and Remember. Letta Blog.
   - *Key contribution*: Working/long-term memory separation; context management patterns for LLM agents [13].

9. **Zhong, W., et al. (2023).** MemoryBank: Enhancing Large Language Models with Long-Term Memory. *arXiv preprint*.
   - *Key contribution*: Memory strength enhancement on recall; simulates human-like memory behavior [1][2].

10. **Packer, G., et al. (2024).** MemGPT: Towards LLMs as Operating Systems. *arXiv preprint*.
    - *Key contribution*: Memory tier architecture; virtualized context management analogous to OS virtual memory [14].

11. **Ge, P., et al. (2025).** SimpleMem: Efficient Lifelong Memory for LLM Agents. *arXiv preprint* (2601.02553).
    - *Key contribution*: Semantic structured compression; adaptive query-aware retrieval; recursive consolidation [15].

12. **Wang, Z., et al. (2024).** A-Mem: Agentic Memory for LLM Agents. *arXiv preprint* (2502.12110).
    - *Key contribution*: Zettelkasten-inspired knowledge networks; dynamic memory organization with links [16].

### Sleep and Memory Consolidation

13. **Rasch, B., & Born, J. (2019).** Rehearsal initiates systems memory consolidation, sleep makes it last. *Nature Reviews Neuroscience, 20*(8), 494-495.
    - *Key contribution*: Rehearsal during wakefulness initiates consolidation; sleep stabilizes these changes [17][18].

14. **Nader, K., & Hardt, O. (2009).** A single standard for memory: The case for reconsolidation. *Nature Reviews Neuroscience, 10*(3), 224-234.
    - *Key contribution*: Memory reconsolidation theory; memories become labile upon retrieval and can be strengthened or modified [19][20].

---

## Citation Links

- [1] https://arxiv.org/html/2404.00573v1 (Hou et al., CHI 2024)
- [2] https://www.emergentmind.com/topics/memory-mechanisms-in-llm-based-agents
- [3] http://arxiv.org/pdf/2404.00573.pdf
- [4] Chessa & Murre (2007) - Memory consolidation model using Poisson processes
- [5] https://pmc.ncbi.nlm.nih.gov/articles/PMC5476736/ (Ebbinghaus forgetting curve)
- [6] https://www.kognitivo.net/p/spaced-repetition (Forgetting curve explanations)
- [7] https://pmc.ncbi.nlm.nih.gov/articles/PMC4007033/ (Sleep-dependent consolidation)
- [8] https://pmc.ncbi.nlm.nih.gov/articles/PMC11416671/ (Sleep in children and aging)
- [9] https://pmc.ncbi.nlm.nih.gov/articles/PMC8139635/ (Sleep architecture and memory)
- [10] https://pmc.ncbi.nlm.nih.gov/articles/PMC5959224/ (Retrieval and sleep counteract forgetting)
- [11] https://blog.learnhall.com/2025/08/29/the-science-of-spaced-repetition/ (Spaced repetition overview)
- [12] Cepeda et al. (2008, 2009) - Spacing effects research
- [13] https://www.letta.com/blog/agent-memory (Letta memory architecture)
- [14] MemGPT - Operating system paradigm for LLM memory
- [15] https://www.alphaxiv.org/overview/2601.02553v1 (SimpleMem architecture)
- [16] https://arxiv.org/html/2502.12110v1 (A-Mem agentic memory)
- [17] https://pmc.ncbi.nlm.nih.gov/articles/PMC6482015/ (Rehearsal and consolidation)
- [18] https://www.frontiersin.org/journals/psychology/articles/10.3389/fpsyg.2016.01368/full (Sleep-dependent consolidation limits)
- [19] https://pmc.ncbi.nlm.nih.gov/articles/PMC5476736/ (Reconsolidation theory)
- [20] Reconsolidation as mechanism for memory updating
