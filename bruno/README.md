# REST scenarios

A [Bruno](https://www.usebruno.com) collection that drives the bike-leasing process end to end against
a **running** service — either variant, they expose the same API. Domain endpoints trigger the business
actions; the Camunda 8 REST API (`/v2`) completes the form-only user task and resolves the incident. CI
runs the collection against both variants on every pull request.

```bash
npx --yes @usebruno/cli@4.0.0 run . --env local -r
```

| Folder | Scenario |
|---|---|
| `01-happy-path` | submit → sign the contract → report the handover |
| `03-abort` | the customer withdraws after signing; the bike order is cancelled and the contract compensated |
| `04-not-solvent` | the DMN rejects the applicant |
| `05-bike-unavailable` | the bike is out of stock and the customer picks an alternative |
| `06-incident-demo` | a failing job runs out of retries and raises an incident |
| `07-list-and-inbox` | the list and task-inbox endpoints |
| `08-alternative-declined` | the bike is out of stock and no alternative is found; contract and policy are compensated, no order is cancelled |
| `09-invalid-request` | invalid input is refused with a 400: a request without income, an accepted alternative without a bike |
| `10-alternative-via-tasklist` | the bike is out of stock and the alternative is chosen the way the Tasklist form does it |

A running Zeebe broker cannot fast-forward timers over REST, so the timer-gated steps — the signature
deadline and the 14-day withdrawal period that ends in an active lease — are covered by the process
tests, not by this collection.

## 📮 Start a case by hand

`POST http://localhost:8081/api/bike-leasing`

```json
{ "customerName": "…", "email": "…", "age": 35, "monthlyNetIncome": 3500, "bikeId": "BIKE-900", "bikeModel": "Gravel Explorer 900" }
```

`age` and `monthlyNetIncome` feed the `checkCreditRating` DMN. `bikeId` is the *only* bike attribute the
engine ever carries: the descriptive `bikeModel` lives in a separate **bike portfolio** aggregate (its
own `bike_portfolio` table) — never as a process variable — and `GET /api/bike-leasing/{id}` resolves it
back from there.

The API also exposes a few read models: `GET /api/bikes` (the seeded catalogue with live dealer
availability), `GET /api/bike-leasing?status=&page=&size=` (the paged application list) and
`GET /api/tasks/clarify-alternative` (the back-office inbox of cases waiting on the
alternative-clarification task, never exposing a raw task id). The full contract is
[`../openapi/openapi.json`](../openapi/openapi.json).

## 🔁 Two ways to complete a user task

If the requested bike is out of stock (`bikeId: "BIKE-OOS"`), the `Clarify alternative with customer`
user task shows a deliberate contrast:

- **Reaches the domain either way:** a client calls `POST /api/bike-leasing/{id}/clarify-alternative`, or
  a human completes the Camunda Form in the Tasklist. Both hand the chosen `bikeId` to the process, and
  the re-order reads it and persists it on the application. The REST call additionally registers the
  bike's model in the portfolio.
- **Counter-example:** `clarify-return` in `cancel-bike-order.bpmn` is completed via the Camunda Form or
  the Camunda 8 REST API only. It never touches the domain, so its data lands only in process variables
  (see the `bpmn:documentation` on each task).

## 🚨 Incident demo

To teach **transaction boundaries, retries and incidents**, submit a request for the poison bike
`BIKE-FAIL`: the simulated dealer outage fails the *Order bike from dealer* job, its retries count down
(`retries="3"`, 10s apart), and once they hit 0 Zeebe raises an **incident** to analyze and retry in
**Operate**. `06-incident-demo/` has the requests ready to run.
