---
id: task-058
title: testing/certs/backup
status: To Do
assignee: []
created_date: '2025-11-07 14:32'
labels: []
dependencies: []
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
fer portabilitat del testing/certs/regenerate-certs.sh; a la metodologia basada en kms;
dins de testing/ hi ha el docker-compose.yml que té el servei kms
molt important el .db amb les dades del kms han de ser a testing/data/kms
i els certificats generats amb kms han de ser a testing/certs però no amb l'script que usa openssl per generar certificats sinó amb un script que usa el ckms del contrib/ i genera els certificats en base a les extensions (.ext) necessàries que posem al mateix propi directori
després fem un testing per assegurar-nos que els certificats estan ben fets; sobretot que la CA està com ha d'estar. Això ho has d'aprendre de com els certs han estat generats amb openssl
<!-- SECTION:DESCRIPTION:END -->
