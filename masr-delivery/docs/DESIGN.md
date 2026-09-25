# Design notes

## 1. Scope and structure

Masr Delivery is an in-memory console application. The supplied assignment contains Parts A-D and F-H; Part E is missing despite a reference to it in G.5. No missing requirements have been invented. In particular, there is no assumed database, background delivery simulation or persistence format for customer and order records.

`Main` handles prompts and presentation. `Platform` coordinates business operations and owns the indexed collections. Entities contain their own state and validation. `Pricing` calculates the immutable financial breakdown. `Reports` produces live analytics with Streams. `MenuItemFactory`, promotion policies, dispatch policies and `OrderEvents` provide the extension points.

All classes are in one package to keep this capstone small. Entity mutation methods are package-private; public callers use `Platform`. Moving the code into modules with narrower package boundaries would be a reasonable next step for a larger system. It would not improve the console requirements enough to justify doing it here.

## 2. Financial and order decisions

### Decimal money

All prices, balances, quantities and discounts use `BigDecimal`. Monetary inputs originate from decimal strings, never `new BigDecimal(double)`. Monetary results use two decimal places and `HALF_UP`, corresponding to the nearest piastre. Ratings alone use `double`, since they are not monetary values; non-finite ratings are rejected.

Each line is rounded to two decimal places, then lines are summed. Combo unit prices are rounded before multiplying by a whole-number count. Weighted quantities retain their entered decimal precision. Positive prices that round to zero are rejected. This is a consistent rounding policy where the assignment leaves line-level rounding unspecified.

The calculation order is:

1. Item-specific line prices and subtotal.
2. Base delivery plus distance surcharge, then loyalty reduction.
3. Service charge of 10% of subtotal.
4. One promotion, if supplied and eligible.
5. Total of the resulting components.

A fixed or percentage discount is capped at the subtotal, so it cannot pay for delivery or service. The assignment says promotions apply only to the subtotal but also requires a free-delivery promotion. The implementation treats free delivery as an explicit exception: it sets the already loyalty-adjusted delivery fee to zero and leaves the service fee unchanged. It does not add a second subtotal discount.

The worked example is verified by `Main --demo` and a regression test: 240 + 42 + 24 - 48 = **258.00 EGP**.

### Placement, payment and cancellation

Placement validates the registered customer and restaurant, address ownership, all items, quantities, combined stock requirements, route and promotion before making any stock or order mutation. A successful placement reserves stock and records an unpaid order. Payment is a separate console operation, as specified. Restaurant acceptance requires payment; cash-on-delivery was not specified and is not implemented.

A failed wallet payment leaves the order unpaid with its reservation intact, allowing another payment attempt or cancellation. It does not cancel an otherwise valid order. Cancellation before pickup restores reserved stock, refunds a paid order once, removes it from the dispatch queue and releases an assigned rider. Repeated payment and repeated cancellation are rejected.

Every order stores its line names, quantities, amounts and financial breakdown at placement. Later availability changes, menu removal or loyalty upgrades cannot change an existing bill. Address is an immutable value. Restaurant and customer identities remain available to historical orders after a restaurant is removed from the active catalog.

The legal forward lifecycle is enforced by `OrderStatus.canMoveTo`. `Platform` also enforces who can perform each kind of transition. `Order.move` is not a public shortcut around stock, wallet or rider coordination. Terminal orders cannot change status. Rejection by a restaurant is limited to pending orders; customer cancellation is allowed at every legal pre-pickup stage.

### Stock and identity

Standard items use whole-number stock; weighted items use kilograms. A combo reserves the underlying components recursively, not a separate bundle counter. A combo and a standalone component in the same order have their requirements combined before validation. A combo is unavailable if any component is unavailable.

Adjusting stock sets the current unreserved quantity. It does not erase existing reservations. Consequently, cancelling an older order adds its reserved quantity back to the current quantity. There is no automatic midnight reset: the assignment requests an adjustment operation, not a reset schedule.

Restaurant, customer, rider and order identities are their immutable IDs within their entity type. `Entity.equals` and `hashCode` use both the runtime entity class and ID. Menu identity is `(restaurantId, itemId)` across item subtypes, so `R1/M1` and `R2/M1` are different items. Addresses are values identified by district and detail text. Promotion codes are stored uppercase. Used restaurant and menu IDs cannot be recycled during a run; this avoids confusing historical records with newly added entities.

A component cannot be removed while a listed combo references it. Removing a restaurant with active orders is rejected. Menu removals do not erase the reservation information retained by existing orders.

## 3. Collections and complexity

| Requirement | Choice | Reason / cost |
| --- | --- | --- |
| Lookup by entity ID | `LinkedHashMap<String, ...>` | Expected O(1) lookup; deterministic administration lists |
| Restaurant menu insertion order | `LinkedHashMap<String, MenuItem>` | Expected O(1) lookup plus stable iteration; seed file order is preserved on every run |
| Distinct cuisines | Case-insensitive `TreeSet` derived from active restaurants | No duplicate cuisine spellings differing only by case; O(k log k) per listing |
| Rating ranking | Stream `sorted` with rating descending, name ascending, ID fallback | O(n log n); no stale sorted index |
| READY dispatch | `PriorityQueue<Order>` | O(log n) insertion; Gold first, then READY timestamp and ID |
| Last five searches | `ArrayDeque<String>` | O(1) insert/trim; storage never exceeds five entries |
| Stock | `HashMap<ItemKey, BigDecimal>` | Expected O(1) component lookup |
| Lifecycle timestamps | `EnumMap<OrderStatus, Instant>` | Natural state ordering and bounded size |
| Used IDs and membership | `HashSet` / `LinkedHashSet` | Deduplication; address selection also preserves order |

Ready-queue traversal creates a sorted snapshot because a heap's iterator does not guarantee priority order. Dispatch costs O(q log q + q*r) for q waiting orders and r riders; removing a particular order from Java's priority queue costs O(q). This is acceptable for a console capstone. No claim is made that every dispatch operation is O(log q).

Public collection accessors return immutable copies or unmodifiable snapshots. Reports do the same. Returning a mutable map or a live `values()` view would let callers bypass validation and is deliberately avoided. Entity references within snapshots can reflect subsequent authorized state changes; collection immutability is not presented as a deep historical snapshot.

The search method accepts `Predicate<Restaurant>`. The console composes district, cuisine, rating and price predicates with `and`. A price ceiling means that a restaurant offers at least one available menu item at or below that unit price; weighted unit prices mean per kilogram. A new criterion requires a new predicate, not a modified search engine.

## 4. Patterns and rejected alternatives

| Problem | Implemented approach | Rejected alternative and reason |
| --- | --- | --- |
| Item-dependent line pricing | Polymorphic `MenuItem.price` / `unitPrice` | A type switch in `Pricing` would require editing order pricing for every new item |
| Promotion variation | Strategy: `Promotion.Policy`, with composable `Condition` rules | A growing conditional chain would mix marketing rules with arithmetic |
| Multi-step order construction | `Order.Builder` produces a validated request; `Platform.place` commits it | A large nullable constructor is hard to read; a builder that commits stock while being assembled would not be atomic |
| Type field in menu data | Registry-based `MenuItemFactory` | Type checks scattered across file import and UI would duplicate creation logic |
| Status reactions | Observer: `OrderEvents` with independent subscribers | Direct calls from `Order` to console, audit and reports would tightly couple the domain to presentation and side effects |
| Load configuration once | Initialization-on-demand holder singleton | An unsynchronized lazy static field can race; eager reloads repeat I/O; passing the global configuration through every entity constructor adds irrelevant coupling |
| Rider variation | Strategy: `DispatchPolicy`; built-in parameterized `VehiclePolicy` instances | A vehicle switch inside `Rider` would need modification for a scooter or other vehicle |

The UI still asks which promotion or vehicle to create. This is a presentation choice, not a type switch in total calculation or rider behavior. A new menu type is registered once with the factory. The regression suite registers a custom item and prices it without changing the calculator.

The observer setup contains four independent reactions: customer console notification, rider dashboard state, audit append and recalculated status counts. Subscribers receive immutable event values. A failing subscriber is recorded in `events().failures()` and does not roll back a committed business transaction or prevent subsequent subscribers from running. This is a local, synchronous notification system, not durable messaging. Slow subscribers delay the operation; retry queues would be a separate production concern. Subscribers are intended to read state and publish side effects, not invoke new business mutations recursively.

The singleton uses JVM class initialization, which is thread-safe. Configuration is private and has no setters or mutable accessor. This does create a global dependency and makes per-test configuration replacement awkward. Passing fee settings into `Pricing` is still supported internally. For a service with multiple tenant configurations, dependency injection would be preferable. The supplied `pool.size` is used by concurrency tests; the console does not pretend to implement an unspecified Part E worker pool.

## 5. Dispatch and concurrency

All multi-entity mutations are synchronized on the same `Platform` instance. Stock validation and reservation happen in one critical section; so do wallet validation/deduction and cancellation/refund. Assignment verifies READY state, queue priority, rider availability, current district and policy eligibility before changing the rider and order together.

A rider keeps one active order ID. Only an on-duty rider with no active order can be assigned. No code path releases that slot on pickup. It is released only on legal cancellation or successful delivery. Marking delivery before pickup and going off duty with an active order are rejected. Public concurrent mutations must pass through `Platform`; reports take the same monitor while collecting their results.

A queue gives Gold customers priority over other customers; within a priority class the earliest READY timestamp wins. The tier is captured at placement, matching the pricing snapshot and preventing mutable comparator keys from corrupting the heap. If an older order has no eligible rider, it remains in the queue while a younger eligible order may be dispatched. This avoids blocking the whole platform behind an undeliverable order.

Riders must currently be in the restaurant's district. Among eligible available riders, the smallest policy-estimated travel time wins, with ID as a deterministic tie-break. Built-in policies are demonstration values:

| Vehicle | Maximum route | Assumed speed | Capacity units |
| --- | ---: | ---: | ---: |
| Motorcycle | 25 km | 30 km/h | 30 |
| Bicycle | 5 km | 12 km/h | 8 |
| Car | 60 km | 25 km/h | 100 |

Because item package weights are absent from the assignment, capacity uses a declared proxy: one standard component counts as one capacity unit; one kilogram of weighted food counts as one unit; combos count their expanded components. These are illustrative eligibility rules, not real logistics measurements. The estimated duration is used for rider selection; recorded delivery statistics use actual lifecycle timestamps.

This coarse locking model sacrifices parallel throughput for a simple correctness argument. Per-rider locks alone would not protect stock and wallets; separate fine-grained locks would need a strict acquisition order. Those approaches are unnecessary for this scope. The single in-process lock is not a distributed lock and provides no crash recovery or multi-JVM guarantee.

## 6. Errors and analytics

### Exception policy

All anticipated platform failures extend unchecked `PlatformException`. These are user-correctable validation or state errors handled at the interactive operation boundary. The UI displays their messages and returns to the menu; it does not catch all JVM failures as if they were input mistakes. Checked exceptions are retained for underlying file I/O, then translated into startup or observer failures at the appropriate boundary.

| Exception | Example |
| --- | --- |
| `RestaurantClosedException` | Placing at a closed restaurant |
| `ItemUnavailableException` | Unavailable item or combo component |
| `InsufficientFundsException` | Wallet cannot cover the total |
| `IllegalTransitionException` | Jumping directly to READY or cancelling after pickup |
| `InvalidPromotionException` | Expired, unknown or ineligible promotion |
| `RiderBusyException` | Assigning a busy rider or going off duty mid-delivery |
| `StockShortageException` | Combined component reservation exceeds stock |
| `DuplicateIdException` | Reusing an entity ID or normalized promo code |
| `NotFoundException` | Unknown record |
| `ValidationException` | Invalid mobile, price, quantity, address or other input |

### Report definitions

The assignment does not specify all accounting boundaries. The application consistently applies the following definitions; calculations are made from live data using Streams, grouping collectors, reductions and `Optional` results.

| Report | Definition |
| --- | --- |
| Revenue over dates | Sum of full billed totals of DELIVERED orders, by delivery date; both end dates included |
| Top five restaurants | Same gross revenue in the requested delivery month; revenue descending, name then ID for ties |
| Average order value by district | Completed totals divided by completed count, grouped by delivery district (case-normalized), rounded to piastres |
| Rating above 4.5 and 20 completions | Active restaurants with strictly greater than 4.5 rating and at least 20 DELIVERED orders |
| Counts by status | Every status, including zero counts |
| Rider statistics | DELIVERED count and mean pickup-to-delivery duration; no invented duration for riders with no deliveries |
| Most frequent item | Number of non-cancelled orders containing each `(restaurantId,itemId)`; merged repeated lines count once per order; ties by key |
| Customer history and spending | All statuses, newest first; spending is paid totals minus refunds, so an active paid order counts as spending before it becomes revenue |
| Peak ordering hour | All orders by placement hour, including later cancellations; tied hours choose the earliest hour |
| Inactive customers | No placement at or after the exact 30-day instant cutoff, including customers who have never ordered |

Counting order appearances for popularity avoids adding kilograms and integer portions as if they were the same unit. A combo is counted as the ordered menu item, not as extra orders for its components. No orders, or only cancelled orders, produces `Optional.empty()` for the popular-item report. Peak hour likewise represents absence honestly.

Dates use `java.time` only. The demonstration clock is `Africa/Cairo`; tests inject a controllable clock. Expiry is inclusive through the named local date. A first-time customer has no prior non-cancelled order, including an unpaid pending order. Cancellation allows that customer to qualify again. A minimum-subtotal failure rejects the promotion and order rather than silently removing the code.

A menu is printed in global insertion order with a category heading whenever the category changes. A category can therefore appear in more than one block; forcing every category into a single block would reorder an interleaved menu and conflict with the insertion-order requirement.

## 7. Verification and requirement map

The dependency-free `PlatformTest` runner throws on any failure and exits unsuccessfully. It checks the exact worked example, monetary edge cases, combo reservations, stock rollback, all promotion conditions, legal and illegal lifecycle operations, wallet refunds, tier thresholds, dispatch ordering, collection protection, identity, factory extension and all ten reports.

Concurrent tests use a start latch and futures, not timing sleeps. They verify one winner when two threads reserve the last stock, pay an order or cancel it. The dispatch test runs 100 independent simultaneous duty/dispatch attempts and checks that exactly one order is assigned to the rider each time. This gives regression evidence in addition to the synchronization argument; it is not a claim of formal verification.

The console smoke test separately drives an invalid menu selection, the complete paid lifecycle, a report and an empty-report path. Test output is included with the submission.

| Assignment | Main implementation |
| --- | --- |
| A.1 | `Restaurant`, `MenuItem`, `StandardItem`, `ComboItem`, `WeightedItem` |
| A.2 | `Customer`, `Address`, `LoyaltyTier` |
| A.3 | `Rider`, `Platform.assign`, `DispatchPolicy` |
| A.4 | `Order`, `OrderStatus`, `Platform` |
| B | `Money`, `Pricing`, `Promotion`, exact-example test |
| C.1-C.8 | Platform maps, restaurant menu, cuisine set, search sort, ready queue, search deque, entity identity, immutable accessors |
| D.1-D.10 | `Reports` |
| D search and time | Predicate-based `Platform.search`, `java.time`, injectable `Clock` |
| E | Not present in the supplied document; no compliance claim |
| F | `PlatformException` hierarchy and console error boundary |
| G.1-G.6 | Policies, builder, factory, observers, singleton, dispatch strategy |
| H | `Main`, all four console areas, seed data and validation |

Known limits are explicit: in-memory business data, no authentication, no database durability, a demonstration route table, synchronous local notifications and no background simulation. These are scope boundaries rather than silently claimed features.
