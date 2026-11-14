// Fetches each service's OpenAPI document from the locally running service and writes
// src/api/schema/<service>.d.ts. Spring services expose it only under the local/dev profile.
import { mkdir, writeFile } from "node:fs/promises";
import openapiTS, { astToString } from "openapi-typescript";

const SERVICES = {
  user: "http://localhost:8080/v3/api-docs",
  attendance: "http://localhost:8082/v3/api-docs",
  task: "http://localhost:8084/v3/api-docs",
  "exam-analysis": "http://localhost:8001/openapi.json",
  notification: "http://localhost:8003/openapi.json",
};

const outDir = new URL("../src/api/schema/", import.meta.url);
await mkdir(outDir, { recursive: true });

let failed = false;
for (const [service, url] of Object.entries(SERVICES)) {
  try {
    const ast = await openapiTS(new URL(url));
    await writeFile(new URL(`${service}.d.ts`, outDir), astToString(ast));
    console.log(`${service}: ok`);
  } catch (error) {
    failed = true;
    console.error(`${service}: failed (${url}): ${error.message}`);
  }
}
if (failed) process.exit(1);
