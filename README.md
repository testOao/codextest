# Joget / Shark XPDL analyzer

Spring Boot 3.5, Java 17 or newer application for static analysis of Joget workflow XPDL exports. No database or Shark installation is required.

## Run

Install a full JDK 17 or newer and Maven 3.9+, then run from this directory:

```sh
mvn test
mvn spring-boot:run
```

Open the application on port 8080 and upload an XML/XPDL file, or use the JSON API:

```sh
curl -f -H 'Content-Type: application/xml' --data-binary @examples/approval.xpdl http://localhost:8080/api/analyze
curl -f -F file=@examples/approval.xpdl http://localhost:8080/api/analyze
```

To package: `mvn package`, then `java -jar target/joget-shark-analyzer-1.0.0.jar`.

## Analysis

Reports package/process identity, activity types, performers, subprocess references, transition conditions, participants, and inferred graph starts/ends. Checks missing/duplicate process, activity and transition IDs, broken transition endpoints, disconnected activities, unresolved performers/subflows, and missing start/end candidates. Both namespace-qualified and unqualified XPDL are supported. Participants declared at package or process scope are recognized.

Start/end candidates are graph heuristics, not authoritative Shark start/termination semantics. Dynamic performer expressions and external subflows produce warnings requiring review. This is not XPDL schema validation or a complete Shark compatibility validator. ActivitySet/block internals, join/split correctness, script execution, Joget plugin/form mappings, live engine instances, and runtime deadlocks are not analyzed. No uploaded XML is persisted. DOCTYPE and external entities are disabled. Use only trusted users on a private development network; add authentication and deployment request-size controls before public exposure. Multipart uploads are limited to 2 MB; direct XML is checked after reading, so production deployments must also limit request bodies at the ingress.

Example input is in `examples/approval.xpdl`.

### This cloud workspace

Maven is installed at `/workspace/apache-maven-3.9.11`. The workspace JDK lacks `ct.sym`; use source/target compilation instead of `--release` here. The external settings file configures the managed network proxy (no credentials):

```sh
/workspace/apache-maven-3.9.11/bin/mvn -s /workspace/maven-settings.xml -Dmaven.compiler.release= -Dmaven.compiler.source=17 -Dmaven.compiler.target=17 package
java -jar target/joget-shark-analyzer-1.0.0.jar
```

A full Java 17 or newer JDK on another machine can use the standard commands above.

If compilation reports an unsupported release, run `mvn -version` and confirm its Java runtime is a full JDK 17 or newer. Set `JAVA_HOME` to that JDK and restart your terminal or IDE before rebuilding. The Java version used by Maven can differ from `java -version`.
