#!/usr/bin/env bash

set -euo pipefail

repo_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$repo_root"

temporary_dir=$(mktemp -d "${TMPDIR:-/tmp}/pul-112-qa.XXXXXX")
trap 'rm -rf -- "$temporary_dir"' EXIT

frontend_report="$temporary_dir/frontend.json"
frontend_log="$temporary_dir/frontend.log"
backend_log="$temporary_dir/backend.log"

printf '%s\n' 'Ejecutando pruebas frontend específicas de PUL-112...'
if ! pnpm --filter @pulso/frontend exec ng test \
  --watch=false \
  --include=src/app/features/discovery/data-access/current-projects-api.spec.ts \
  --include=src/app/features/discovery/data-access/project-reviews-api.spec.ts \
  --include=src/app/features/discovery/ui/conversation-thread.spec.ts \
  --include=src/app/features/discovery/ui/profile-block-control.spec.ts \
  --include=src/app/features/discovery/ui/project-management.spec.ts \
  --include=src/app/features/discovery/ui/project-opportunity-skills.spec.ts \
  --include=src/app/features/discovery/ui/project-reviews.spec.ts \
  --include=src/app/features/discovery/ui/project-teams.spec.ts \
  --include=src/app/features/discovery/ui/public-project-detail.spec.ts \
  --include=src/app/features/discovery/ui/requests-view.spec.ts \
  --reporters=json \
  --output-file="$frontend_report" >"$frontend_log" 2>&1; then
  cat "$frontend_log"
  exit 1
fi

python3 - "$frontend_report" <<'PY'
import json
import sys
from pathlib import Path

expected = {
    "src/app/features/discovery/data-access/current-projects-api.spec.ts",
    "src/app/features/discovery/data-access/project-reviews-api.spec.ts",
    "src/app/features/discovery/ui/conversation-thread.spec.ts",
    "src/app/features/discovery/ui/profile-block-control.spec.ts",
    "src/app/features/discovery/ui/project-management.spec.ts",
    "src/app/features/discovery/ui/project-opportunity-skills.spec.ts",
    "src/app/features/discovery/ui/project-reviews.spec.ts",
    "src/app/features/discovery/ui/project-teams.spec.ts",
    "src/app/features/discovery/ui/public-project-detail.spec.ts",
    "src/app/features/discovery/ui/requests-view.spec.ts",
}

path = Path(sys.argv[1])
if not path.is_file() or path.stat().st_size == 0:
    raise SystemExit("El runner frontend no generó un reporte de pruebas.")
try:
    report = json.loads(path.read_text())
except (OSError, json.JSONDecodeError) as error:
    raise SystemExit(f"El reporte frontend no es válido: {error}")

results = report.get("testResults", [])
actual = {
    str(Path(result.get("name", "")).resolve()).split("/frontend/", 1)[-1]
    for result in results
}
missing = expected - actual
unexpected = actual - expected
if missing or unexpected:
    raise SystemExit(
        f"Las suites frontend no coinciden; faltantes={sorted(missing)}, inesperadas={sorted(unexpected)}."
    )

tests = report.get("numTotalTests", 0)
passed = report.get("numPassedTests", 0)
failed = report.get("numFailedTests", 0)
omitted = report.get("numPendingTests", 0) + report.get("numTodoTests", 0) + report.get("numSkippedTests", 0)
if (
    not report.get("success")
    or tests <= 0
    or passed != tests
    or failed != 0
    or omitted != 0
    or any(result.get("status") != "passed" or not result.get("assertionResults") for result in results)
    or any(assertion.get("status") != "passed" for result in results for assertion in result.get("assertionResults", []))
):
    raise SystemExit(
        f"Frontend incompleto o fallido: {tests} totales, {passed} aprobados, {failed} fallidos, {omitted} omitidos."
    )

print(f"Frontend: {len(results)} archivos, {passed} pruebas aprobadas, 0 fallos, 0 omitidas.")
PY

printf '%s\n' 'Ejecutando pruebas backend específicas de PUL-112...'
if ! sh ./backend/gradlew -p backend test \
  --tests com.pulso.backend.connection.api.ConnectionIntentControllerTest \
  --tests com.pulso.backend.connection.api.ConnectionIntentSecurityTest \
  --tests com.pulso.backend.connection.infrastructure.JdbcConnectionIntentRepositoryTest \
  --tests com.pulso.backend.opportunity.api.ProjectOpportunityControllerTest \
  --tests com.pulso.backend.opportunity.api.ProjectOpportunitySecurityTest \
  --tests com.pulso.backend.opportunity.api.ProjectOpportunitySkillsSecurityTest \
  --tests com.pulso.backend.opportunity.application.ManageOpportunitySkillsTest \
  --tests com.pulso.backend.opportunity.application.ManageProjectOpportunitiesTest \
  --tests com.pulso.backend.opportunity.domain.OpportunityLifecycleTest \
  --tests com.pulso.backend.opportunity.infrastructure.JdbcOpportunitySkillRepositoryTest \
  --tests com.pulso.backend.opportunity.infrastructure.JdbcProjectOpportunityRepositoryTest \
  --tests com.pulso.backend.profile.api.ProfileBlockControllerTest \
  --tests com.pulso.backend.profile.application.ManageProfileBlocksTest \
  --tests com.pulso.backend.profile.infrastructure.JdbcProfileBlockRepositoryTest \
  --tests com.pulso.backend.project.api.CurrentProjectsControllerTest \
  --tests com.pulso.backend.project.api.ProjectLifecycleControllerTest \
  --tests com.pulso.backend.project.api.ProjectLifecycleSecurityTest \
  --tests com.pulso.backend.project.api.ProjectUpdateControllerTest \
  --tests com.pulso.backend.project.api.ProjectUpdateSecurityTest \
  --tests com.pulso.backend.project.domain.ProjectLifecycleTest \
  --tests com.pulso.backend.project.domain.ProjectReviewTest \
  --tests com.pulso.backend.project.domain.ProjectUpdatePatchTest \
  --tests com.pulso.backend.project.infrastructure.JdbcCurrentProjectsRepositoryTest \
  --tests com.pulso.backend.project.infrastructure.JdbcProjectLifecycleRepositoryTest \
  --tests com.pulso.backend.project.infrastructure.JdbcProjectUpdateRepositoryTest \
  --tests com.pulso.backend.project.infrastructure.ProjectReviewIntegrationTest \
  --tests com.pulso.backend.report.api.ReportControllerTest \
  --tests com.pulso.backend.report.infrastructure.ReportIntegrationTest \
  --rerun-tasks >"$backend_log" 2>&1; then
  cat "$backend_log"
  exit 1
fi

python3 - backend/build/test-results/test <<'PY'
import sys
from pathlib import Path
from xml.etree import ElementTree

expected = {
    "com.pulso.backend.connection.api.ConnectionIntentControllerTest",
    "com.pulso.backend.connection.api.ConnectionIntentSecurityTest",
    "com.pulso.backend.connection.infrastructure.JdbcConnectionIntentRepositoryTest",
    "com.pulso.backend.opportunity.api.ProjectOpportunityControllerTest",
    "com.pulso.backend.opportunity.api.ProjectOpportunitySecurityTest",
    "com.pulso.backend.opportunity.api.ProjectOpportunitySkillsSecurityTest",
    "com.pulso.backend.opportunity.application.ManageOpportunitySkillsTest",
    "com.pulso.backend.opportunity.application.ManageProjectOpportunitiesTest",
    "com.pulso.backend.opportunity.domain.OpportunityLifecycleTest",
    "com.pulso.backend.opportunity.infrastructure.JdbcOpportunitySkillRepositoryTest",
    "com.pulso.backend.opportunity.infrastructure.JdbcProjectOpportunityRepositoryTest",
    "com.pulso.backend.profile.api.ProfileBlockControllerTest",
    "com.pulso.backend.profile.application.ManageProfileBlocksTest",
    "com.pulso.backend.profile.infrastructure.JdbcProfileBlockRepositoryTest",
    "com.pulso.backend.project.api.CurrentProjectsControllerTest",
    "com.pulso.backend.project.api.ProjectLifecycleControllerTest",
    "com.pulso.backend.project.api.ProjectLifecycleSecurityTest",
    "com.pulso.backend.project.api.ProjectUpdateControllerTest",
    "com.pulso.backend.project.api.ProjectUpdateSecurityTest",
    "com.pulso.backend.project.domain.ProjectLifecycleTest",
    "com.pulso.backend.project.domain.ProjectReviewTest",
    "com.pulso.backend.project.domain.ProjectUpdatePatchTest",
    "com.pulso.backend.project.infrastructure.JdbcCurrentProjectsRepositoryTest",
    "com.pulso.backend.project.infrastructure.JdbcProjectLifecycleRepositoryTest",
    "com.pulso.backend.project.infrastructure.JdbcProjectUpdateRepositoryTest",
    "com.pulso.backend.project.infrastructure.ProjectReviewIntegrationTest",
    "com.pulso.backend.report.api.ReportControllerTest",
    "com.pulso.backend.report.infrastructure.ReportIntegrationTest",
}

directory = Path(sys.argv[1])
reports = {path.stem.removeprefix("TEST-"): path for path in directory.glob("TEST-*.xml")}
missing = expected - reports.keys()
if missing:
    raise SystemExit(f"Faltan reportes JUnit backend: {sorted(missing)}.")

total = skipped = failures = errors = 0
for name in sorted(expected):
    try:
        suite = ElementTree.parse(reports[name]).getroot()
    except (OSError, ElementTree.ParseError) as error:
        raise SystemExit(f"Reporte JUnit inválido para {name}: {error}")
    if suite.tag != "testsuite" or suite.attrib.get("name") != name:
        raise SystemExit(f"Reporte JUnit no corresponde a {name}.")
    suite_tests = int(suite.attrib.get("tests", "0"))
    suite_skipped = int(suite.attrib.get("skipped", "0"))
    suite_failures = int(suite.attrib.get("failures", "0")) + len(suite.findall("failure"))
    suite_errors = int(suite.attrib.get("errors", "0")) + len(suite.findall("error"))
    if suite_tests <= 0:
        raise SystemExit(f"La suite backend {name} está vacía.")
    total += suite_tests
    skipped += suite_skipped
    failures += suite_failures
    errors += suite_errors

if skipped or failures or errors:
    raise SystemExit(
        f"Backend incompleto o fallido: {total} totales, {skipped} omitidos, {failures} fallidos, {errors} errores."
    )

print(f"Backend: {len(expected)} suites, {total} pruebas aprobadas, 0 fallos, 0 omitidas.")
PY

printf '%s\n' 'PUL_112_QA_OK'
