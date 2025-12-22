#!/usr/bin/env node
import fs from "node:fs";
import path from "node:path";

function parseArgs(argv) {
  const out = {};
  for (let i = 2; i < argv.length; i += 1) {
    const k = argv[i];
    const v = argv[i + 1];
    if (!k.startsWith("--") || !v || v.startsWith("--")) {
      throw new Error(`Invalid args near: ${k}`);
    }
    out[k.slice(2)] = v;
    i += 1;
  }
  return out;
}

function readJson(p) {
  const raw = fs.readFileSync(p, "utf8");
  const sanitized = raw.charCodeAt(0) === 0xfeff ? raw.slice(1) : raw;
  return JSON.parse(sanitized);
}

function methodsOf(pathItem) {
  if (!pathItem || typeof pathItem !== "object") return [];
  const allowed = new Set([
    "get",
    "put",
    "post",
    "delete",
    "options",
    "head",
    "patch",
    "trace",
  ]);
  return Object.keys(pathItem).filter((m) => allowed.has(m.toLowerCase())).map((m) => m.toLowerCase());
}

function getOperation(spec, p, method) {
  return spec?.paths?.[p]?.[method];
}

function operationId(op, fallback) {
  return op?.operationId || fallback;
}

function findParam(op, where, name) {
  const params = Array.isArray(op?.parameters) ? op.parameters : [];
  return params.find((p) => p?.in === where && p?.name === name);
}

function successCodes(op) {
  const responses = op?.responses || {};
  return Object.keys(responses).filter((c) => /^2\d\d$/.test(c));
}

function checkBreaking(oldSpec, newSpec) {
  const issues = [];

  const oldPaths = Object.keys(oldSpec?.paths || {});
  const newPathsSet = new Set(Object.keys(newSpec?.paths || {}));

  for (const p of oldPaths) {
    if (!newPathsSet.has(p)) {
      issues.push({
        type: "removed_path",
        message: `Path removed: ${p}`,
        severity: "error",
      });
      continue;
    }

    const oldMethods = methodsOf(oldSpec.paths[p]);
    const newMethods = new Set(methodsOf(newSpec.paths[p]));
    for (const m of oldMethods) {
      if (!newMethods.has(m)) {
        issues.push({
          type: "removed_operation",
          message: `Operation removed: ${m.toUpperCase()} ${p}`,
          severity: "error",
        });
        continue;
      }

      const oldOp = getOperation(oldSpec, p, m);
      const newOp = getOperation(newSpec, p, m);
      const opName = operationId(oldOp, `${m.toUpperCase()} ${p}`);

      // Newly required request body is breaking for existing clients.
      const oldReqRequired = Boolean(oldOp?.requestBody?.required);
      const newReqRequired = Boolean(newOp?.requestBody?.required);
      if (!oldReqRequired && newReqRequired) {
        issues.push({
          type: "request_body_became_required",
          message: `${opName}: requestBody became required`,
          severity: "error",
        });
      }

      // Added required params or making optional param required are breaking.
      const oldParams = Array.isArray(oldOp?.parameters) ? oldOp.parameters : [];
      const newParams = Array.isArray(newOp?.parameters) ? newOp.parameters : [];

      for (const np of newParams) {
        if (!np?.in || !np?.name) continue;
        if (!np.required) continue;

        const op = findParam(oldOp, np.in, np.name);
        if (!op) {
          issues.push({
            type: "new_required_parameter",
            message: `${opName}: new required parameter ${np.in}:${np.name}`,
            severity: "error",
          });
          continue;
        }
        if (!op.required && np.required) {
          issues.push({
            type: "parameter_became_required",
            message: `${opName}: parameter became required ${np.in}:${np.name}`,
            severity: "error",
          });
        }
      }

      // Removing all 2xx responses or a previously exposed 2xx response code is breaking.
      const old2xx = successCodes(oldOp);
      const new2xx = successCodes(newOp);
      if (old2xx.length > 0 && new2xx.length === 0) {
        issues.push({
          type: "removed_success_response_family",
          message: `${opName}: all 2xx responses removed`,
          severity: "error",
        });
      } else {
        const new2xxSet = new Set(new2xx);
        for (const code of old2xx) {
          if (!new2xxSet.has(code)) {
            issues.push({
              type: "removed_success_response_code",
              message: `${opName}: success response removed ${code}`,
              severity: "error",
            });
          }
        }
      }
    }
  }

  // Removing components schema often breaks generated clients that reference them.
  const oldSchemas = Object.keys(oldSpec?.components?.schemas || {});
  const newSchemasSet = new Set(Object.keys(newSpec?.components?.schemas || {}));
  for (const name of oldSchemas) {
    if (!newSchemasSet.has(name)) {
      issues.push({
        type: "removed_schema",
        message: `Schema removed: components.schemas.${name}`,
        severity: "error",
      });
    }
  }

  return issues;
}

function writeReport(reportPath, oldPath, newPath, issues, allowBreaking) {
  const lines = [];
  lines.push("# OpenAPI Breaking Change Report");
  lines.push("");
  lines.push(`- old: \`${path.resolve(oldPath)}\``);
  lines.push(`- new: \`${path.resolve(newPath)}\``);
  lines.push(`- issues: **${issues.length}**`);
  lines.push(`- allow_breaking: **${allowBreaking}**`);
  lines.push("");

  if (issues.length === 0) {
    lines.push("No breaking changes detected.");
  } else {
    lines.push("Detected breaking changes:");
    for (const i of issues) {
      lines.push(`- [${i.type}] ${i.message}`);
    }
  }

  fs.writeFileSync(reportPath, `${lines.join("\n")}\n`, "utf8");
}

function main() {
  const args = parseArgs(process.argv);
  const oldPath = args.old;
  const newPath = args.new;
  const reportPath = args.report || "openapi-breaking-report.md";

  if (!oldPath || !newPath) {
    throw new Error("Usage: node check-breaking.mjs --old <old.json> --new <new.json> [--report <path>]");
  }

  const oldSpec = readJson(oldPath);
  const newSpec = readJson(newPath);
  const issues = checkBreaking(oldSpec, newSpec);
  const allowBreaking = String(process.env.ALLOW_OPENAPI_BREAKING || "false").toLowerCase() === "true";
  writeReport(reportPath, oldPath, newPath, issues, allowBreaking);

  if (issues.length > 0 && !allowBreaking) {
    console.error(`OpenAPI breaking check failed with ${issues.length} issue(s). See ${reportPath}`);
    process.exit(1);
  }
  console.log(`OpenAPI breaking check passed. Report: ${reportPath}`);
}

main();
