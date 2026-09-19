# Installation and setup

Use Java 21 and Docker Desktop. Maven is supplied by the repository wrapper. MongoDB must be a writable `rs0` replica set before integration tests or transaction-backed application flows run.

## macOS — Bash or Zsh

Install Git, a Java 21 JDK and Docker Desktop; start Docker Desktop. IntelliJ IDEA is optional.

```sh
git --version
java -version
docker --version
docker compose version
```

If Java is missing or the wrong version is selected, install a Java 21 JDK, then set the current shell (optionally place these in your shell profile):

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

Clone and start infrastructure:

```sh
git clone https://github.com/the-sagar/petstore-modernized.git
cd petstore-modernized
docker compose up -d mongodb artemis
docker compose ps
docker exec petstore-mongodb mongosh --quiet --eval 'db.adminCommand({ping:1})'
docker exec petstore-mongodb mongosh --quiet --eval 'rs.status().ok'
```

Ping should include `ok: 1`. Replica status should return `1`. On a **fresh, uninitialized** volume, status instead reports that no replica-set configuration exists. Initialize exactly once:

```sh
docker exec petstore-mongodb mongosh --eval \
'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})'
```

Wait a few seconds for election, then verify:

```sh
docker exec petstore-mongodb mongosh --quiet --eval 'rs.status().ok'
docker exec petstore-mongodb mongosh --quiet --eval 'db.hello().isWritablePrimary'
./mvnw clean verify
```

Expected results: `1`, `true`, then BUILD SUCCESS. Do not reinitialize an already configured replica set. The advertised localhost address is appropriate because services run on the host, not in application containers.

Run each service in a **separate terminal**, from the repository root:

```sh
./mvnw -pl storefront-service spring-boot:run
./mvnw -pl order-processing-service spring-boot:run
./mvnw -pl supplier-service spring-boot:run
```

## Windows 10/11 — PowerShell

Install Git for Windows, a Java 21 JDK and Docker Desktop using **Linux containers with the WSL2 backend**. Start Docker Desktop. IntelliJ IDEA is optional. These commands are for PowerShell, not cmd.exe.

```powershell
git --version
java -version
docker --version
docker compose version
```

If Java is not detected, set `JAVA_HOME` to your actual JDK installation and put its `bin` directory on PATH. For the current terminal, replacing the example path:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
```

For persistent configuration use Windows Environment Variables, then reopen terminals/IntelliJ.

```powershell
git clone https://github.com/the-sagar/petstore-modernized.git
cd petstore-modernized
docker compose up -d mongodb artemis
docker compose ps
docker exec petstore-mongodb mongosh --quiet --eval "db.adminCommand({ping:1})"
docker exec petstore-mongodb mongosh --quiet --eval "rs.status().ok"
```

Ping should include `ok: 1`; initialized replica status is `1`. **Only if uninitialized**, run this single line:

```powershell
docker exec petstore-mongodb mongosh --eval "rs.initiate({_id:'rs0',members:[{_id:0,host:'localhost:27017'}]})"
```

Outer PowerShell double quotes and inner JavaScript single quotes avoid native embedded-double-quote differences between Windows PowerShell 5.1 and newer PowerShell. The commonly shown outer-single/inner-double version is suitable for PowerShell 7.3+ with standard Windows native argument passing, but should not be assumed portable to 5.1. Microsoft's [native argument parsing documentation](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.core/about/about_parsing?view=powershell-7.4) explains the difference.

If an escaped-double-quote alternative is needed on Windows, `--%` stops PowerShell parsing for the rest of this single literal line; backslash escapes are then for the native executable:

```powershell
docker --% exec petstore-mongodb mongosh --eval "rs.initiate({_id:\"rs0\",members:[{_id:0,host:\"localhost:27017\"}]})"
```

Use **one** initialization command, not both. After election:

```powershell
docker exec petstore-mongodb mongosh --quiet --eval "rs.status().ok"
docker exec petstore-mongodb mongosh --quiet --eval "db.hello().isWritablePrimary"
.\mvnw.cmd clean verify
```

Expect `1`, `true`, and BUILD SUCCESS. Run in **three separate PowerShell terminals**, from the root:

```powershell
.\mvnw.cmd -pl storefront-service spring-boot:run
.\mvnw.cmd -pl order-processing-service spring-boot:run
.\mvnw.cmd -pl supplier-service spring-boot:run
```

The Windows wrapper is `.\mvnw.cmd`; macOS uses `./mvnw`. Windows commands were reviewed for PowerShell argument handling, not executed on a Windows host during this documentation pass.

## IntelliJ — both platforms

1. Open the **repository root**, not separate service folders; import the root `pom.xml` as a Maven project.
2. Select Java 21 for Project SDK and Maven runner/importer.
3. Create Spring Boot run configurations (or Java Application configurations if Spring integration is unavailable):
   - **Storefront Service:** `com.mdb.petstore.PetstoreModernizedApplication`, storefront module.
   - **Order Processing Service:** `com.mdb.petstore.orderprocessing.OrderProcessingApplication`, order-processing module.
   - **Supplier Service:** `com.mdb.petstore.supplier.SupplierApplication`, supplier module.
4. Create Compound configuration **Petstore - All Services**, selecting those three configurations.
5. Start Docker infrastructure and initialize Mongo before running the compound configuration.
6. Open **http://localhost:8080**. Services listen on 8080, 8081 and 8082 respectively. Ports 8081/8082 are backend APIs; `/` returning 404 is expected.

## Infrastructure and broker configuration

Compose runs one MongoDB 7 container (`petstore-mongodb`) with one-node `rs0` and persistent data, plus Artemis 2.57.0 (`petstore-artemis`). Mongo's three logical databases are `petstore_storefront`, `petstore_orders` and `petstore_supplier`; each service accesses only its own.

Artemis uses `apache/artemis:2.57.0-alpine`, broker port **61616**, console **http://localhost:8161/console**, and heap `-Xms128m -Xmx256m`. Broker ports bind to loopback. Default local credentials are **petstore / petstore-dev**. Set `ARTEMIS_USER` and `ARTEMIS_PASSWORD` in the Compose shell and in Order Processing/Supplier runtime environments to override them consistently. Broker volume initialization retains configuration; changing environment variables alone is not a password-rotation procedure for an existing broker instance.

Mongo replica-set transactions are needed locally for registration and Supplier stock/progress consistency. A single node is not production HA.

## Environment and demo accounts

**Non-secret LOCAL DEMO defaults only:** Admin `admin/admin`, Supplier `supplier/supplier`. Customers register at `/register`. Override both username and password for a role before starting Storefront.

macOS Bash/Zsh, substituting your local values:

```sh
export PETSTORE_ADMIN_USERNAME='your-local-admin'
export PETSTORE_ADMIN_PASSWORD='replace-with-your-local-value'
export PETSTORE_SUPPLIER_USERNAME='your-local-supplier'
export PETSTORE_SUPPLIER_PASSWORD='replace-with-your-local-value'
```

Windows PowerShell:

```powershell
$env:PETSTORE_ADMIN_USERNAME = 'your-local-admin'
$env:PETSTORE_ADMIN_PASSWORD = 'replace-with-your-local-value'
$env:PETSTORE_SUPPLIER_USERNAME = 'your-local-supplier'
$env:PETSTORE_SUPPLIER_PASSWORD = 'replace-with-your-local-value'
```

In IntelliJ: **Run → Edit Configurations → Storefront Service → Environment variables**. Set all required overrides there; a shell export does not necessarily reach an IDE launched from the desktop. Never commit real credentials.

Bootstrap stores BCrypt hashes, creates no Customer for operational users, and is idempotent. If either configured field is blank it skips that role. An existing username is left unchanged: changing an environment variable does not reset its password or add a role. Login redirect precedence is ADMIN, then SUPPLIER, then customer Shop.

## Build and test expectations

From the root use `./mvnw clean verify` on macOS or `.\mvnw.cmd clean verify` on Windows. The last reported baseline is **215 passing tests** across all three modules. This documentation pass could not reconfirm that count because local Docker/MongoDB was unavailable. The final `./mvnw clean verify` ended with BUILD FAILURE: Storefront ran 149 tests (5 passed, 144 errors, 0 assertion failures); Mongo connection/context startup errors prevented the remaining two modules from running. Java 21 and reachable MongoDB with initialized `rs0` are required. Tests use isolated Mongo databases and mock/isolate JMS and downstream HTTP where applicable; regular verification needs neither manual browser interaction nor a live Artemis broker. The end-to-end demo needs all infrastructure/services.

## Stop, reset and troubleshoot

Stop applications with Ctrl+C or IntelliJ Stop. Infrastructure shutdown preserves volumes:

```text
docker compose down
```

**Destructive full local demo-data reset:**

```text
docker compose down -v
```

`-v` deletes persistent Mongo **and Artemis** demo data, including accounts, orders, stock and broker messages. Only use it when that loss is intended. Start infrastructure again and reinitialize `rs0`; seeds/bootstrap then recreate baseline data.

| Symptom | Check/action |
|---|---|
| 27017 already in use | Stop the conflicting local Mongo/container, or deliberately align all Mongo URIs with a chosen alternate port |
| 61616 or 8161 already in use | Stop the conflicting broker/container; inspect `docker compose ps` |
| 8080/8081/8082 already in use | Stop duplicate IDE/terminal service processes; keep configured client URLs aligned if changing ports |
| “Transaction numbers are only allowed…” | Verify Mongo runs with `--replSet rs0`, initialize if fresh, and check writable primary; URI must include `replicaSet=rs0` |
| Checkout 502 | Check Order Processing and its logs/broker connection. Cart is retained; persistence may have preceded an acknowledgement/send failure, so avoid blind repeated submissions |
| Order remains APPROVED | Check Supplier/listener/broker availability. A failed InventoryRequested send after approval requires replay/reconciliation; restarting alone may not recover a publication gap |
| Supplier page reports unavailable | Start Supplier Service and check its Mongo/Artemis dependencies and Storefront base URL |
| Admin/Supplier page 403 | Log in with the matching role. ADMIN alone is not SUPPLIER; bootstrap does not promote existing usernames |
| Backend `/` on 8081/8082 returns 404 | Expected: use Storefront 8080 for browser interaction |
| Initial Maven dependency download fails | Check network/proxy settings and Java/Maven environment, then retry |

Continue with the [interview demo](09-demo-and-verification.md).
