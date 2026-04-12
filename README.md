# TAS

### This project was completed by Fayez ALHAJJ and Aymane RYANY as part of the TAS module for the Master 2 STL at Sorbonne University (2025/2026).

# Static Analysis Domains — LiSA Tutorial

This project implements three abstract domains for static program analysis using the [LiSA](https://github.com/lisa-analyzer/lisa) framework (Library for Static Analysis). The domains are: 
1. **Extended Sign Domain**: An enriched sign domain that tracks the sign of integer variables with greater precision than the classical `{−, 0, +}` domain.
2. **Two Variables Per Inequality (TVPI)**: A relational numerical abstract domain that  represents program states as conjunctions of linear inequalities involving at most two variables each.
3. **Cartesian Product of Extended Signs and TVPI**: Combines the above two domains to track both relational inequalities and individual sign information simultaneously.


---

## Project Structure

```
src/
└── main/java/it/unive/lisa/tutorial/
    ├── ExtendedSigns.java                    # Extended sign abstract domain
    ├── TwoVariablesPerInequality.java        # TVPI relational abstract domain
    └── ExtendedSignsAndTVPICartesian.java    # Cartesian product of the two above

src/
└── test/java/it/unive/lisa/tutorial/
    ├── ExtendedSignsTest.java
    ├── TwoVariablesPerInequalityTest.java
    └── ExtendedSignsAndTVPICartesianTest.java

inputs/
    ├── extendedSigns.imp                     # Test program for ExtendedSigns
    ├── twoVariablesPerInequality.imp         # Test program for TVPI
    └── extendedsignsandtvpicartesian.imp     # Test program for the Cartesian product


```

---

## Abstract Domains

### 1. `ExtendedSigns` - Extended Sign Domain

An enriched sign domain that tracks the sign of integer variables with greater precision than the classical `{−, 0, +}` domain.

**Lattice elements (8 total):**

| Element             | Meaning          |
|---------------------|------------------|
| `BOTTOM` (⊥)        | Unreachable      |
| `STRICTLY_NEGATIVE` | x < 0            |
| `ZERO`              | x = 0            |
| `STRICTLY_POSITIVE` | x > 0            |
| `NEGATIVE`          | x ≤ 0            |
| `POSITIVE`          | x ≥ 0            |
| `NON_ZERO`          | x ≠ 0            |
| `TOP` (⊤)           | Unknown          |

**Features:**
- Abstract semantics for `+`, `-`, `*`, `/`, and unary negation
- Refinement via `assumeBinaryExpression` for comparisons (`<`, `<=`, `>`, `>=`, `==`)
- Non-relational: operates variable by variable inside a `ValueEnvironment`

---

### 2. `TwoVariablesPerInequality` - TVPI Relational Domain

A relational numerical abstract domain that represents program states as conjunctions of linear inequalities involving **at most two variables** each, of the form:

```
a*x + b*y ≤ c
```

**Key algorithmic components:**

- **Completion** (Definition 10): iterative closure via Fourier-Motzkin elimination to derive all implied two-variable constraints. Runs for at most ⌈log₂(d−1)⌉ + 2 iterations where *d* is the number of distinct variables.
- **Resultant** (Definition 9): pairwise elimination of a shared variable between two inequalities to produce a new implied constraint.
- **Widening**: applied per variable pair to ensure convergence in loops, using angular ordering of constraints in the 2D plane.
- **Join (lub)**: computes the convex hull of two abstract states per variable pair.
- **Projection**: removes a variable by forgetting all constraints involving it.
- **Satisfiability check**: determines whether a boolean condition is always true, always false, or unknown given the current state.

Internally, the domain separates:
- **Unary bounds**: single-variable constraints stored as `[lo, hi]` intervals
- **Binary planes**: two-variable constraint sets, one per `{x, y}` pair

---

### 3. `ExtendedSignsAndTVPICartesian` - Cartesian Product

Combines `TwoVariablesPerInequality` and `ValueEnvironment<ExtendedSigns>` as a **Cartesian product** using LiSA's `CartesianProduct` combinator. Both components are maintained and updated independently; neither is used to refine the other.

This allows the analysis to simultaneously track:
- Relational inequalities between variables (TVPI)
- Individual sign information per variable (ExtendedSigns)

---

## Test Programs (`.imp`)

The `.imp` files are written in IMP, a simple imperative language supported by LiSA.

| File | Tests |
|------|-------|
| `extendedSigns.imp` | Arithmetic operations, negation, loops, and branches under the sign domain |
| `twoVariablesPerInequality.imp` | Assignments, copies, translations, joins, loops, widening, and projections |
| `extendedsignsandtvpicartesian.imp` | Combined scenarios exercising both domains simultaneously |

---

## Running the Analyses

Each test class runs a LiSA analysis and writes HTML control-flow graphs annotated with the abstract state at each program point to the `outputs/` directory.

```
outputs/
├── extendedsigns/
├── twoVariablesInequality/
└── extendedsignsandtvpicartesian/
```


Run each analysis by executing the corresponding test class directly.

---

## References

- Simon, King & Howe, [Two Variables per Linear Inequality as an Abstract Domain](https://link.springer.com/chapter/10.1007/3-540-45013-0_7)
- Antoine Miné, [Static Inference of Numeric Invariants by Abstract Interpretation](https://mine.perso.lip6.fr/enseignement/mpri/2024-2025/course_ok.pdf)