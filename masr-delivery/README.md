# Masr Delivery

Java 17 console application for the Food Delivery capstone assignment.

The project includes restaurant menus, wallet payments, stock reservations, rider dispatch, promotions and all ten requested reports. It uses the Java standard library only.

## Run

A **JDK 17 or newer** must be installed and `java` must be available on PATH. No Maven, Gradle, database or internet connection is needed to build or run it.

### Windows

Extract the ZIP first. Open the extracted `masr-delivery` folder, then double-click `run.bat`.

From Command Prompt in that folder:

```bat
run.bat
run.bat --demo
test.bat
```

### Linux / macOS

```sh
chmod +x build.sh run.sh test.sh
./run.sh
./run.sh --demo
./test.sh
```

The scripts call the JDK compiler and archive modules through `java`, so they do not depend on separate `javac` or `jar` launcher paths. `build.sh` / `build.bat` rebuild the runnable archive.

A prebuilt archive is also included:

```sh
java -jar dist/masr-delivery.jar
java -jar dist/masr-delivery.jar --demo
```

The demo must print **258.00 EGP**, matching the assignment's worked example.

## Included files

| Location | Purpose |
| --- | --- |
| `src/main/java/masr/delivery/` | Application source |
| `src/main/resources/application.properties` | Fees, resource location, audit path and test pool size |
| `src/main/resources/menu.psv` | Deterministic demo menu loaded through the item factory |
| `src/test/java/masr/delivery/PlatformTest.java` | Dependency-free regression tests |
| `dist/masr-delivery.jar` | Runnable Java 17 archive |
| `docs/DESIGN.md` | Design choices, rejected alternatives, assumptions and requirement mapping |
| `docs/GUIDE_AR.md` | Arabic walkthrough and discussion notes |
| `docs/TEST_RESULTS.txt` | Captured regression test output |
| `docs/CONSOLE_SMOKE_TEST.txt` | Captured console lifecycle and invalid-input checks |

## Demo accounts

This is a local role-based demonstration, without passwords or authentication.

| Type | ID | Details |
| --- | --- | --- |
| Customer | C1 | Omar; 5,000 EGP; Faisal and Maadi addresses |
| Customer | C2 | Salma; 2,500 EGP; Dokki address |
| Restaurant | R1 | Nile Kitchen, Maadi |
| Restaurant | R2 | Pasta Corner, Dokki |
| Restaurant | R3 | Green Bowl, Nasr City |
| Rider | D1 | Ahmed, motorcycle, Maadi |
| Rider | D2 | Mona, bicycle, Dokki |
| Rider | D3 | Karim, car, Nasr City |

All riders start off duty. After an order is paid, a restaurant can accept it, mark it preparing and mark it ready. Going on duty triggers dispatch automatically. After delivery, a rider's current district becomes the customer's district; use **Update district** before collecting a subsequent order elsewhere.

Demo promotions: `NILE20` (20%, capped at 50 EGP), `WELCOME30` (30 EGP, first order, minimum 100 EGP) and `FREEDEL` (minimum 150 EGP). Codes are case-insensitive. Demo expiry dates are one year from the application's startup date.

## Data and scope

- Records are held in memory and demo data is reloaded on each launch. Changes are not restored after exit. The append-only audit file is written to `data/audit.log`.
- District distances are a documented demonstration table, not geographical estimates. Maadi to Faisal is exactly 12 km, as required by the assignment. Unsupported routes are rejected explicitly.
- Revenue means gross totals of delivered orders. See `docs/DESIGN.md` for report definitions and other decisions left open by the assignment.
- **Part E is absent from the supplied PDF.** This implementation covers the supplied sections A-D and F-H. It does not claim compliance with an unseen persistence or background-processing specification.
- The automated suite runs on Java 17. Windows scripts are included but were not executed in a Windows environment.

For an IDE, create/open a Java 17 project, mark `src/main/java` as Sources, `src/main/resources` as Resources and `src/test/java` as Test Sources. Run `masr.delivery.Main`. The build scripts are the reference build path.
