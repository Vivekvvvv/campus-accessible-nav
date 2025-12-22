#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

const repoRoot = process.cwd();
const migrationDir = path.join(repoRoot, "backend", "src", "main", "resources", "db", "migration");
const metaDir = path.join(migrationDir, "meta");

function readFile(p) {
  return fs.readFileSync(p, "utf8");
}

function parseSimpleYaml(yamlText) {
  const out = {};
  for (const raw of yamlText.split(/\r?\n/)) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const idx = line.indexOf(":");
    if (idx < 0) continue;
    const key = line.slice(0, idx).trim();
    const val = line.slice(idx + 1).trim().replace(/^['"]|['"]$/g, "");
    out[key] = val;
  }
  return out;
}

function hasRiskySql(sql) {
  const s = sql.toLowerCase();
  const patterns = [
    /\bdrop\s+table\b/,
    /\bdrop\s+column\b/,
    /\btruncate\s+table\b/,
    /\balter\s+table\b[\s\S]*?\balter\s+column\b[\s\S]*?\btype\b/,
    /\bdelete\s+from\b/,
  ];
  return patterns.some((p) => p.test(s));
}

function check() {
  if (!fs.existsSync(migrationDir)) {
    throw new Error(`Missing migration dir: ${migrationDir}`);
  }
  if (!fs.existsSync(metaDir)) {
    throw new Error(`Missing migration meta dir: ${metaDir}`);
  }

  const migrations = fs
    .readdirSync(migrationDir)
    .filter((n) => /^V\d+__.+\.sql$/.test(n))
    .sort((a, b) => {
      const va = Number(/^V(\d+)__/.exec(a)?.[1] || "0");
      const vb = Number(/^V(\d+)__/.exec(b)?.[1] || "0");
      return va - vb;
    });

  const allowedStrategy = new Set(["init", "expand", "contract", "index_only", "data_backfill"]);
  const expandLikeStrategy = new Set(["expand", "index_only", "data_backfill", "init"]);
  const errors = [];
  const entries = [];

  for (const mig of migrations) {
    const version = /^V(\d+)__/.exec(mig)?.[1];
    const migPath = path.join(migrationDir, mig);
    const metaPath = path.join(metaDir, `V${version}.yml`);
    if (!version) continue;

    if (!fs.existsSync(metaPath)) {
      errors.push(`Missing meta file for ${mig}: ${path.relative(repoRoot, metaPath)}`);
      continue;
    }

    const meta = parseSimpleYaml(readFile(metaPath));
    entries.push({
      migrationName: mig,
      version: Number(version),
      migrationPath: migPath,
      metaPath,
      meta,
    });

    const requiredKeys = [
      "strategy",
      "backward_compatible",
      "owner",
      "rollout",
      "rollback",
      "compat_window",
    ];
    for (const k of requiredKeys) {
      if (!meta[k]) {
        errors.push(`${mig}: missing meta key "${k}"`);
      }
    }

    if (meta.strategy && !allowedStrategy.has(meta.strategy)) {
      errors.push(`${mig}: invalid strategy "${meta.strategy}"`);
    }
    if (meta.backward_compatible && !["true", "false"].includes(meta.backward_compatible.toLowerCase())) {
      errors.push(`${mig}: backward_compatible must be true/false`);
    }

    const risky = hasRiskySql(readFile(migPath));
    const backwardCompatible = String(meta.backward_compatible || "").toLowerCase() === "true";
    if (risky && meta.strategy !== "contract") {
      errors.push(`${mig}: risky SQL detected, strategy must be "contract"`);
    }
    if (risky && backwardCompatible) {
      errors.push(`${mig}: risky SQL detected, backward_compatible cannot be true`);
    }
    if (risky && meta.strategy === "contract" && !String(meta.rollback || "").toLowerCase().includes("backup")) {
      errors.push(`${mig}: risky contract migration rollback must mention backup restore path`);
    }
    if (meta.strategy === "data_backfill" && !meta.backfill_task) {
      errors.push(`${mig}: data_backfill strategy requires meta key "backfill_task"`);
    }
    if (String(meta.rollout || "").toLowerCase().includes("backfill") && !meta.backfill_task) {
      errors.push(`${mig}: rollout mentions backfill but meta key "backfill_task" is missing`);
    }
  }

  const byVersion = new Map(entries.map((e) => [e.version, e]));
  for (const e of entries) {
    const meta = e.meta;
    const mig = e.migrationName;
    const backwardCompatible = String(meta.backward_compatible || "").toLowerCase() === "true";

    if (meta.strategy === "expand" && !backwardCompatible) {
      errors.push(`${mig}: expand strategy must keep backward_compatible=true`);
    }
    if (meta.strategy === "index_only" && !backwardCompatible) {
      errors.push(`${mig}: index_only strategy must keep backward_compatible=true`);
    }
    if (meta.strategy === "data_backfill" && !backwardCompatible) {
      errors.push(`${mig}: data_backfill strategy must keep backward_compatible=true`);
    }

    if (meta.strategy === "contract") {
      if (backwardCompatible) {
        errors.push(`${mig}: contract strategy must set backward_compatible=false`);
      }
      const compatWindow = String(meta.compat_window || "").trim().toLowerCase();
      if (!compatWindow || compatWindow === "n/a" || compatWindow === "none") {
        errors.push(`${mig}: contract strategy must define a non-empty compat_window`);
      }

      const pairedExpandRaw = String(meta.paired_expand || "").trim();
      if (!pairedExpandRaw) {
        errors.push(`${mig}: contract strategy requires meta key "paired_expand" (e.g. V6)`);
        continue;
      }
      const matched = /^V(\d+)$/i.exec(pairedExpandRaw);
      if (!matched) {
        errors.push(`${mig}: paired_expand must match format V<version>, got "${pairedExpandRaw}"`);
        continue;
      }
      const pairedVersion = Number(matched[1]);
      const pairedEntry = byVersion.get(pairedVersion);
      if (!pairedEntry) {
        errors.push(`${mig}: paired_expand ${pairedExpandRaw} does not exist`);
        continue;
      }
      if (pairedVersion >= e.version) {
        errors.push(`${mig}: paired_expand ${pairedExpandRaw} must be older than current migration`);
      }
      if (!expandLikeStrategy.has(String(pairedEntry.meta.strategy || ""))) {
        errors.push(`${mig}: paired_expand ${pairedExpandRaw} must reference an expand-like migration`);
      }
    }
  }

  const summary = [
    "# Flyway Migration Policy Report",
    "",
    `- migrations checked: **${migrations.length}**`,
    `- errors: **${errors.length}**`,
    "",
  ];
  if (errors.length > 0) {
    summary.push("## Violations");
    for (const e of errors) summary.push(`- ${e}`);
  } else {
    summary.push("No migration policy violations.");
  }
  const reportPath = path.join(repoRoot, ".run", "flyway-policy-report.md");
  fs.mkdirSync(path.dirname(reportPath), { recursive: true });
  fs.writeFileSync(reportPath, `${summary.join("\n")}\n`, "utf8");
  console.log(`Flyway policy report: ${reportPath}`);

  if (errors.length > 0) {
    process.exit(1);
  }
}

check();
