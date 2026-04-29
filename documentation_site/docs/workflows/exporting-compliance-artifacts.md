# Exporting Compliance Artifacts

## Description

ReARM provides structured exports for the compliance artifacts attached to your releases. Five export types are available:

| Export | Format | Source | Where |
|---|---|---|---|
| **SBOM** | CycloneDX JSON / CSV / Excel, SPDX | Merged release SBOM via Rebom | Release view |
| **VDR** | CycloneDX 1.6 JSON or PDF | Vulnerability data from release metrics | Release view |
| **VEX** | CycloneDX 1.6 VEX or OpenVEX 0.2.0 JSON | Triage decisions on release vulnerabilities | Release view |
| **OBOM** | CycloneDX JSON | Operational BOM from outbound deliverable | Release view |
| **BOV** | CycloneDX 1.6 JSON or PDF | Findings data from the Findings Modal | Findings Modal |

All exports except BOV are server-generated. BOV is assembled client-side from the currently displayed findings.

## Accessing Exports

Open any release and click the **download icon** in the release header. A modal opens with tabs for **SBOM**, **VDR**, **VEX**, and **OBOM**.

## SBOM Export

Exports the merged SBOM for the release. Options:

| Option | Description |
|---|---|
| **SBOM Configuration** | Which merged SBOM variant to export (`SBOM`, `TEST`, `BUILD_TIME`, etc.) |
| **Structure** | `FLAT` or `NESTED` (CycloneDX hierarchy) |
| **Top-level only** | Strip transitive dependencies, keep direct dependencies only |
| **Ignore Dev** | Exclude development dependencies |
| **Exclude coverage types** | Exclude artifacts tagged as Dev, Test, or Build-Time coverage |
| **Media Type** | `JSON`, `CSV`, or `Excel` |

Click **Export** to download the file.

## VDR Export

Exports vulnerability disclosure data as a [CycloneDX 1.6 VDR](https://cyclonedx.org/capabilities/vdr/).

### Snapshot Options

VDR exports can optionally be scoped to a historical point in time:

| Option | Description |
|---|---|
| **Include Suppressed** | Include findings in `FALSE_POSITIVE` or `NOT_AFFECTED` state |
| **Up To Date** | Snapshot findings as of a specific date |
| **Target Lifecycle** | Snapshot findings as of when the release reached a specific lifecycle stage |
| **Target Approval** | *(ReARM Pro)* Snapshot findings as of a specific approval entry |

When no snapshot option is set, the export reflects the current state of findings.

### Output Formats

- **JSON** — CycloneDX 1.6 VDR JSON file
- **PDF** — Formatted vulnerability report PDF

## VEX Export

Exports a [Vulnerability Exploitability eXchange](https://www.cisa.gov/sites/default/files/2023-04/minimum-requirements-for-vex-508c.pdf) document. Where the VDR enumerates every known vulnerability, the VEX is the negative-advisory complement: it tells consumers which CVEs **do not affect** this release and why, and which previously-known CVEs are now **fixed**.

### Output Formats

| Format | Description |
|---|---|
| **CycloneDX 1.6 VEX (JSON)** | CycloneDX VEX-only BOM — vulnerabilities with `analysis` blocks, no component tree |
| **OpenVEX 0.2.0 (JSON)** | [OpenVEX](https://openvex.dev/) statements, strict L15-compliant |

### Statement Selection

Each finding's [analysis state](./auditing-findings#analysis-states) maps to VEX as follows:

| Analysis State | CDX `analysis.state` | OpenVEX `status` |
|---|---|---|
| `EXPLOITABLE` | `exploitable` | `affected` |
| `NOT_AFFECTED` | `not_affected` | `not_affected` |
| `FALSE_POSITIVE` | `false_positive` | `not_affected` |
| `RESOLVED` | `resolved` | `fixed` |
| `IN_TRIAGE` | `in_triage` | `under_investigation` |

`NOT_AFFECTED`, `FALSE_POSITIVE`, and `RESOLVED` are **always included** so consumers see decided non-actionable findings. `IN_TRIAGE` (and findings with no analysis at all) are **excluded by default** per CISA guidance — toggle **Include In-Triage** to include them.

### Historically Resolved Findings

VEX exports also include CVEs that affected **prior versions** on the same branch but are no longer present. ReARM walks the release's branch lineage (and fork-point ancestry) and emits any CVE that disappeared along the way as `resolved` (CDX) / `fixed` (OpenVEX). For Product releases the walk recurses through child component releases.

Each historically-resolved CDX entry carries two ReARM properties:

- `rearm:vex:resolvedInRelease` — UUID of the release where the CVE was last present
- `rearm:vex:resolvedInVersion` — version string of that release

This produces a complete remediation history and pre-empts the consumer question "why did this CVE disappear from your VEX?".

### Snapshot Options

VEX exports can optionally be scoped to a historical point in time:

| Option | Description |
|---|---|
| **Current State** | No historical filter (default) |
| **By Date** | Statements as of a specific date and time |
| **First Scanned** | Statements as of when this release was first scanned (estimated from artifact creation + 6 hours when no firstScanned timestamp is recorded) |
| **By Lifecycle** | Statements as of when the release reached a specific lifecycle stage |
| **By Approval** | *(ReARM Pro)* Statements as of a specific approval entry |

Snapshots apply to both current-state and historically-resolved entries.

### Output Naming

Files are named `<release-uuid>-vex[-<snapshot-suffix>].cdx.json` for CycloneDX or `.openvex.json` for OpenVEX.

## OBOM Export

Exports the Operational BOM derived from the release's outbound deliverable. No configuration options — click **Export OBOM** to download.

## BOV / Findings Export (Findings Modal)

Findings can be exported from the **Findings Modal** in two formats:

- **PDF** — formatted findings report (default)
- **CycloneDX 1.6 (JSON) BOV** — a [CycloneDX 1.6 BOV](https://cyclonedx.org/capabilities/bov/) assembled client-side from the currently displayed findings

Select the desired format in the modal before clicking **Export**.

### BOV-specific options

When **CycloneDX 1.6 (JSON) BOV** is selected, additional options become available:

| Option | Description |
|---|---|
| **Include Suppressed** | Include `FALSE_POSITIVE` / `NOT_AFFECTED` findings |
| **Include Analysis** | Embed the current triage state for each vulnerability |

Violations and Weaknesses are not included in the BOV — the spec covers only vulnerabilities.

## Download Log

The **Download Log** (accessible under **Organization Settings → Download Log**, visible to org admins only) records SBOM, VDR, and VEX download events in your organization. Each entry shows:

- **Download type** (`SBOM_EXPORT`, `VDR_EXPORT`, `VEX_EXPORT`)
- **Subject** — the release the export was generated from, linked to its release page
- **Config details** — the export parameters used (structure, media type, snapshot options, etc.)
- **Downloaded by** — the user who triggered the export
- **IP address** and **timestamp**

> **Scope:** The Download Log only records server-side exports from the Release view (SBOM, VDR, VEX). Client-side exports such as BOV and PDF are not logged, nor are OBOM exports.

The log is useful for compliance audits to demonstrate when and how vulnerability or SBOM data was accessed.
