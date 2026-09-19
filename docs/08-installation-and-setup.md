# Installation and setup

This guide starts with a clean machine and ends with Petstore running locally. Choose **one** complete path: [macOS](#macos) or [Windows 10/11 with PowerShell](#windows-1011-with-powershell). Then follow the shared IntelliJ, first-run and verification sections. You need an internet connection for installers, container images and Maven dependencies, and permission to install software on your machine.

Git downloads the source code; JDK 21 compiles and runs the three Java services; Docker Desktop runs their database and message broker. IntelliJ IDEA is an optional, recommended editor and debugger. Applications run on your host machine; MongoDB and Artemis run in Docker containers.

## Software you do not install separately

| Software | How this project supplies it |
| --- | --- |
| Maven | The repository includes Maven Wrapper (`mvnw` / `mvnw.cmd`), which downloads the required Maven version on first use. Do **not** install Maven separately. |
| MongoDB 7 | Docker Compose downloads and runs `mongo:7.0`. No native database installation is required. |
| ActiveMQ Artemis | Docker Compose downloads and runs `apache/artemis:2.57.0-alpine`. No native broker installation is required. |
| Spring Boot | Maven resolves it as a project dependency; there is no separate installer or CLI requirement. |
| mongosh | Commands below execute the shell already included inside the Mongo container. |
| Node/npm | This project does not use them. |

## macOS

Use **Terminal** (Applications → Utilities → Terminal), with the default Zsh shell. Run commands one line at a time, without adding a prompt character. First detect your architecture:

```sh
uname -m
```

`arm64` means Apple silicon; `x86_64` means Intel. Use this result when selecting installers. If Terminal is running through Rosetta on an Apple silicon Mac, reopen it without Rosetta and check again; Apple menu → About This Mac also identifies the chip.

### A. Install Git

Git is needed to clone the repository and track source changes. The simplest installation is Apple's Command Line Tools, which include Git; full Xcode is not required. See the official [Git macOS installation page](https://git-scm.com/install/mac).

```sh
xcode-select --install
```

Accept the installation dialog and wait for it to finish. Open a new Terminal and verify:

```sh
git --version
```

Expect `git version ...` (possibly with an Apple Git suffix). If the tools are already installed, that message is normal: run the verification. If Git is still missing, complete the Command Line Tools installation and check macOS Software Update.

Alternatively, **if you already use Homebrew**, install Git with:

```sh
brew install git
git --version
```

Homebrew is optional and is not a project prerequisite. Choose one Git installation approach.

### B. Install Java JDK 21

The JDK provides the compiler and runtime for building, testing and running Petstore. Install **JDK 21**, not just a JRE or whichever Java version the download page selects by default.

1. Open [Eclipse Adoptium / Temurin downloads](https://adoptium.net/temurin/releases/?version=21).
2. Select **JDK**, version **21**, **macOS**, and **aarch64 / ARM64** for Apple silicon or **x64** for Intel.
3. Download the **.pkg**, open it and complete the installer. See [Adoptium's macOS installer instructions](https://adoptium.net/installation/macOS/).
4. Close and reopen Terminal.

Verify the installed JDKs and the currently selected Java:

```sh
/usr/libexec/java_home -V
java -version
```

Expect a JDK 21 entry and Java/OpenJDK version `21...`. If another version is selected, set Java 21 for the current Terminal:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

`JAVA_HOME` points to the JDK's home directory; `PATH` tells the shell where to find its commands. Persist the selection for future Zsh terminals by running these lines **once**:

```sh
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
echo "$JAVA_HOME"
java -version
javac -version
```

The single quotes preserve the expressions when writing the file so they are evaluated at shell startup. Expect a JDK 21 home path and both Java and `javac` version 21. If `java_home` cannot find version 21, check that you installed the JDK .pkg for your architecture. If you already have Java settings in `~/.zshrc`, update those instead of appending competing entries.

### C. Install Docker Desktop

Docker Desktop supplies the container engine and Docker Compose used to run MongoDB and Artemis.

1. Open the official [Docker Desktop for Mac installation page](https://docs.docker.com/desktop/setup/install/mac-install/), and check its supported macOS versions.
2. Download the **Apple silicon** or **Intel** installer matching your machine.
3. Open `Docker.dmg` and drag **Docker** into **Applications**.
4. Start Docker Desktop from Applications, review/accept its terms, and configure the requested permissions.
5. Wait until Docker Desktop reports that the engine is running.

Docker Desktop requires **at least 4 GB RAM on macOS**. This project is more comfortable with additional free memory: IntelliJ, three JVMs (Java application processes), Mongo and Artemis run together. A machine with 16 GB total RAM is a practical recommendation for this workflow, not a Docker minimum.

Verify in Terminal:

```sh
docker --version
docker compose version
docker info
```

Expect Docker and Compose version numbers, then both client and server/engine information. **Do not continue until `docker info` succeeds.** If the command is missing, finish Docker Desktop's command-line tool setup and reopen Terminal. If it cannot connect to the daemon, start Docker Desktop and wait for engine startup.

### D. Install IntelliJ IDEA (optional, recommended)

IntelliJ provides code navigation, Maven import, debugging and a convenient way to launch all three services.

1. Download current [IntelliJ IDEA from JetBrains](https://www.jetbrains.com/idea/download/), selecting macOS and the architecture matching your machine.
2. Open the downloaded disk image and drag IntelliJ IDEA into Applications.
3. Launch it and finish the initial setup. See [JetBrains installation instructions](https://www.jetbrains.com/help/idea/installation-guide.html).
4. No separate Java runtime is needed to launch IntelliJ: it bundles its own. **The project still requires the JDK 21 installed above.** Configure Project SDK = JDK 21 after cloning.

Verify the installation by launching it from Terminal:

```sh
open -a "IntelliJ IDEA"
```

Expect the welcome screen or IDE window. If macOS cannot find the app, confirm it was copied into Applications. The [IntelliJ project setup](#intellij-project-setup) below configures Maven and the services. If you skip IntelliJ, use the terminal launch commands at the end of this macOS path.

### E. Clone the project and verify Maven Wrapper

Choose a folder for your source checkout in Terminal, then run:

```sh
git clone https://github.com/the-sagar/petstore-modernized.git
cd petstore-modernized
./mvnw -version
```

Expect Maven version information and **Java version: 21...**. Do not install Maven separately. The first wrapper invocation downloads Maven and can take time. If execution is denied, run `chmod +x mvnw` and retry. Run the remaining project commands from this repository root (the folder containing `pom.xml` and `compose.yaml`).

### F. Start project infrastructure

Compose automatically pulls `mongo:7.0` and `apache/artemis:2.57.0-alpine`; no native MongoDB or Artemis installation is needed. The first pull can take several minutes.

```sh
docker compose pull
docker compose up -d mongodb artemis
docker compose ps
```

Expect both containers to be running (Mongo becomes healthy). Check Mongo:

```sh
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
```

Expected results: `1` and `true`. Do not reinitialize an already configured replica set. The advertised localhost address is appropriate because services run on the host, not in application containers.

Check Artemis startup:

```sh
docker compose logs artemis
```

Expect successful broker startup with no fatal startup error. Open **http://localhost:8161/console** and log in with **petstore / petstore-dev**. If startup fails, resolve it before launching the services.

### G. Build the project

Confirm the wrapper uses Java 21, then compile and run verification:

```sh
./mvnw -version
./mvnw clean verify
```

Expect **Java version: 21...** and finally **BUILD SUCCESS** for all three modules. Mongo must be reachable and writable before this build; dependency downloads can take time.

### H. Start the services

Continue to [IntelliJ project setup](#intellij-project-setup) to create the Compound configuration. Alternatively, run each service in a **separate Terminal window**, first changing to the repository root in each:

```sh
./mvnw -pl storefront-service spring-boot:run
./mvnw -pl order-processing-service spring-boot:run
./mvnw -pl supplier-service spring-boot:run
```

## Windows 10/11 with PowerShell

Use **Windows PowerShell** or PowerShell from Windows Terminal. Open it from Start/Search. These commands are not for cmd.exe or a WSL Linux terminal. Do not use Git Bash unless you understand the shell differences. Use a normal PowerShell window except when an administrator window is explicitly requested.

Before installing Docker, check the current [Docker Windows requirements](https://docs.docker.com/desktop/setup/install/windows-install/). Docker supports Windows releases within Microsoft's servicing timeline; a Windows 10/11 label alone does not guarantee support. Update to a supported Windows release if needed. The current Windows requirements include 8 GB RAM and hardware virtualization; additional free memory helps when running IntelliJ and all services together.

### A. Install Git

Git downloads the project and tracks source changes.

1. Open the official [Git for Windows download page](https://git-scm.com/install/windows).
2. Download and run the installer for your machine.
3. Defaults are acceptable unless your organization requires otherwise. Keep Git available to command-line and third-party tools.
4. Close and reopen PowerShell so it sees the updated PATH.

Verify:

```powershell
git --version
```

Expect `git version ...`, usually with a Windows suffix. If Git is not recognized, reopen PowerShell or rerun the installer to enable command-line PATH integration.

If you already have `winget`, this is an optional alternative to the manual installer:

```powershell
winget install --id Git.Git -e
```

Reopen PowerShell and run the same verification. `winget` is not required; use the manual installer if it is unavailable.

### B. Install Java JDK 21

Java's JDK contains the compiler and runtime needed to build, test and run the services.

1. Open [Eclipse Adoptium / Temurin downloads](https://adoptium.net/temurin/releases/?version=21).
2. Select **JDK**, version **21**, **Windows**, and **x64** for normal Intel/AMD PCs. Check Settings → System → About → System type if unsure. Select the appropriate architecture on other hardware; Adoptium's MSI instructions currently cover x64, so check available packages before proceeding on ARM.
3. Download the **MSI** and run it.
4. On Custom Setup, enable **Add to PATH** and **Set/Update JAVA_HOME** when offered. Follow [Adoptium's Windows installer instructions](https://adoptium.net/installation/windows/).
5. Finish and reopen PowerShell.

Verify:

```powershell
java -version
javac -version
$env:JAVA_HOME
```

Expect Java/OpenJDK and `javac` version `21...`, plus a JDK 21 directory for `JAVA_HOME`. Install a JDK, not only a JRE. If Java is missing or shows another version, fix the environment selection below.

If `JAVA_HOME` is missing or incorrect:

1. Use Windows Search → **Edit the system environment variables** → **Environment Variables**.
2. Create or edit `JAVA_HOME` under System variables (administrator access may be required), pointing to the actual JDK 21 installation directory, usually under `C:\Program Files\Eclipse Adoptium`. Do not append `\bin` or include quotes in the value.
3. Edit `Path` in the same scope and add `%JAVA_HOME%\bin`. Move it ahead of conflicting Java entries where necessary. User variables can be used if system changes are unavailable; check for conflicting system PATH entries.
4. Confirm the dialogs and reopen PowerShell and IntelliJ.

For the **current PowerShell session only**, you can set:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21..."
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
java -version
$env:JAVA_HOME
```

Replace `jdk-21...` with the actual directory shown in File Explorer; it is a placeholder, not a literal folder name. Patch-level directory names vary. `Get-Command java` shows which executable PowerShell selected if versions still disagree.

### C. Enable WSL 2

Windows Subsystem for Linux (WSL 2) supplies the Linux environment used by the recommended Docker backend. You still run Java, Git and project commands in Windows PowerShell.

1. Search for PowerShell, right-click it, select **Run as administrator**, and accept the elevation prompt.
2. Check WSL:

```powershell
wsl --version
```

If WSL is missing, install it:

```powershell
wsl --install
```

Restart Windows if requested, then reopen administrator PowerShell. Installation may also install Ubuntu and ask you to create a Linux username/password on first launch; complete that prompt, then return to PowerShell. Do **not** install MongoDB or Artemis inside WSL.

Update WSL and select version 2 for new distributions:

```powershell
wsl --update
wsl --set-default-version 2
wsl --version
wsl --status
```

Expect WSL and kernel version information, and default version **2**. Docker currently requires WSL **2.1.5 or later**; the WSL package version and default distribution version are different numbers. If `--version` is unsupported, the older Windows-provided WSL needs updating. Follow [Microsoft's WSL installation guide](https://learn.microsoft.com/en-us/windows/wsl/install) if installation shows help text or fails on an older Windows build.

If WSL or Docker says virtualization is unavailable, enable hardware virtualization (often Intel VT-x or AMD-V/SVM) in BIOS/UEFI using your computer manufacturer's instructions, then restart. Task Manager → Performance → CPU shows virtualization status. On a managed machine, ask your administrator to enable it. Finish WSL setup before installing Docker.

### D. Install Docker Desktop

Docker Desktop provides the engine and Compose plugin that run the project's Linux containers.

1. Download the installer from the official [Docker Desktop for Windows page](https://docs.docker.com/desktop/setup/install/windows-install/), matching your architecture.
2. Run it and choose the **WSL 2 backend** (the “Use WSL 2 instead of Hyper-V” option where offered).
3. Complete installation and restart or sign out if requested.
4. Launch Docker Desktop from Start, complete its initial setup, and wait for engine startup.
5. In **Settings → General**, verify **Use the WSL 2 based engine** where applicable.
6. This project uses **Linux containers**. If Docker is in Windows-container mode, use its tray menu to switch to Linux containers.

In a new **normal PowerShell** window, verify:

```powershell
docker --version
docker compose version
docker info
```

Expect Docker/Compose version numbers and engine/server details, with `OSType: linux`. **All three commands must work before continuing.** If Docker is not recognized, reopen PowerShell after installation. A daemon connection error means the engine is not ready; check Docker Desktop and the WSL checks above.

### E. Install IntelliJ IDEA (optional, recommended)

IntelliJ supplies code navigation, Maven support and debugging, plus one-click startup of the three services.

1. Download the Windows installer from [JetBrains IntelliJ IDEA downloads](https://www.jetbrains.com/idea/download/).
2. Run the installer for your architecture and follow the wizard.
3. Useful optional choices include a launcher shortcut, adding the command-line launcher to PATH, and file associations. See [JetBrains installation instructions](https://www.jetbrains.com/help/idea/installation-guide.html).
4. Launch IntelliJ from Start. Its own Java runtime is bundled, but JDK 21 is still required for this project.
5. After cloning, configure Project SDK and Maven to use JDK 21 as described below.

Verify by opening IntelliJ and checking that its welcome screen appears. If you enabled the PATH launcher, reopen PowerShell and also check:

```powershell
Get-Command idea64.exe
```

Expect the installed launcher path. If absent, use the Start menu or enable the installer's PATH option; the launcher is optional. If you skip IntelliJ, use the terminal commands at the end of this Windows path.

### F. Clone the project and verify Maven Wrapper

Choose a local source folder in PowerShell, then run:

```powershell
git clone https://github.com/the-sagar/petstore-modernized.git
cd petstore-modernized
.\mvnw.cmd -version
```

Do **not** install Maven separately. The wrapper downloads the required Maven version on first use. Expect Maven information and **Java version: 21...**. If it selects a different Java, correct `JAVA_HOME` and reopen PowerShell. Run remaining commands from the repository root containing `pom.xml` and `compose.yaml`.

### G. Start project infrastructure

Compose downloads `mongo:7.0` and `apache/artemis:2.57.0-alpine` automatically. The initial pull can take several minutes. No native MongoDB, Artemis or mongosh installation is needed.

```powershell
docker compose pull
docker compose up -d mongodb artemis
docker compose ps
```

Expect both containers to be running (Mongo becomes healthy). Check Mongo:

```powershell
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
```

Expect `1` and `true`. Do not reinitialize an already configured replica set. The advertised localhost address is correct because the services run on the Windows host.

Check Artemis:

```powershell
docker compose logs artemis
```

Expect successful broker startup and no fatal startup error. Open **http://localhost:8161/console** and log in with **petstore / petstore-dev**.

### H. Build the project

```powershell
.\mvnw.cmd -version
.\mvnw.cmd clean verify
```

Expect **Java version: 21...** and finally **BUILD SUCCESS** across all three modules. Keep Mongo running with a writable `rs0`; allow time for the first dependency downloads.

### I. Start the services

Continue to [IntelliJ project setup](#intellij-project-setup) for the Compound configuration. Alternatively, run in **three separate PowerShell terminals**, first changing to the repository root in each:

```powershell
.\mvnw.cmd -pl storefront-service spring-boot:run
.\mvnw.cmd -pl order-processing-service spring-boot:run
.\mvnw.cmd -pl supplier-service spring-boot:run
```

## IntelliJ project setup

These UI steps apply after completing either OS path. If you started services in terminals, stop them before launching the same services from IntelliJ to avoid duplicate processes and port conflicts.

1. Select **File → Open**, choose the repository root or its root `pom.xml`, and open/import it as a **Maven project**. Do not import the service folders as separate projects.
2. Under **File → Project Structure → Project → SDK**, select **JDK 21**. If absent, use **Add SDK → JDK** and browse to the installed JDK home. Use language level 21 or the SDK default.
3. Open **Settings → Build, Execution, Deployment → Build Tools → Maven** (Settings is under the IntelliJ IDEA menu on macOS). Select **Maven Wrapper** for Maven home.
4. In Maven's **Runner**, set JRE to JDK 21 / Project SDK. In **Importing**, also choose JDK 21 for the importer JDK where the setting is available. Menu wording can vary by IDE version.
5. Wait for dependency downloads and indexing to finish. If modules are missing, use the Maven tool window's reload action for the root project.
6. Open **Run → Edit Configurations → + → Spring Boot**. Create the following configurations, selecting JDK 21 and the corresponding module's classpath (it may be displayed with a `.main` suffix):

| Configuration name | Module | Main class |
| --- | --- | --- |
| Storefront Service | `storefront-service` | `com.mdb.petstore.PetstoreModernizedApplication` |
| Order Processing Service | `order-processing-service` | `com.mdb.petstore.orderprocessing.OrderProcessingApplication` |
| Supplier Service | `supplier-service` | `com.mdb.petstore.supplier.SupplierApplication` |

If Spring Boot configuration support is unavailable, choose **Application** with the same main class, module and JDK. The application can run without paid Spring-specific IDE integration.

7. For each configuration, use the repository root as the working directory. No profile or environment override is required for the local defaults. Set any overrides in that individual configuration's **Environment variables** field; see the table below.
8. Select **+ → Compound**, name it **Petstore - All Services**, and add all three configurations. Apply and save.
9. Keep Docker Desktop running and complete the Mongo primary and Artemis checks from your OS path before running the Compound configuration. It starts Java services, not the infrastructure.

## First run

Select **Petstore - All Services** and click Run. If using terminals instead, keep all three service commands running. Check each console for both the application's `Started ...Application` message and its web-server port:

| Application | Successful startup |
| --- | --- |
| Storefront | `PetstoreModernizedApplication` started, port **8080** |
| Order Processing | `OrderProcessingApplication` started, port **8081** |
| Supplier | `SupplierApplication` started, port **8082** |

Wait until all three have started without startup exceptions, then open **http://localhost:8080**. Use these local demo accounts:

| Role | Username / password |
| --- | --- |
| Admin | `admin` / `admin` |
| Supplier | `supplier` / `supplier` |
| Customer | Register at **http://localhost:8080/register** |

Ports **8081 and 8082 are backend services**. A **404 at their root path `/` is normal**; use Storefront on 8080 for browser interaction.

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

### Runtime environment variables

Leave these unset for the supplied local configuration. In IntelliJ, set them on each relevant service configuration; a desktop-launched IDE may not inherit shell environment changes.

| Variable | Local default | Where used |
| --- | --- | --- |
| `PETSTORE_ADMIN_USERNAME` / `PETSTORE_ADMIN_PASSWORD` | `admin` / `admin` | Storefront bootstrap |
| `PETSTORE_SUPPLIER_USERNAME` / `PETSTORE_SUPPLIER_PASSWORD` | `supplier` / `supplier` | Storefront bootstrap |
| `PETSTORE_SUPPLIER_BASE_URL` | `http://localhost:8082` | Storefront |
| `ARTEMIS_USER` / `ARTEMIS_PASSWORD` | `petstore` / `petstore-dev` | Compose broker initialization and Order Processing/Supplier |
| `ARTEMIS_BROKER_URL` | `tcp://localhost:61616?initialConnectAttempts=1&reconnectAttempts=0&callTimeout=5000` | Order Processing/Supplier |
| `ORDER_SUBMITTED_DESTINATION` | `petstore.order.submitted` | Order Processing |
| `INVENTORY_REQUESTED_DESTINATION` | `petstore.inventory.requested` | Order Processing/Supplier; keep matching |
| `INVENTORY_FULFILLED_DESTINATION` | `petstore.inventory.fulfilled` | Order Processing/Supplier; keep matching |
| `SPRING_MONGODB_URI` | `mongodb://localhost:27017/<service-database>?replicaSet=rs0` | Optional override per service; use its own database listed above |

Storefront's Order Processing URL defaults to `http://localhost:8081`; the Spring property `petstore.order-processing.base-url` can be overridden if deliberately changing that port. Keep dependent client URLs consistent with any service port changes.

## Build and test expectations

The OS-specific build steps use the repository wrapper and require Java 21 plus reachable MongoDB with initialized, writable `rs0`. Tests use isolated Mongo databases and mock/isolate JMS and downstream HTTP where applicable; normal verification does not require browser interaction or a live Artemis broker. The end-to-end demo needs both infrastructure containers and all three services. Judge your run by **BUILD SUCCESS** for the full reactor; test counts can change as the project evolves.

## Verify everything

Use the commands from your selected OS path to complete this checklist:

- [ ] `git --version` reports an installed Git version.
- [ ] `java -version` shows 21.
- [ ] `JAVA_HOME` points to JDK 21 (`echo "$JAVA_HOME"` on macOS; `$env:JAVA_HOME` in PowerShell).
- [ ] `docker info` succeeds and includes server details.
- [ ] `docker compose version` works.
- [ ] Mongo container is running (`docker compose ps`).
- [ ] Artemis container is running (`docker compose ps`).
- [ ] `rs.status().ok` returns `1` through container mongosh.
- [ ] `db.hello().isWritablePrimary` returns `true` through container mongosh.
- [ ] Artemis console opens at `http://localhost:8161/console` and login works.
- [ ] `./mvnw -version` (macOS) or `.\mvnw.cmd -version` (PowerShell) reports Java 21.
- [ ] The wrapper's `clean verify` succeeds for all modules.
- [ ] All three Spring applications start on 8080, 8081 and 8082.
- [ ] `http://localhost:8080` opens Petstore.

## Stop, reset and troubleshoot

Stop applications with Ctrl+C in each terminal or IntelliJ Stop. From the repository root, stop infrastructure while preserving volumes:

macOS:

```sh
docker compose down
```

Windows PowerShell:

```powershell
docker compose down
```

### Docker volume reset

A volume reset is **not part of normal installation or troubleshooting**. The destructive command `docker compose down -v` removes persistent Mongo **and Artemis** data, including accounts, orders, stock and broker messages. Use it only if you deliberately intend to discard all local demo data. After such a reset, repeat your OS path's infrastructure startup and one-time `rs0` initialization; application seeds/bootstrap recreate baseline data. A normal `docker compose down` preserves data.

### Installation troubleshooting

| Symptom | Check/action |
| --- | --- |
| `command not found: git` / Git not recognized | macOS: finish `xcode-select --install`. Windows: rerun Git installer with command-line PATH support. Reopen the shell and run `git --version`. |
| `command not found: java` / Java not recognized | Install Temurin **JDK 21**, reopen the shell, then apply your OS path's JAVA_HOME/PATH steps. |
| `JAVA_HOME` incorrect | Point it to the JDK 21 home, not its `bin` folder. macOS: use `/usr/libexec/java_home -v 21`. Windows: use the actual installed folder, without literal `...` or quotes in Environment Variables. |
| Maven wrapper uses wrong Java | Check wrapper `-version` after fixing JAVA_HOME/PATH. Restart terminals and IntelliJ; check Project SDK and Maven runner/importer separately. |
| Docker command not found | Complete Docker Desktop installation and CLI setup, then reopen Terminal/PowerShell. Installing only Java or WSL does not install Docker. |
| Docker daemon not running | Launch Docker Desktop and wait for the engine; repeat `docker info` before Compose. |
| Windows WSL missing/outdated | In administrator PowerShell use `wsl --install` if missing, restart if requested, then `wsl --update`; verify `wsl --version` and `wsl --status`. |
| Virtualization disabled | Enable virtualization in BIOS/UEFI using the PC vendor's instructions; restart and verify Task Manager's CPU virtualization status. |
| Docker stuck starting | Check free memory/disk and pending OS restarts. Restart Docker Desktop. On Windows update WSL first; if still stuck, quit Docker and run `wsl --shutdown` in PowerShell (stops all WSL sessions), then relaunch Docker. Use Docker's Troubleshoot diagnostics if unresolved; avoid factory reset as a first step. |
| `./mvnw`: permission denied (macOS) | From the repository root run `chmod +x mvnw`, then retry `./mvnw -version`. |
| Shell syntax errors on Windows | Use PowerShell and the Windows path's commands, including `.\mvnw.cmd`; do not paste Zsh/Bash commands into PowerShell or use Git Bash interchangeably. |
| `rs0` not initialized / no replica-set configuration | Run the initialization command from your OS path only on a fresh volume. Wait for election; verify status `1` and writable primary `true`. A healthy Mongo ping alone does not prove replica-set readiness. |
| Docker data appears missing after a reset | Volume deletion is destructive; restore a backup if needed. For an intentional fresh start, recreate infrastructure and initialize `rs0` before building/running. |

### Project troubleshooting

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
