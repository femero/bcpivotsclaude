---
description: Format the code and run the single quality gate
allowed-tools: Bash(mvn spotless:apply), Bash(mvn clean verify)
---

Run `mvn spotless:apply`, then `mvn clean verify` (Checkstyle -> compile -> tests -> Spotless check).
Report any failure with its output.
