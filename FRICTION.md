# Friction log (from using the product myself)
| Time | Step in workflow | What felt awkward | Fix | Before -> after | Status |
|---|---|---|---|---|---|
| 21:29 | Case panel, other attempts, toast | Statuses shown as codes (`PENDING_REVIEW`, `RECAPTURE_REQUESTED`) | Plain labels ("Waiting for review", "Retake requested") | code → plain words | fixed |
| 21:29 | Token expires / 401 | Silently sent back to the login page, no reason given | "Your session ended. Log in again." on the login page | silent → explained | fixed |
| 21:29 | Decide | Decision and reason code not linked: "Approve" + "Document invalid" was accepted (tester created one such record on VC-20261014-0053) | Offer only reasons that fit the decision; server rejects mismatches (backlog N4, mapping needs compliance OK) | — | open |
| 21:29 | Errors | Some 400s say only "Validation failure" / "Invalid request content" without the field | Name the field and rule in the message | — | open |
| 21:29 | Take next | Same case comes back without saying it is your open case | Label "Continue your case"; pin my claim on top | — | open |
| 21:29 | Case panel | Case taken by a colleague can be opened and decided with no warning | Show "Taken by X" in the panel | — | open |
| 21:29 | Other attempts | Attempt 2 created before attempt 1 looks odd and is not explained | Mark the date clash as a data problem | — | open |
| 21:29 | Reload | Reload logs you out (token in memory) | Accepted trade-off for now; short-lived session storage later | — | accepted |
| 21:29 | Login | Hint "for example j.sebo" names a real user | Generic hint | — | open |
