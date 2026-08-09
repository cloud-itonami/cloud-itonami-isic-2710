(ns elecequipmfg.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave3 rollout): this repo previously shipped no generator and no
  sample console. This namespace drives the REAL actor stack
  (`elecequipmfg.operation` -> `elecequipmfg.governor` ->
  `elecequipmfg.store`) through a scenario adapted from this repo's own
  `elecequipmfg.sim` demo driver (`clojure -M:dev:run`, confirmed BEFORE
  writing this file against the real seeded ids `batch-001`..`batch-003`
  / `winder-001` / `test-bench-002` -- ids match
  `elecequipmfg.store/sample-data!`, so it was safe to reuse rather than
  author from scratch), covering one full lifecycle plus distinct HARD-
  hold reasons, rendered deterministically -- no invented numbers, no
  timestamps in the page content, byte-identical across reruns against
  the same seed (verify by diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [jp-go-dds.skin]
            [clojure.string :as str]
            [elecequipmfg.store :as store]
            [elecequipmfg.operation :as op]
            [elecequipmfg.phase :as phase]
            [elecequipmfg.governor :as governor]
            [langgraph.graph :as g]))

(def ^:private coordinator
  {:actor-id "coord-1" :actor-role :plant-coordinator :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context coordinator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "coord-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, mirrored from `elecequipmfg.sim` (not calling
  its `-main`, to keep this namespace self-contained and free of
  println noise):

    1. `:log-production-batch` batch-001 clean patch -- phase-3 auto-
       commit when governor-clean (only auto-eligible op).
    2. `:schedule-maintenance` mnt-1 on winder-001 (verified+registered
       winding machine) -- never auto -> escalate -> approve -> commit.
    3. `:flag-safety-concern` concern-1 on winder-001 -- ALWAYS
       escalates (`:coordination/safety-concern`) -> approve -> commit.
    4. `:coordinate-shipment` ship-1 on batch-001, 50.0 units (within
       headroom 500-100) -- escalate -> approve -> commit.

    Then distinct HARD-hold scenarios (none ever reaches a human):

    5. request `:effect` other than `:propose` -> `:not-propose-effect`.
    6. unrecognized op -> `:unknown-op`.
    7. schedule-maintenance on test-bench-002 (UNVERIFIED/unregistered)
       -> `:equipment-not-verified`.
    8. coordinate-shipment on batch-003 (UNVERIFIED/unregistered)
       -> `:batch-not-verified`.
    9. coordinate-shipment ship-3 on batch-002, 100 units (180+100>200)
       -> `:shipment-quantity-exceeded`.
   10. schedule-maintenance with `:actuate-equipment? true`
       -> `:equipment-actuate-blocked` (PERMANENT).
   11. schedule-maintenance mnt-1 AGAIN -> `:already-scheduled`.
   12. log-production-batch fabricated product-type
       -> `:invalid-product-type`.
   13. log-production-batch implausible dielectric-test-kv
       -> `:invalid-dielectric-test-kv`.
   14. log-production-batch implausible defect-rate
       -> `:invalid-defect-rate`.
   15. log-production-batch attempting self-issue certification
       -> `:certification-authority-blocked` (PERMANENT).

  Returns the resulting store -- every field read by `render` is real
  governor/store output, not a hand-typed copy."
  []
  (let [db (-> (store/mem-store) (store/sample-data!))
        actor (op/build db)]

    (exec! actor "t1"
           {:op :log-production-batch :effect :propose :subject "batch-001"
            :patch {:product-type :transformer :last-assessed "2026-07-14"}})

    (exec! actor "t2"
           {:op :schedule-maintenance :effect :propose :subject "mnt-1"
            :value {:equipment-id "winder-001" :maintenance-type :coil-inspection
                    :scheduled-date "2026-08-01" :actuate-equipment? false}})
    (approve! actor "t2")

    (exec! actor "t3"
           {:op :flag-safety-concern :effect :propose :subject "concern-1"
            :value {:equipment-id "winder-001" :severity :moderate
                    :description "巻線絶縁の異常兆候、高電圧試験時の異音"}})
    (approve! actor "t3")

    (exec! actor "t4"
           {:op :coordinate-shipment :effect :propose :subject "ship-1"
            :value {:batch-id "batch-001" :units 50.0
                    :destination "buyer-yard-north"}})
    (approve! actor "t4")

    (exec! actor "t5"
           {:op :log-production-batch :effect :direct-write :subject "batch-001"
            :patch {:product-type :transformer}})

    (exec! actor "t6"
           {:op :actuate-winding-line :effect :propose :subject "batch-001"})

    (exec! actor "t7"
           {:op :schedule-maintenance :effect :propose :subject "mnt-2"
            :value {:equipment-id "test-bench-002" :maintenance-type :calibration
                    :scheduled-date "2026-08-01" :actuate-equipment? false}})

    (exec! actor "t8"
           {:op :coordinate-shipment :effect :propose :subject "ship-2"
            :value {:batch-id "batch-003" :units 100.0
                    :destination "buyer-yard-south"}})

    (exec! actor "t9"
           {:op :coordinate-shipment :effect :propose :subject "ship-3"
            :value {:batch-id "batch-002" :units 100.0
                    :destination "buyer-yard-east"}})

    (exec! actor "t10"
           {:op :schedule-maintenance :effect :propose :subject "mnt-3"
            :value {:equipment-id "winder-001" :maintenance-type :force-run
                    :scheduled-date "2026-09-01" :actuate-equipment? true}})

    (exec! actor "t11"
           {:op :schedule-maintenance :effect :propose :subject "mnt-1"
            :value {:equipment-id "winder-001" :maintenance-type :coil-inspection
                    :scheduled-date "2026-08-01" :actuate-equipment? false}})

    (exec! actor "t12"
           {:op :log-production-batch :effect :propose :subject "batch-001"
            :patch {:product-type :unobtainium}})

    (exec! actor "t13"
           {:op :log-production-batch :effect :propose :subject "batch-001"
            :patch {:dielectric-test-kv 999999.0}})

    (exec! actor "t14"
           {:op :log-production-batch :effect :propose :subject "batch-001"
            :patch {:defect-rate-percent 999.0}})

    (exec! actor "t15"
           {:op :log-production-batch :effect :propose :subject "batch-001"
            :patch {:issue-certification? true}})

    db))

;; ----------------------------- render helpers -----------------------------

(defn- esc
  "Minimal HTML-escape -- every rendered string passes through this."
  [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= subject-id (:subject %)) ledger)))

(defn- status-cell [fact]
  (cond
    (nil? fact)                      ["muted" "no activity"]
    (= :committed (:t fact))         ["ok" "committed"]
    (= :approval-granted (:t fact))  ["ok" "approval-granted"]
    (= :governor-hold (:t fact))
    ["err" (str "governor-hold: "
                (str/join "," (map name (or (:basis fact) []))))]
    (= :approval-rejected (:t fact)) ["err" "approval-rejected"]
    (= :approval-requested (:t fact)) ["warn" "approval-requested"]
    :else                            ["muted" "in progress"]))

(defn- yes-no [b]
  (if b "yes" "<span class=\"critical\">no</span>"))

(defn- batches-table [db]
  (let [batches (store/all-batches db)
        ledger (store/ledger db)]
    (str
     "<table>\n<thead><tr>\n"
     "<th>id</th><th>product-type</th><th>model</th>"
     "<th>dielectric (kV)</th><th>quantity (units)</th>"
     "<th>defect-rate %</th><th>verified?</th><th>registered?</th>"
     "<th>shipped (units)</th><th>status</th>\n"
     "</tr></thead>\n<tbody>\n"
     (str/join
      "\n"
      (for [b batches
            :let [fact (last-fact-for ledger (:id b))
                  [cls label] (status-cell fact)]]
        (str "<tr>"
             "<td><code>" (esc (:id b)) "</code></td>"
             "<td><code>" (esc (:product-type b)) "</code></td>"
             "<td>" (esc (:model b)) "</td>"
             "<td>" (esc (:dielectric-test-kv b)) "</td>"
             "<td>" (esc (:quantity-units b)) "</td>"
             "<td>" (esc (:defect-rate-percent b)) "</td>"
             "<td>" (yes-no (:verified? b)) "</td>"
             "<td>" (yes-no (:registered? b)) "</td>"
             "<td>" (esc (:shipped-units b)) "</td>"
             "<td class=\"" cls "\">" (esc label) "</td>"
             "</tr>")))
     "\n</tbody></table>")))

(defn- equipment-table [db]
  (let [equipment (store/all-equipment db)]
    (str
     "<table>\n<thead><tr>\n"
     "<th>id</th><th>kind</th><th>verified?</th><th>registered?</th>\n"
     "<th>last maintenance</th><th>last scheduled maintenance</th>\n"
     "</tr></thead>\n<tbody>\n"
     (str/join
      "\n"
      (for [e equipment]
        (str "<tr>"
             "<td><code>" (esc (:id e)) "</code></td>"
             "<td><code>" (esc (:kind e)) "</code></td>"
             "<td>" (yes-no (:verified? e)) "</td>"
             "<td>" (yes-no (:registered? e)) "</td>"
             "<td>" (if-let [d (:last-maintenance-date e)] (esc d) "&mdash;") "</td>"
             "<td>" (if-let [d (:last-scheduled-maintenance-date e)] (esc d) "&mdash;") "</td>"
             "</tr>")))
     "\n</tbody></table>")))

(defn- safety-concerns-table [db]
  (let [concerns (store/safety-concerns db)]
    (str
     "<table>\n<thead><tr>\n"
     "<th>id</th><th>equipment</th><th>severity</th><th>description</th>\n"
     "</tr></thead>\n<tbody>\n"
     (if (seq concerns)
       (str/join
        "\n"
        (for [c concerns]
          (str "<tr>"
               "<td><code>" (esc (:id c)) "</code></td>"
               "<td><code>" (esc (:equipment-id c)) "</code></td>"
               "<td>" (esc (:severity c)) "</td>"
               "<td>" (esc (:description c)) "</td>"
               "</tr>")))
       "<tr><td colspan=\"4\" class=\"muted\">none</td></tr>")
     "\n</tbody></table>")))

(defn- committed-records-table [db]
  (let [maintenances (store/maintenance-history db)
        shipments (store/shipment-history db)]
    (str
     "<table>\n<thead><tr>\n"
     "<th>record_id</th><th>kind</th><th>maintenance_id / shipment_id</th>\n"
     "</tr></thead>\n<tbody>\n"
     (str/join
      "\n"
      (for [r (concat maintenances shipments)]
        (str "<tr>"
             "<td><code>" (esc (get r "record_id")) "</code></td>"
             "<td>" (esc (get r "kind")) "</td>"
             "<td><code>" (esc (or (get r "maintenance_id")
                                   (get r "shipment_id"))) "</code></td>"
             "</tr>")))
     "\n</tbody></table>")))

(defn- action-gate-table
  "Static op-contract description from real `elecequipmfg.phase/phases`
  and `elecequipmfg.governor/high-stakes` -- documentation of fixed
  behavior, not runtime telemetry."
  []
  (let [ph (get phase/phases phase/default-phase)]
    (str
     "<table>\n<thead><tr>\n"
     "<th>op</th><th>phase-" phase/default-phase " write allowed?</th>"
     "<th>auto-eligible?</th><th>always escalates (high-stakes)?</th>\n"
     "</tr></thead>\n<tbody>\n"
     (str/join
      "\n"
      (for [op (sort phase/write-ops)]
        (str "<tr>"
             "<td><code>" (esc op) "</code></td>"
             "<td>" (if (contains? (:writes ph) op) "yes" "<span class=\"warn\">no</span>") "</td>"
             "<td>" (if (contains? (:auto ph) op) "<span class=\"ok\">yes</span>" "no") "</td>"
             "<td>" (if (contains? governor/high-stakes
                                   (when (= op :flag-safety-concern)
                                     :coordination/safety-concern))
                      "<span class=\"critical\">yes</span>" "no") "</td>"
             "</tr>")))
     "\n</tbody></table>")))

(defn- audit-ledger-table [db]
  (str
   "<table>\n<thead><tr>\n"
   "<th>t</th><th>op</th><th>subject</th><th>disposition</th><th>basis / rule</th>\n"
   "</tr></thead>\n<tbody>\n"
   (str/join
    "\n"
    (for [f (store/ledger db)]
      (str "<tr>"
           "<td>" (esc (:t f)) "</td>"
           "<td><code>" (esc (:op f)) "</code></td>"
           "<td><code>" (esc (:subject f)) "</code></td>"
           "<td class=\""
           (case (:disposition f) :commit "ok" :hold "err" "muted")
           "\">" (esc (:disposition f)) "</td>"
           "<td>" (if (seq (:basis f))
                    (str/join ", " (map (comp esc name) (:basis f)))
                    "&mdash;")
           "</td>"
           "</tr>")))
   "\n</tbody></table>"))

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (str
   "<!doctype html>\n"
   "<html lang=\"ja\">\n<head>\n<meta charset=\"utf-8\">\n"
   "<title>cloud-itonami-isic-2710 &middot; electric motors/generators/transformers plant ops</title>\n"
   "<style>"
   (jp-go-dds.skin/dds+skin)
   "</style>\n"
   "</head>\n<body>\n"
   "<header class=\"bar\">\n"
   "  <h1>Electrical Equipment Plant Operations Governor — Operator Console (ISIC 2710)</h1>\n"
   "  <span class=\"badge\">read-only sample · governor-gated · phase "
   phase/default-phase " (" (esc (:label (get phase/phases phase/default-phase))) ")"
   " · actuate/certification permanently blocked</span>\n"
   "</header>\n"
   "<main>\n"
   "  <section class=\"card\">\n"
   "    <h2>Production batches</h2>\n"
   "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>elecequipmfg.store</code> via <code>elecequipmfg.render-html</code> (<code>clojure -M:dev:render-html</code>). No invented numbers; every cell is real governor/store output.</p>\n"
   (batches-table db) "\n"
   "  </section>\n"
   "  <section class=\"card\">\n"
   "    <h2>Equipment (winding / assembly / test-bench)</h2>\n"
   (equipment-table db) "\n"
   "  </section>\n"
   "  <section class=\"card\">\n"
   "    <h2>Safety concerns (flagged this run)</h2>\n"
   "    <p class=\"muted\">Always human-approved — never auto at any phase. Direct equipment actuation and self-issued electrical-safety certification marks remain permanently blocked.</p>\n"
   (safety-concerns-table db) "\n"
   "  </section>\n"
   "  <section class=\"card\">\n"
   "    <h2>Committed draft records (maintenance-schedule / shipment-coordination)</h2>\n"
   "    <p class=\"muted\">Unsigned drafts only — real plant actuation and freight dispatch stay outside this actor's authority.</p>\n"
   (committed-records-table db) "\n"
   "  </section>\n"
   "  <section class=\"card\">\n"
   "    <h2>Action gate (elecequipmfg.phase · elecequipmfg.governor/high-stakes)</h2>\n"
   "    <p class=\"muted\">HARD holds cannot be overridden. Equipment/batch verified+registered status, shipment headroom, product-type / dielectric-test-kv / defect-rate plausibility, double-schedule, actuate-equipment, and certification-authority blocks are independently recomputed.</p>\n"
   (action-gate-table) "\n"
   "  </section>\n"
   "  <section class=\"card\">\n"
   "    <h2>Audit ledger (this run)</h2>\n"
   "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
   (audit-ledger-table db) "\n"
   "  </section>\n"
   "</main>\n"
   "</body></html>\n"))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)
        f (java.io.File. out)]
    (when-let [parent (.getParentFile f)]
      (.mkdirs parent))
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/maintenance-history db)) "maintenance drafts,"
             (count (store/shipment-history db)) "shipment drafts,"
             (count (store/safety-concerns db)) "safety concerns )")))
