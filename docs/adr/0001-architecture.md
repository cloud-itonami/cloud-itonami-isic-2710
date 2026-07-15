# ADR-0001: ElecEquipAdvisor ⊣ Electrical Equipment Plant Operations Governor architecture

## Status

Accepted. `cloud-itonami-isic-2710` promoted from `:spec` to
`:implemented` in the `kotoba-lang/industry` registry, following the
verified fresh-scaffold protocol established by prior actors in this
fleet.

## Context

`cloud-itonami-isic-2710` publishes an OSS blueprint for electric-
motor/generator/transformer/distribution-and-control-apparatus
**plant operations coordination** (production-batch product-type/
dielectric-test-voltage/quantity/defect-rate data logging, winding/
assembly/test-bench-equipment maintenance scheduling, safety-concern
flagging, and outbound electrical-equipment shipment coordination).
Like every actor in this fleet, the blueprint alone is not an
implementation: this ADR records the governed-actor architecture that
promotes it to real, tested code, following the same langgraph
StateGraph + independent Governor + Phase 0->3 rollout pattern
established across the cloud-itonami fleet.

The closest domain analogs are `cloud-itonami-isic-2211` (Manufacture
of rubber tyres and tubes) and `cloud-itonami-isic-2013` (Manufacture
of plastics and synthetic rubber in primary forms): all three are
back-office coordination actors for a fixed processing PLANT with
heavy manufacturing equipment and a real physical safety dimension,
and all three share the same four-op shape (`:log-production-batch`/
`:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment`)
and the same two-entity verified/registered gate structure (equipment
for maintenance scheduling, batch for shipment coordination). This
build mirrors `cloud-itonami-isic-2211`'s architecture closely (itself
informed by `cloud-itonami-isic-2013`, which in turn was informed by
`cloud-itonami-isic-2220`'s Manufacture-of-plastics-products actor)
but adapts the hazard profile and equipment/product vocabulary to the
electrical-equipment plant: this vertical's central physical hazard is
the high-voltage dielectric/hipot withstand-testing process (electric
shock / insulation-failure hazard, not 2211's high-temperature/high-
pressure curing-press hazard or 2013's chemical monomer-exposure/
exothermic-reaction-risk hazard); its permanent equipment-actuation
block guards winding/assembly/test-bench EQUIPMENT
(`:actuate-equipment?`) rather than a building/curing/vulcanization
LINE (`:actuate-line?` in 2211) or a polymerization REACTOR
(`:actuate-reactor?` in 2013); its production-batch record declares a
`:product-type` (closed set spanning electric-motor/generator/
transformer/distribution-apparatus/control-apparatus) and a
`:dielectric-test-kv` (the routine hipot/withstand test voltage in kV,
plausibility-checked 0-2500 against IEC 60076-3's lightning-impulse
withstand table) in addition to a `:defect-rate-percent`, rather than
2211's `:tyre-category`/`:load-index` or 2013's `:polymer-grade`/
`:off-spec-rate-percent`; and its shipment quantity is tracked in
finished-unit UNITS (`:units`/`:quantity-units`/`:shipped-units`), the
same shape 2211 uses for finished tyres (counted, not weighed, for
freight coordination) since motors/generators/transformers/apparatus
are likewise discrete counted units rather than a bulk weight.

This vertical additionally has a DOMAIN-SPECIFIC permanent block
neither 2211 nor 2013 needs in the same form: manufacture of electric
motors, generators, transformers and electricity distribution/control
apparatus is subject to electrical-safety certification regimes (e.g.
UL 1004/60034 series, IEC 60034/60076, CE marking under the EU Low
Voltage Directive). This actor is never the certification authority —
any proposal (regardless of op) that declares `:issue-certification?
true` is a HARD, PERMANENT, unconditional block
(`elecequipmfg.governor/certification-authority-blocked-violations`),
the same "no phase, no human override" posture as the equipment-
actuation block.

This vertical has NO pre-existing `kotoba-lang/elecequipmfg`-style
capability library to wrap (verified: no such repo exists). This build
therefore uses self-contained domain logic — pure functions in
`elecequipmfg.registry` (equipment/batch verification, shipment-
quantity recompute, product-type validation, dielectric-test-voltage
plausibility validation, defect-rate plausibility validation) are
re-verified independently by the governor, the same "ground truth, not
self-report" discipline established across prior actors (most
directly `cloud-itonami-isic-2211`'s `tyremfg.registry` and
`cloud-itonami-isic-2013`'s `resinmfg.registry`).

This blueprint's own `:itonami.blueprint/governor` keyword,
`:electrical-equipment-plant-operations-governor`, is grep-verified
UNIQUE fleet-wide (`gh search code
"electrical-equipment-plant-operations-governor" --owner
cloud-itonami`, zero hits before this repo was created).

## Decision

### Decision 1: Self-contained domain logic (no external electrical-equipment-manufacturing capability library to wrap)

Unlike actors that delegate to pre-existing domain libraries, this
electric-motor/generator/transformer/distribution-and-control-
apparatus vertical has NO pre-existing capability library to wrap. The
equipment/batch-verification / shipment-quantity / product-type /
dielectric-test-voltage / defect-rate validation functions live as
pure functions in `elecequipmfg.registry` and are re-verified
independently by `elecequipmfg.governor` — the same "ground truth, not
self-report" discipline established across prior actors (most
directly `cloud-itonami-isic-2211`'s `tyremfg.registry` and
`cloud-itonami-isic-2013`'s `resinmfg.registry`).

### Decision 2: Coordination, not control — scope boundary at the back-office

This actor is **strictly back-office coordination** of electric-motor/
generator/transformer/distribution-and-control-apparatus plant
operations. It does NOT:
- Control winding, assembly, or test-bench equipment directly
- Make plant-safety or certification decisions (exclusive to the human plant supervisor / accredited certification body)
- Actuate winding/assembly/test-bench equipment
- Self-issue an electrical-safety certification mark (e.g. UL/CE/IEC)

All proposals are `:effect :propose` only. The advisor proposes; the
governor validates; escalation paths funnel to human plant-supervisor
approval. This is not a replacement for the supervisor's authority or
the certification body's authority — it is a proposal-screening and
documentation layer.

**CRITICAL SAFETY BOUNDARY**: electric motor/generator/transformer/
distribution-and-control-apparatus manufacturing is a safety-critical
domain (high-voltage dielectric/hipot test hazard, insulation-failure
risk, electrical-safety certification, downstream grid-reliability and
worker-safety consequence). Safety-concern flagging NEVER auto-
commits. All safety concerns escalate immediately to human review.

### Decision 3: Safety-concern escalation — always human sign-off

`:flag-safety-concern` (insulation-failure concern, electrical-safety
concern, high-voltage-test-hazard concern, equipment-safety concern)
ALWAYS escalates, never auto-commits. This is not a "low-stakes
proposal" — it is a circuit-breaker that must reach human authority.

### Decision 4: Two independent verified/registered gates (equipment AND batch), not one

Like `cloud-itonami-isic-2211` and `cloud-itonami-isic-2013`, this
vertical has TWO entity kinds each gating a different op:
`:schedule-maintenance` independently verifies the referenced
**equipment** unit's own `:verified?`/`:registered?` fields;
`:coordinate-shipment` independently verifies the referenced **batch**'s
own `:verified?`/`:registered?` fields. Both are the same "plant/batch
record must be independently verified/registered before any action"
HARD invariant applied to the two distinct record kinds this domain
actually has. `:coordinate-shipment` additionally independently
recomputes whether a batch's own recorded shipped-to-date unit
quantity plus the proposal's own claimed unit quantity would exceed
the batch's own recorded production quantity — never taken on the
advisor's self-report.

### Decision 5: HARD invariants (no override)

Four HARD governor invariants (elaborated into twelve concrete checks
in `elecequipmfg.governor`, mirroring `cloud-itonami-isic-2211`'s and
`cloud-itonami-isic-2013`'s own elaboration of their HARD invariants
into concrete checks) block proposals and cannot be overridden by
human approval:
1. Plant/batch record (equipment for maintenance, batch for shipment) must be independently verified/registered before any action is taken against it, and a shipment's quantity must independently recompute within the batch's own logged production quantity
2. Proposals must be `:effect :propose` only (never direct equipment control)
3. Direct winding/assembly/test-bench-equipment control, equipment actuation, or self-issued electrical-safety certification is permanently blocked
4. The op allowlist is closed — `:log-production-batch`/`:schedule-maintenance`/`:flag-safety-concern`/`:coordinate-shipment` only

## Consequences

(+) Electric-motor/generator/transformer/distribution-and-control-
apparatus plant operations back-office now has a documented, governed,
auditable coordination layer that funnels all decisions through
independent validation before human approval.

(+) The "coordination, not control" boundary is explicit in code: all
`:effect :propose`, all real-world actuation requires human plant-
supervisor sign-off, and no certification mark can ever be
self-issued.

(+) Scope is bounded and verifiable: four HARD invariants (elaborated
into twelve concrete governor checks) protect against scope creep into
unauthorized equipment operation, equipment actuation, or
certification self-issuance. Safety concerns are a circuit-breaker,
not a threshold.

(+) Safety-critical discipline is explicit: safety-concern flagging
cannot be rate-limited, suppressed, or auto-decided by phase gate.
Human review is mandatory.

(-) Still a simulation/proposal layer, not a real plant-operations
control system. Equipment actuation, line operation, and certification
issuance remain human-/institution-controlled via external channels.

(-) No integration with real plant-management databases (equipment
telemetry, batch tracking, freight dispatch, certification-body APIs)
— this is a standalone coordinator blueprint.

## Verification

- `cloud-itonami-isic-2710`: `clojure -M:test` green (all tests pass;
  see the superproject ADR and `kotoba-lang/industry` registry entry
  for the exact `Ran N tests containing M assertions, 0 failures, 0
  errors` output, verified from an independent fresh clone), `clojure
  -M:lint` clean, `clojure -M:dev:run` demo narrative exercises
  proposal submission, escalation, and every HARD-hold scenario
  directly (not-propose-effect, unknown-op, equipment-not-verified,
  batch-not-verified, shipment-quantity-exceeded, equipment-actuate-
  blocked, certification-authority-blocked, already-scheduled,
  invalid-product-type, invalid-dielectric-test-kv, invalid-defect-
  rate).
- All source is `.cljc` (portable ClojureScript / JVM / nbb) — no
  JVM-only interop; the actor graph is invoked exclusively via
  `langgraph.graph/run*` (not `.invoke`, which is not cljs-portable).
- Audit ledger is append-only, all decisions are traced; every settled
  request (commit or hold) leaves exactly one ledger fact.
- `deps.edn` pins `io.github.kotoba-lang/langgraph` and
  `io.github.kotoba-lang/langchain` via `:local/root` directly in the
  top-level `:deps` (not only under a `:dev` alias), so a bare
  `clojure -M:test` resolves offline inside the monorepo checkout.
