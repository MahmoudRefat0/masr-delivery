# 🚚 Masr Delivery

A food-delivery platform built as a **Java 17 console application** using only the standard library (no frameworks). It covers the full flow of an order, from the restaurant menu to the rider at the customer's door.

## Features
- **Restaurants & menus**: menu items loaded through a factory, stock reservations
- **Order lifecycle**: paid → accepted → preparing → ready → delivered
- **Wallet payments** for customers
- **Rider dispatch**: riders go on duty and get assigned automatically by district
- **Promotions**: percentage, fixed-amount and free-delivery codes with rules and expiry
- **Analytics reports** built with the **Stream API**
- Append-only **audit log**
- Dependency-free **regression tests**

## Tech
| | |
| --- | --- |
| Language | Java 17 |
| Concepts | OOP, Design Patterns (Factory), Collections, Streams, Enums, Exceptions |
| Build | Plain JDK scripts (`build` / `run` / `test`), runnable JAR in `dist/` |
| Config | `application.properties` |

## Quick start
```bash
cd masr-delivery
java -jar dist/masr-delivery.jar          # interactive console
java -jar dist/masr-delivery.jar --demo   # scripted demo
```
Or build and test from source:
```bash
./build.sh && ./run.sh && ./test.sh       # Windows: build.bat, run.bat, test.bat
```

## Documentation
- [Full project README](masr-delivery/README.md): run options, demo accounts and promo codes
- [Design notes](masr-delivery/docs/DESIGN.md): design choices and trade-offs
- [Arabic walkthrough](masr-delivery/docs/GUIDE_AR.md)
- [Test results](masr-delivery/docs/TEST_RESULTS.txt)
